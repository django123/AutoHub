package com.ocarius.autohub.rental.infrastructure.out.persistence;

import com.ocarius.autohub.fleet.domain.model.VehicleId;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.model.CustomerId;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.rental.domain.model.RentalStatus;
import com.ocarius.autohub.rental.domain.model.VehicleHandover;
import com.ocarius.autohub.shared.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.IllegalTransactionStateException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'integration de {@link RentalRepositoryAdapter} contre un PostgreSQL
 * reel : aller-retour du mapping, contrainte d'exclusion, cas limites de
 * l'ADR 0002 et verrouillage optimiste.
 *
 * <p>On injecte le <b>port</b> ({@link RentalRepository}), pas l'adaptateur :
 * c'est ce que verra la couche application, et c'est son contrat qu'on teste.
 */
class RentalRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    /** "Maintenant" fige bien avant toutes les periodes utilisees, pour respecter le preavis. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-01T10:00:00Z"), ZoneOffset.UTC);

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2027, 3, 8, 8, 0);

    @Autowired
    private RentalRepository repository;

    /** Cree et reserve une location ; ne la sauvegarde pas. */
    private static Rental reserved(VehicleId vehicleId, RentalPeriod period) {
        Rental rental = Rental.draft(RentalId.newId(), vehicleId, CustomerId.newId(), period, Money.euros("120.00"));
        rental.reserve(CLOCK);
        return rental;
    }

    /** Sauvegarde dans sa propre transaction, comme le ferait un cas d'usage. */
    private void saveInNewTransaction(Rental rental) {
        tx.executeWithoutResult(status -> repository.save(rental));
    }

    @Nested
    @DisplayName("Aller-retour du mapping")
    class AllerRetour {

        @Test
        @DisplayName("une location rendue se relit a l'identique, etats des lieux compris")
        void aller_retour_complet() {
            Rental rental = reserved(insertVehicle(), RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(2)));
            rental.confirm(CLOCK);
            rental.pickUp(new VehicleHandover(LUNDI_8H.plusHours(1), 12_000, 90), CLOCK);
            rental.returnVehicle(new VehicleHandover(LUNDI_8H.plusDays(2).plusHours(3), 12_480, 35), CLOCK);
            saveInNewTransaction(rental);

            Rental reloaded = repository.findById(rental.id()).orElseThrow();

            assertThat(reloaded.status()).isEqualTo(RentalStatus.RETURNED);
            assertThat(reloaded.period()).isEqualTo(rental.period());
            assertThat(reloaded.quotedPrice()).isEqualTo(rental.quotedPrice());
            assertThat(reloaded.pickup()).isEqualTo(rental.pickup());
            assertThat(reloaded.dropoff()).isEqualTo(rental.dropoff());
            assertThat(reloaded.isLateReturn()).isTrue();
            assertThat(reloaded.peekDomainEvents()).as("la relecture n'emet rien").isEmpty();
        }

        @Test
        @DisplayName("l'echeance de reservation (Instant) se relit a l'identique")
        void echeance_relue() {
            Rental rental = reserved(insertVehicle(), RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(1)));
            saveInNewTransaction(rental);

            assertThat(repository.findById(rental.id()).orElseThrow().reservedUntil())
                    .isEqualTo(rental.reservedUntil());
        }

        @Test
        @DisplayName("* les heures stockees sont les heures nominales, sans decalage de fuseau")
        void heures_nominales_en_base() {
            // ADR 0002 : la base doit contenir 08:00 si le domaine dit 08:00,
            // quel que soit le fuseau de la JVM ou de la session PostgreSQL.
            Rental rental = reserved(insertVehicle(), RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(1)));
            saveInNewTransaction(rental);

            String stored = jdbc.queryForObject(
                    "SELECT starts_at::text FROM rental WHERE id = ?", String.class, rental.id().value());
            assertThat(stored).isEqualTo("2027-03-08 08:00:00");
        }
    }

    @Nested
    @DisplayName("Contrainte d'exclusion rental_no_overlap")
    class ContrainteExclusion {

        @Test
        @DisplayName("une location qui en chevauche une autre est refusee en VehicleNotAvailableException")
        void chevauchement_refuse() {
            VehicleId vehicle = insertVehicle();
            saveInNewTransaction(reserved(vehicle, RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(2))));

            Rental overlapping = reserved(vehicle, RentalPeriod.of(LUNDI_8H.plusDays(1), LUNDI_8H.plusDays(3)));

            assertThatThrownBy(() -> saveInNewTransaction(overlapping))
                    .isInstanceOf(VehicleNotAvailableException.class)
                    .hasMessageContaining(vehicle.value().toString());
            assertThat(repository.findById(overlapping.id())).isEmpty();
        }

        @Test
        @DisplayName("deux locations adjacentes (fin = debut) sont acceptees")
        void adjacentes_acceptees() {
            VehicleId vehicle = insertVehicle();
            saveInNewTransaction(reserved(vehicle, RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(2))));
            Rental next = reserved(vehicle, RentalPeriod.of(LUNDI_8H.plusDays(2), LUNDI_8H.plusDays(4)));

            saveInNewTransaction(next);

            assertThat(repository.findById(next.id())).isPresent();
        }

        @Test
        @DisplayName("deux vehicules differents peuvent etre loues sur la meme periode")
        void vehicules_differents() {
            RentalPeriod period = RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(2));
            saveInNewTransaction(reserved(insertVehicle(), period));
            Rental other = reserved(insertVehicle(), period);

            saveInNewTransaction(other);

            assertThat(repository.findById(other.id())).isPresent();
        }

        @Test
        @DisplayName("* une location annulee libere la periode pour une nouvelle reservation")
        void annulee_ne_bloque_plus() {
            VehicleId vehicle = insertVehicle();
            RentalPeriod period = RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(2));
            Rental first = reserved(vehicle, period);
            saveInNewTransaction(first);

            tx.executeWithoutResult(status -> {
                Rental loaded = repository.findById(first.id()).orElseThrow();
                loaded.cancel("test", CLOCK);
                repository.save(loaded);
            });
            Rental second = reserved(vehicle, period);
            saveInNewTransaction(second);

            assertThat(repository.findById(second.id())).isPresent();
        }

        @Test
        @DisplayName("* une reservation sur l'heure du passage a l'heure d'ete est bien protegee (ADR 0002)")
        void passage_a_l_heure_d_ete() {
            // Le 28 mars 2027, 02:00 -> 03:00 n'existe pas a Paris. En tstzrange
            // la plage devenait vide et la contrainte ne jouait plus ; en tsrange,
            // elle doit jouer comme n'importe quel autre jour.
            VehicleId vehicle = insertVehicle();
            LocalDateTime deuxHeures = LocalDateTime.of(2027, 3, 28, 2, 0);
            RentalPeriod nuitDuChangement = RentalPeriod.of(deuxHeures, deuxHeures.plusHours(1));
            saveInNewTransaction(reserved(vehicle, nuitDuChangement));

            assertThatThrownBy(() -> saveInNewTransaction(reserved(vehicle, nuitDuChangement)))
                    .isInstanceOf(VehicleNotAvailableException.class);
        }

        @Test
        @DisplayName("* les statuts filtres par la contrainte sont exactement RentalStatus.blockingStatuses()")
        void statuts_de_la_contrainte_alignes_sur_le_domaine() {
            // Le code et la base dupliquent la liste des statuts bloquants. Ce
            // test lit la definition reelle de la contrainte dans le catalogue
            // PostgreSQL et la compare au domaine : si l'un change sans l'autre,
            // il echoue.
            String definition = jdbc.queryForObject(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'rental_no_overlap'",
                    String.class);

            Set<RentalStatus> inConstraint = Arrays.stream(RentalStatus.values())
                    .filter(s -> definition.contains("'" + s.name() + "'"))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(RentalStatus.class)));

            assertThat(inConstraint).isEqualTo(RentalStatus.blockingStatuses());
        }
    }

    @Nested
    @DisplayName("Transactions et verrouillage optimiste")
    class Transactions {

        @Test
        @DisplayName("save hors transaction est refuse (Propagation.MANDATORY)")
        void save_exige_une_transaction() {
            Rental rental = reserved(insertVehicle(), RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(1)));

            assertThatThrownBy(() -> repository.save(rental))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        @DisplayName("* deux modifications concurrentes de la meme location : la seconde echoue")
        void mise_a_jour_perdue_evitee() throws Exception {
            Rental rental = reserved(insertVehicle(), RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(1)));
            saveInNewTransaction(rental);

            // La transaction A charge la location, puis attend que B ait commite
            // sa propre modification avant de sauvegarder la sienne.
            CountDownLatch aHasLoaded = new CountDownLatch(1);
            CountDownLatch bHasCommitted = new CountDownLatch(1);

            CompletableFuture<Void> a = CompletableFuture.runAsync(() ->
                    tx.executeWithoutResult(status -> {
                        Rental loaded = repository.findById(rental.id()).orElseThrow();
                        aHasLoaded.countDown();
                        await(bHasCommitted);
                        loaded.confirm(CLOCK);
                        repository.save(loaded);
                    }));

            await(aHasLoaded);
            tx.executeWithoutResult(status -> {
                Rental loaded = repository.findById(rental.id()).orElseThrow();
                loaded.cancel("B est passe avant", CLOCK);
                repository.save(loaded);
            });
            bHasCommitted.countDown();

            assertThatThrownBy(a::join).hasCauseInstanceOf(OptimisticLockingFailureException.class);
            assertThat(repository.findById(rental.id()).orElseThrow().status()).isEqualTo(RentalStatus.CANCELLED);
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
