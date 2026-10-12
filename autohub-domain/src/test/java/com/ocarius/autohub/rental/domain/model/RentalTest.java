package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.rental.domain.event.LateReturnPenaltyApplied;
import com.ocarius.autohub.rental.domain.event.RentalCancelled;
import com.ocarius.autohub.rental.domain.event.RentalConfirmed;
import com.ocarius.autohub.rental.domain.event.RentalExpired;
import com.ocarius.autohub.rental.domain.event.RentalReserved;
import com.ocarius.autohub.rental.domain.event.VehiclePickedUp;
import com.ocarius.autohub.rental.domain.event.VehicleReturned;
import com.ocarius.autohub.rental.domain.exception.IllegalRentalTransitionException;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de l'agregat {@link Rental}, sans Spring ni base de donnees.
 *
 * <p>Le temps est entierement pilote par des {@link Clock} fixes : "dans
 * 31 minutes" s'ecrit {@code later(Duration.ofMinutes(31))}, sans attente
 * reelle. Les noms precedes d'un asterisque dans {@code @DisplayName} sont les
 * cas les plus souvent oublies.
 */
class RentalTest {

    /** Instant de la reservation : une semaine avant le debut de la location. */
    private static final Instant NOW = Instant.parse("2027-03-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final LocalDateTime START = LocalDateTime.of(2027, 3, 8, 8, 0);
    private static final LocalDateTime END = LocalDateTime.of(2027, 3, 10, 8, 0);
    private static final RentalPeriod PERIOD = RentalPeriod.of(START, END);
    private static final Money DAILY_RATE = Money.euros("90.00");
    private static final Money PRICE = Money.euros("180.00");
    private static final RentalQuote QUOTE = RentalQuote.of(DAILY_RATE, PRICE);

    private Rental rental;

    @BeforeEach
    void setUp() {
        rental = Rental.draft(RentalId.newId(), VehicleId.newId(), CustomerId.newId(), PERIOD, QUOTE);
    }

    /** Horloge decalee de {@code offset} par rapport a {@link #NOW}. */
    private static Clock later(Duration offset) {
        return Clock.offset(CLOCK, offset);
    }

    private static VehicleHandover handover(LocalDateTime at, long mileage) {
        return new VehicleHandover(at, mileage, 100);
    }

    /** Amene la location en CONFIRMED et vide les evenements produits. */
    private void reserveAndConfirm() {
        rental.reserve(CLOCK);
        rental.confirm(CLOCK);
        rental.pullDomainEvents();
    }

    /** Amene la location en PICKED_UP, depart au debut de la periode avec 10 000 km. */
    private void pickUpAtStart() {
        reserveAndConfirm();
        rental.pickUp(handover(START, 10_000), CLOCK);
        rental.pullDomainEvents();
    }

    private static String codeOf(Throwable e) {
        return ((BusinessRuleViolation) e).code();
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("une location nait au brouillon, sans evenement ni blocage du vehicule")
        void nait_au_brouillon() {
            assertThat(rental.status()).isEqualTo(RentalStatus.DRAFT);
            assertThat(rental.blocksVehicle()).isFalse();
            assertThat(rental.peekDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("un prix nul est refuse")
        void prix_nul_refuse() {
            // Verifie par RentalQuote : impossible de tenir en main un devis a zero.
            assertThatThrownBy(() -> RentalQuote.of(DAILY_RATE, Money.zeroEuros()))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_PRICE_NOT_POSITIVE"));
        }
    }

    @Nested
    @DisplayName("Reservation")
    class Reservation {

        @Test
        @DisplayName("reserver fixe l'echeance a 30 minutes et emet RentalReserved")
        void reserver() {
            rental.reserve(CLOCK);

            assertThat(rental.status()).isEqualTo(RentalStatus.RESERVED);
            assertThat(rental.reservedUntil()).contains(NOW.plus(Rental.RESERVATION_TTL));
            assertThat(rental.blocksVehicle()).isTrue();
            assertThat(rental.peekDomainEvents()).singleElement().isInstanceOfSatisfying(RentalReserved.class, e -> {
                assertThat(e.rentalId()).isEqualTo(rental.id());
                assertThat(e.start()).isEqualTo(START);
                assertThat(e.occurredOn()).isEqualTo(NOW);
            });
        }

        @Test
        @DisplayName("* reserver une periode qui commence dans moins d'une heure est refuse")
        void preavis_insuffisant() {
            Clock justeAvant = Clock.fixed(START.minusMinutes(30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

            assertThatThrownBy(() -> rental.reserve(justeAvant))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_NOTICE_TOO_SHORT"));
            assertThat(rental.status()).isEqualTo(RentalStatus.DRAFT);
        }

        @Test
        @DisplayName("* reserver deux fois est refuse")
        void double_reservation() {
            rental.reserve(CLOCK);
            assertThatThrownBy(() -> rental.reserve(CLOCK)).isInstanceOf(IllegalRentalTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Confirmation et expiration")
    class ConfirmationEtExpiration {

        @BeforeEach
        void reserve() {
            rental.reserve(CLOCK);
            rental.pullDomainEvents();
        }

        @Test
        @DisplayName("confirmer avant l'echeance efface l'echeance et emet RentalConfirmed")
        void confirmer_a_temps() {
            rental.confirm(later(Duration.ofMinutes(29)));

            assertThat(rental.status()).isEqualTo(RentalStatus.CONFIRMED);
            assertThat(rental.reservedUntil()).isEmpty();
            assertThat(rental.peekDomainEvents()).singleElement().isInstanceOf(RentalConfirmed.class);
        }

        @Test
        @DisplayName("* confirmer a l'instant exact de l'echeance est refuse (echeance exclue)")
        void confirmer_pile_a_l_echeance() {
            assertThatThrownBy(() -> rental.confirm(later(Rental.RESERVATION_TTL)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_RESERVATION_EXPIRED"));
            assertThat(rental.status()).isEqualTo(RentalStatus.RESERVED);
        }

        @Test
        @DisplayName("une reservation echue expire et emet RentalExpired")
        void expiration() {
            boolean expired = rental.expireIfOverdue(later(Duration.ofMinutes(31)));

            assertThat(expired).isTrue();
            assertThat(rental.status()).isEqualTo(RentalStatus.EXPIRED);
            assertThat(rental.blocksVehicle()).isFalse();
            assertThat(rental.peekDomainEvents()).singleElement().isInstanceOf(RentalExpired.class);
        }

        @Test
        @DisplayName("une reservation non echue n'expire pas")
        void pas_encore_echue() {
            assertThat(rental.expireIfOverdue(later(Duration.ofMinutes(29)))).isFalse();
            assertThat(rental.status()).isEqualTo(RentalStatus.RESERVED);
        }

        @Test
        @DisplayName("* expirer deux fois ne produit qu'un seul evenement (idempotence)")
        void expiration_idempotente() {
            rental.expireIfOverdue(later(Duration.ofHours(1)));
            boolean second = rental.expireIfOverdue(later(Duration.ofHours(2)));

            assertThat(second).isFalse();
            assertThat(rental.peekDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("* une location confirmee n'expire jamais")
        void confirmee_n_expire_pas() {
            rental.confirm(CLOCK);
            assertThat(rental.expireIfOverdue(later(Duration.ofDays(3)))).isFalse();
            assertThat(rental.status()).isEqualTo(RentalStatus.CONFIRMED);
        }

        @Test
        @DisplayName("* confirmer une reservation expiree est une transition interdite")
        void confirmer_apres_expiration() {
            rental.expireIfOverdue(later(Duration.ofHours(1)));
            assertThatThrownBy(() -> rental.confirm(later(Duration.ofHours(1))))
                    .isInstanceOf(IllegalRentalTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Annulation")
    class Annulation {

        @Test
        @DisplayName("annuler une reservation libere le vehicule et emet RentalCancelled")
        void annuler_reservation() {
            rental.reserve(CLOCK);
            rental.pullDomainEvents();

            rental.cancel("changement de programme", CLOCK);

            assertThat(rental.status()).isEqualTo(RentalStatus.CANCELLED);
            assertThat(rental.reservedUntil()).isEmpty();
            assertThat(rental.blocksVehicle()).isFalse();
            assertThat(rental.peekDomainEvents()).singleElement().isInstanceOfSatisfying(RentalCancelled.class,
                    e -> assertThat(e.reason()).isEqualTo("changement de programme"));
        }

        @Test
        @DisplayName("annuler une location confirmee est permis")
        void annuler_confirmee() {
            reserveAndConfirm();
            rental.cancel("client malade", CLOCK);
            assertThat(rental.status()).isEqualTo(RentalStatus.CANCELLED);
        }

        @Test
        @DisplayName("annuler apres le depart du vehicule est interdit")
        void annuler_apres_depart() {
            pickUpAtStart();

            assertThatThrownBy(() -> rental.cancel("trop tard", CLOCK))
                    .isInstanceOfSatisfying(IllegalRentalTransitionException.class, e -> {
                        assertThat(e.from()).isEqualTo(RentalStatus.PICKED_UP);
                        assertThat(e.to()).isEqualTo(RentalStatus.CANCELLED);
                    });
        }

        @Test
        @DisplayName("* annuler un brouillon est interdit : il n'y a rien a annuler")
        void annuler_brouillon() {
            assertThatThrownBy(() -> rental.cancel("rien", CLOCK)).isInstanceOf(IllegalRentalTransitionException.class);
        }

        @Test
        @DisplayName("* annuler deux fois est interdit")
        void annuler_deux_fois() {
            rental.reserve(CLOCK);
            rental.cancel("une fois", CLOCK);
            assertThatThrownBy(() -> rental.cancel("deux fois", CLOCK))
                    .isInstanceOf(IllegalRentalTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Depart du vehicule")
    class Depart {

        @Test
        @DisplayName("le depart pendant la periode est enregistre et emet VehiclePickedUp")
        void depart_dans_la_periode() {
            reserveAndConfirm();

            rental.pickUp(handover(START.plusHours(1), 10_000), CLOCK);

            assertThat(rental.status()).isEqualTo(RentalStatus.PICKED_UP);
            assertThat(rental.pickup()).isPresent();
            assertThat(rental.peekDomainEvents()).singleElement().isInstanceOf(VehiclePickedUp.class);
        }

        @Test
        @DisplayName("* le depart avant le debut de la periode est refuse")
        void depart_anticipe() {
            reserveAndConfirm();
            assertThatThrownBy(() -> rental.pickUp(handover(START.minusMinutes(1), 10_000), CLOCK))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_PICKUP_OUTSIDE_PERIOD"));
        }

        @Test
        @DisplayName("* le depart a la fin exacte de la periode est refuse (fin exclue)")
        void depart_a_la_fin() {
            reserveAndConfirm();
            assertThatThrownBy(() -> rental.pickUp(handover(END, 10_000), CLOCK))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_PICKUP_OUTSIDE_PERIOD"));
        }

        @Test
        @DisplayName("partir sans confirmation est interdit")
        void depart_sans_confirmation() {
            rental.reserve(CLOCK);
            assertThatThrownBy(() -> rental.pickUp(handover(START, 10_000), CLOCK))
                    .isInstanceOf(IllegalRentalTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Retour du vehicule")
    class Retour {

        @BeforeEach
        void partir() {
            pickUpAtStart();
        }

        @Test
        @DisplayName("un retour a l'heure est enregistre et emet VehicleReturned")
        void retour_a_l_heure() {
            rental.returnVehicle(handover(END.minusHours(1), 10_420), CLOCK);

            assertThat(rental.status()).isEqualTo(RentalStatus.RETURNED);
            assertThat(rental.isLateReturn()).isFalse();
            assertThat(rental.kilometersDriven()).contains(420L);
            assertThat(rental.peekDomainEvents()).singleElement().isInstanceOfSatisfying(VehicleReturned.class, e -> {
                assertThat(e.mileageKm()).isEqualTo(10_420);
                assertThat(e.late()).isFalse();
            });
        }

        @Test
        @DisplayName("* un retour avant la date prevue est accepte et ne change pas le prix")
        void retour_anticipe() {
            rental.returnVehicle(handover(START.plusHours(5), 10_050), CLOCK);

            assertThat(rental.status()).isEqualTo(RentalStatus.RETURNED);
            assertThat(rental.isLateReturn()).isFalse();
            assertThat(rental.quotedPrice()).isEqualTo(PRICE);
            assertThat(rental.totalDue()).as("aucun remboursement").isEqualTo(PRICE);
            assertThat(rental.blocksVehicle()).isFalse();
        }

        @Test
        @DisplayName("* un retour a la fin exacte n'est pas un retard")
        void retour_pile_a_la_fin() {
            rental.returnVehicle(handover(END, 10_100), CLOCK);
            assertThat(rental.isLateReturn()).isFalse();
        }

        @Test
        @DisplayName("un retour apres la fin prevue est accepte mais signale en retard")
        void retour_tardif() {
            rental.returnVehicle(handover(END.plusMinutes(1), 10_100), CLOCK);

            assertThat(rental.isLateReturn()).isTrue();
            // Deux evenements : le retour, puis la penalite (voir une_minute_de_retard).
            assertThat(rental.peekDomainEvents()).hasSize(2).first()
                    .isInstanceOfSatisfying(VehicleReturned.class, e -> assertThat(e.late()).isTrue());
        }

        @Test
        @DisplayName("un kilometrage au retour inferieur a celui du depart est refuse")
        void kilometrage_en_recul() {
            assertThatThrownBy(() -> rental.returnVehicle(handover(END, 9_999), CLOCK))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_RETURN_MILEAGE_DECREASED"));
            assertThat(rental.status()).isEqualTo(RentalStatus.PICKED_UP);
        }

        @Test
        @DisplayName("* un kilometrage identique au depart est accepte (vehicule non utilise)")
        void kilometrage_identique() {
            rental.returnVehicle(handover(START.plusHours(2), 10_000), CLOCK);
            assertThat(rental.kilometersDriven()).contains(0L);
        }

        @Test
        @DisplayName("* un retour date avant le depart est refuse")
        void retour_avant_depart() {
            assertThatThrownBy(() -> rental.returnVehicle(handover(START.minusMinutes(1), 10_100), CLOCK))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_RETURN_BEFORE_PICKUP"));
        }

        @Test
        @DisplayName("un retour a l'heure n'a pas de penalite : le total du est le prix fige")
        void pas_de_penalite_a_l_heure() {
            rental.returnVehicle(handover(END, 10_100), CLOCK);

            assertThat(rental.lateReturnPenalty()).isEqualTo(Money.zeroEuros());
            assertThat(rental.totalDue()).isEqualTo(PRICE);
            assertThat(rental.peekDomainEvents()).noneMatch(LateReturnPenaltyApplied.class::isInstance);
        }

        @Test
        @DisplayName("* une minute de retard coute un jour de penalite a 150 % du tarif")
        void une_minute_de_retard() {
            rental.returnVehicle(handover(END.plusMinutes(1), 10_100), CLOCK);

            assertThat(rental.lateReturnPenalty()).isEqualTo(Money.euros("135.00"));   // 90 x 1,5
            assertThat(rental.totalDue()).isEqualTo(Money.euros("315.00"));
            assertThat(rental.peekDomainEvents()).filteredOn(LateReturnPenaltyApplied.class::isInstance)
                    .singleElement()
                    .isInstanceOfSatisfying(LateReturnPenaltyApplied.class,
                            e -> assertThat(e.penalty()).isEqualTo(Money.euros("135.00")));
        }

        @Test
        @DisplayName("* 24 h pile de retard font un jour, 24 h et une minute en font deux")
        void jours_de_retard_entames() {
            Rental exactementUnJour = Rental.restore(RentalId.newId(), VehicleId.newId(), CustomerId.newId(),
                    PERIOD, QUOTE, RentalStatus.PICKED_UP, null, handover(START, 10_000), null, Money.zeroEuros());
            exactementUnJour.returnVehicle(handover(END.plusDays(1), 10_100), CLOCK);
            assertThat(exactementUnJour.lateReturnPenalty()).isEqualTo(Money.euros("135.00"));

            rental.returnVehicle(handover(END.plusDays(1).plusMinutes(1), 10_100), CLOCK);
            assertThat(rental.lateReturnPenalty()).isEqualTo(Money.euros("270.00"));
        }

        @Test
        @DisplayName("* la penalite utilise le tarif fige, pas un tarif courant")
        void penalite_sur_tarif_fige() {
            // Le tarif est dans le devis de la location : rien, en dehors de
            // l'agregat, ne peut le modifier entre la reservation et le retour.
            rental.returnVehicle(handover(END.plusHours(2), 10_100), CLOCK);
            assertThat(rental.lateReturnPenalty()).isEqualTo(DAILY_RATE.times(Rental.LATE_RETURN_PENALTY_RATE));
        }

        @Test
        @DisplayName("la cloture apres retour est permise, avant retour non")
        void cloture() {
            assertThatThrownBy(() -> rental.close()).isInstanceOf(IllegalRentalTransitionException.class);

            rental.returnVehicle(handover(END, 10_100), CLOCK);
            rental.close();

            assertThat(rental.status()).isEqualTo(RentalStatus.CLOSED);
            assertThat(rental.status().isTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("Reconstitution depuis la persistance")
    class Reconstitution {

        @Test
        @DisplayName("* une location confirmee dont la periode est passee se recharge sans erreur")
        void recharge_location_passee() {
            // Si restore() rejouait reserve(), le preavis serait reverifie
            // aujourd'hui et cette location, valide hier, deviendrait illisible.
            RentalPeriod passee = RentalPeriod.of(LocalDateTime.of(2020, 1, 1, 8, 0), LocalDateTime.of(2020, 1, 3, 8, 0));

            Rental restored = Rental.restore(RentalId.newId(), VehicleId.newId(), CustomerId.newId(),
                    passee, QUOTE, RentalStatus.CONFIRMED, null, null, null, Money.zeroEuros());

            assertThat(restored.status()).isEqualTo(RentalStatus.CONFIRMED);
            assertThat(restored.peekDomainEvents()).as("rien ne vient de se produire").isEmpty();
        }

        @Test
        @DisplayName("* une location PICKED_UP sans etat des lieux de depart est rejetee")
        void incoherence_depart_manquant() {
            assertThatThrownBy(() -> Rental.restore(RentalId.newId(), VehicleId.newId(), CustomerId.newId(),
                    PERIOD, QUOTE, RentalStatus.PICKED_UP, null, null, null, Money.zeroEuros()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pickup");
        }

        @Test
        @DisplayName("* une location RESERVED sans echeance est rejetee")
        void incoherence_echeance_manquante() {
            assertThatThrownBy(() -> Rental.restore(RentalId.newId(), VehicleId.newId(), CustomerId.newId(),
                    PERIOD, QUOTE, RentalStatus.RESERVED, null, null, null, Money.zeroEuros()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("reservedUntil");
        }

        @Test
        @DisplayName("* une penalite sans etat des lieux de retour est rejetee")
        void incoherence_penalite_sans_retour() {
            assertThatThrownBy(() -> Rental.restore(RentalId.newId(), VehicleId.newId(), CustomerId.newId(),
                    PERIOD, QUOTE, RentalStatus.CONFIRMED, null, null, null, Money.euros("10.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lateReturnPenalty");
        }

        @Test
        @DisplayName("deux instances de meme identifiant sont egales, quel que soit leur etat")
        void egalite_par_identite() {
            Rental copie = Rental.restore(rental.id(), VehicleId.newId(), CustomerId.newId(),
                    PERIOD, RentalQuote.of(DAILY_RATE, Money.euros("999.00")), RentalStatus.CANCELLED,
                    null, null, null, Money.zeroEuros());
            assertThat(copie).isEqualTo(rental);
        }
    }
}
