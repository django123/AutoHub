package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.AvailableVehicleView;
import com.ocarius.autohub.rental.application.port.in.HandoverCommand;
import com.ocarius.autohub.rental.application.port.in.RentalView;
import com.ocarius.autohub.rental.application.port.in.ReserveRentalCommand;
import com.ocarius.autohub.rental.domain.event.LateReturnPenaltyApplied;
import com.ocarius.autohub.rental.domain.event.RentalConfirmed;
import com.ocarius.autohub.rental.domain.event.RentalExpired;
import com.ocarius.autohub.rental.domain.event.RentalReserved;
import com.ocarius.autohub.rental.domain.event.VehicleReturned;
import com.ocarius.autohub.rental.domain.exception.IllegalRentalTransitionException;
import com.ocarius.autohub.rental.domain.exception.RentalNotFoundException;
import com.ocarius.autohub.rental.domain.exception.UnknownVehicleException;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.exception.VehicleNotRentableException;
import com.ocarius.autohub.rental.domain.pricing.PricingService;
import com.ocarius.autohub.shared.application.fake.DirectUnitOfWork;
import com.ocarius.autohub.shared.application.fake.MutableClock;
import com.ocarius.autohub.shared.application.fake.RecordingEventPublisher;
import com.ocarius.autohub.shared.application.view.MoneyView;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.VehicleId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests des cas d'usage du contexte rental, <b>sans Spring et sans Mockito</b> :
 * de simples fakes en memoire suffisent, parce que chaque port est petit.
 *
 * <p>Ces tests verifient l'orchestration (qui est charge, qui est enregistre,
 * quels evenements partent). Les regles metier elles-memes sont testees dans
 * le domaine ; la contrainte d'exclusion et la transaction reelle, contre
 * PostgreSQL.
 */
class RentalUseCasesTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDateTime START = LocalDateTime.of(2027, 3, 8, 8, 0);
    private static final LocalDateTime END = LocalDateTime.of(2027, 3, 10, 8, 0);

    private final MutableClock clock = new MutableClock(Instant.parse("2027-03-01T09:00:00Z"), PARIS);
    private final InMemoryRentalRepository rentals = new InMemoryRentalRepository();
    private final FakeVehicleCatalog catalog = new FakeVehicleCatalog();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final DirectUnitOfWork unitOfWork = new DirectUnitOfWork();
    private final PricingService pricing = PricingService.standard();

    private final ReserveRentalService reserve =
            new ReserveRentalService(rentals, catalog, pricing, events, unitOfWork, clock);
    private final ConfirmRentalService confirm = new ConfirmRentalService(rentals, events, unitOfWork, clock);
    private final CancelRentalService cancel = new CancelRentalService(rentals, events, unitOfWork, clock);
    private final PickUpVehicleService pickUp = new PickUpVehicleService(rentals, events, unitOfWork, clock);
    private final ReturnVehicleService returnVehicle = new ReturnVehicleService(rentals, events, unitOfWork, clock);
    private final CloseRentalService close = new CloseRentalService(rentals, events, unitOfWork);
    private final GetRentalService get = new GetRentalService(rentals, unitOfWork);
    private final ExpireOverdueReservationsService expire =
            new ExpireOverdueReservationsService(rentals, events, unitOfWork, clock);
    private final FindAvailableVehiclesService findAvailable =
            new FindAvailableVehiclesService(catalog, pricing, unitOfWork, clock);

    private UUID reserve(VehicleId vehicle) {
        return reserve.reserve(new ReserveRentalCommand(vehicle.value(), UUID.randomUUID(), START, END));
    }

    private static MoneyView euros(String amount) {
        return new MoneyView(new BigDecimal(amount), "EUR");
    }

    @Nested
    @DisplayName("Reserver")
    class Reserver {

        @Test
        @DisplayName("reserver un vehicule louable fige le prix calcule et publie RentalReserved")
        void reservation_nominale() {
            VehicleId vehicle = catalog.addRentable("45.00");

            UUID id = reserve(vehicle);

            RentalView view = get.get(id);
            assertThat(view.status()).isEqualTo("RESERVED");
            assertThat(view.quotedPrice()).isEqualTo(euros("90.00"));
            assertThat(view.dailyRate()).isEqualTo(euros("45.00"));
            assertThat(events.published(RentalReserved.class)).hasSize(1);
        }

        @Test
        @DisplayName("un vehicule inconnu du catalogue est refuse")
        void vehicule_inconnu() {
            assertThatThrownBy(() -> reserve(VehicleId.newId())).isInstanceOf(UnknownVehicleException.class);
        }

        @Test
        @DisplayName("un vehicule non louable est refuse")
        void vehicule_non_louable() {
            VehicleId vehicle = catalog.addNotRentable("45.00");
            assertThatThrownBy(() -> reserve(vehicle)).isInstanceOf(VehicleNotRentableException.class);
            assertThat(rentals.size()).isZero();
        }

        @Test
        @DisplayName("une periode invalide est refusee avant d'ouvrir la moindre transaction")
        void periode_invalide() {
            VehicleId vehicle = catalog.addRentable("45.00");
            assertThatThrownBy(() -> reserve.reserve(
                    new ReserveRentalCommand(vehicle.value(), UUID.randomUUID(), START, START.plusMinutes(30))))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThat(unitOfWork.transactions()).isZero();
        }

        @Test
        @DisplayName("une seconde reservation sur le meme creneau est refusee")
        void creneau_pris() {
            VehicleId vehicle = catalog.addRentable("45.00");
            reserve(vehicle);

            assertThatThrownBy(() -> reserve(vehicle)).isInstanceOf(VehicleNotAvailableException.class);
        }

        @Test
        @DisplayName("* une reservation echue mais pas encore expiree ne bloque plus le creneau")
        void reservation_echue_liberee_a_la_reservation() {
            VehicleId vehicle = catalog.addRentable("45.00");
            UUID first = reserve(vehicle);

            clock.advance(Duration.ofMinutes(31));
            UUID second = reserve(vehicle);

            assertThat(get.get(first).status()).isEqualTo("EXPIRED");
            assertThat(get.get(second).status()).isEqualTo("RESERVED");
            assertThat(events.published(RentalExpired.class)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Confirmer, annuler, expirer")
    class ConfirmerAnnulerExpirer {

        @Test
        @DisplayName("confirmer dans les 30 minutes publie RentalConfirmed")
        void confirmer() {
            UUID id = reserve(catalog.addRentable("45.00"));
            clock.advance(Duration.ofMinutes(29));

            confirm.confirm(id);

            assertThat(get.get(id).status()).isEqualTo("CONFIRMED");
            assertThat(events.published(RentalConfirmed.class)).hasSize(1);
        }

        @Test
        @DisplayName("confirmer une location inconnue leve RentalNotFoundException")
        void confirmer_inconnue() {
            assertThatThrownBy(() -> confirm.confirm(UUID.randomUUID())).isInstanceOf(RentalNotFoundException.class);
        }

        @Test
        @DisplayName("annuler une reservation libere le creneau")
        void annuler() {
            VehicleId vehicle = catalog.addRentable("45.00");
            UUID id = reserve(vehicle);

            cancel.cancel(id, "changement de programme");

            assertThat(get.get(id).status()).isEqualTo("CANCELLED");
            assertThat(reserve(vehicle)).isNotNull();
        }

        @Test
        @DisplayName("le traitement d'expiration n'expire que les reservations echues")
        void traitement_d_expiration() {
            UUID echue = reserve(catalog.addRentable("45.00"));
            clock.advance(Duration.ofMinutes(20));
            UUID recente = reserve(catalog.addRentable("45.00"));
            clock.advance(Duration.ofMinutes(15));

            int expired = expire.expireOverdueReservations();

            assertThat(expired).isEqualTo(1);
            assertThat(get.get(echue).status()).isEqualTo("EXPIRED");
            assertThat(get.get(recente).status()).isEqualTo("RESERVED");
        }

        @Test
        @DisplayName("* le traitement d'expiration ouvre une transaction par reservation")
        void une_transaction_par_reservation() {
            reserve(catalog.addRentable("45.00"));
            reserve(catalog.addRentable("45.00"));
            clock.advance(Duration.ofHours(1));
            int before = unitOfWork.transactions();

            expire.expireOverdueReservations();

            // Une pour lister les candidates, puis une par reservation.
            assertThat(unitOfWork.transactions() - before).isEqualTo(3);
        }

        @Test
        @DisplayName("* relancer le traitement d'expiration ne fait rien de plus (idempotence)")
        void expiration_idempotente() {
            reserve(catalog.addRentable("45.00"));
            clock.advance(Duration.ofHours(1));
            expire.expireOverdueReservations();

            assertThat(expire.expireOverdueReservations()).isZero();
            assertThat(events.published(RentalExpired.class)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Cycle complet : depart, retour, cloture")
    class CycleComplet {

        @Test
        @DisplayName("reserver, confirmer, partir, rendre en retard, clore : penalite et evenements")
        void cycle_complet_avec_retard() {
            UUID id = reserve(catalog.addRentable("45.00"));
            confirm.confirm(id);
            pickUp.pickUp(id, new HandoverCommand(START, 12_000, 100));

            returnVehicle.returnVehicle(id, new HandoverCommand(END.plusHours(3), 12_480, 40));
            close.close(id);

            RentalView view = get.get(id);
            assertThat(view.status()).isEqualTo("CLOSED");
            assertThat(view.lateReturnPenalty()).isEqualTo(euros("67.50"));   // 45 x 1,5 x 1 jour entame
            assertThat(view.totalDue()).isEqualTo(euros("157.50"));
            assertThat(view.dropoff().mileageKm()).isEqualTo(12_480);
            assertThat(events.published(VehicleReturned.class)).singleElement()
                    .satisfies(e -> assertThat(e.mileageKm()).isEqualTo(12_480));
            assertThat(events.published(LateReturnPenaltyApplied.class)).hasSize(1);
        }

        @Test
        @DisplayName("un depart sans confirmation est refuse, et rien n'est publie")
        void depart_sans_confirmation() {
            UUID id = reserve(catalog.addRentable("45.00"));
            int publishedBefore = events.published().size();

            assertThatThrownBy(() -> pickUp.pickUp(id, new HandoverCommand(START, 12_000, 100)))
                    .isInstanceOf(IllegalRentalTransitionException.class);
            assertThat(events.published()).hasSize(publishedBefore);
        }
    }

    @Nested
    @DisplayName("Rechercher des disponibilites")
    class Disponibilites {

        @Test
        @DisplayName("chaque vehicule disponible est renvoye avec son devis detaille")
        void devis_par_vehicule() {
            catalog.addRentable("100.00");
            catalog.addNotRentable("50.00");

            List<AvailableVehicleView> found = findAvailable.findAvailable(START, START.plusDays(7));

            assertThat(found).singleElement().satisfies(v -> {
                assertThat(v.total()).isEqualTo(euros("630.00"));
                assertThat(v.priceLines()).hasSize(2);
            });
        }
    }
}
