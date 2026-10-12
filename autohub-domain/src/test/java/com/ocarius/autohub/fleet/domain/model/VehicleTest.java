package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.fleet.domain.event.VehicleAddedToFleet;
import com.ocarius.autohub.fleet.domain.event.VehicleMileageUpdated;
import com.ocarius.autohub.fleet.domain.event.VehicleOfferingChanged;
import com.ocarius.autohub.fleet.domain.event.VehicleSold;
import com.ocarius.autohub.fleet.domain.event.VehicleWithdrawn;
import com.ocarius.autohub.fleet.domain.exception.IllegalVehicleTransitionException;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests de l'agregat {@link Vehicle} et de sa machine a etats, sans Spring. */
class VehicleTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-01T10:00:00Z"), ZoneOffset.UTC);

    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        vehicle = acquire(Set.of(Offering.FOR_RENT));
    }

    private static Vehicle acquire(Set<Offering> offerings) {
        return Vehicle.acquire(VehicleId.newId(), Vin.of("VF3ABCDEF12345678"), PlateNumber.of("AB-123-CD"),
                new VehicleModel("Peugeot", "208", 2024), Money.euros("45.00"), Mileage.of(10_000),
                VehicleCondition.NEW, offerings, CLOCK);
    }

    /** Amene le vehicule dans le parc disponible et vide ses evenements. */
    private void putInFleet() {
        vehicle.startPreparation();
        vehicle.commission();
        vehicle.pullDomainEvents();
    }

    private static String codeOf(Throwable e) {
        return ((BusinessRuleViolation) e).code();
    }

    @Nested
    @DisplayName("Acquisition")
    class Acquisition {

        @Test
        @DisplayName("un vehicule acquis entre en ACQUIRED, n'est pas louable et emet VehicleAddedToFleet")
        void acquisition() {
            assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACQUIRED);
            assertThat(vehicle.isRentable()).isFalse();
            assertThat(vehicle.peekDomainEvents()).singleElement().isInstanceOf(VehicleAddedToFleet.class);
        }

        @Test
        @DisplayName("un tarif journalier nul est refuse")
        void tarif_nul() {
            assertThatThrownBy(() -> Vehicle.acquire(VehicleId.newId(), Vin.of("VF3ABCDEF12345678"),
                    PlateNumber.of("AB-123-CD"), new VehicleModel("Peugeot", "208", 2024), Money.zeroEuros(),
                    Mileage.of(0), VehicleCondition.NEW, Set.of(), CLOCK))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VEHICLE_DAILY_RATE_NOT_POSITIVE"));
        }

        @Test
        @DisplayName("* modifier l'ensemble passe a l'acquisition ne modifie pas le vehicule")
        void copie_defensive_des_offres() {
            Set<Offering> offers = new HashSet<>(Set.of(Offering.FOR_RENT));
            Vehicle v = acquire(offers);
            offers.add(Offering.FOR_SALE);

            assertThat(v.isOfferedFor(Offering.FOR_SALE)).isFalse();
            assertThatThrownBy(() -> v.offerings().add(Offering.FOR_SALE))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Machine a etats")
    class MachineAEtats {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"ACQUIRED, IN_PREPARATION", "IN_PREPARATION, IN_FLEET", "IN_FLEET, IN_MAINTENANCE",
                "IN_MAINTENANCE, IN_FLEET", "IN_FLEET, SOLD", "IN_FLEET, RETIRED", "IN_MAINTENANCE, RETIRED"})
        void transitions_autorisees(VehicleStatus from, VehicleStatus to) {
            assertThat(from.canTransitionTo(to)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"ACQUIRED, IN_FLEET", "IN_MAINTENANCE, SOLD", "ACQUIRED, SOLD", "IN_PREPARATION, IN_MAINTENANCE"})
        @DisplayName("transitions interdites")
        void transitions_interdites(VehicleStatus from, VehicleStatus to) {
            assertThat(from.canTransitionTo(to)).isFalse();
        }

        @ParameterizedTest(name = "{0} est terminal")
        @EnumSource(value = VehicleStatus.class, names = {"SOLD", "RETIRED"})
        void etats_terminaux(VehicleStatus terminal) {
            assertThat(terminal.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("on ne met pas en service un vehicule qui n'a pas ete prepare")
        void mise_en_service_sans_preparation() {
            assertThatThrownBy(() -> vehicle.commission()).isInstanceOf(IllegalVehicleTransitionException.class);
        }

        @Test
        @DisplayName("un vehicule prepare et mis en service, propose a la location, est louable")
        void cycle_nominal() {
            putInFleet();
            assertThat(vehicle.status()).isEqualTo(VehicleStatus.IN_FLEET);
            assertThat(vehicle.isRentable()).isTrue();
        }

        @Test
        @DisplayName("* on ne vend pas un vehicule en maintenance")
        void vente_en_maintenance() {
            putInFleet();
            vehicle.sendToMaintenance();
            assertThatThrownBy(() -> vehicle.sell(CLOCK)).isInstanceOf(IllegalVehicleTransitionException.class);
        }

        @Test
        @DisplayName("vendre un vehicule le sort du parc, efface ses offres et emet VehicleSold")
        void vente() {
            putInFleet();
            vehicle.sell(CLOCK);

            assertThat(vehicle.status()).isEqualTo(VehicleStatus.SOLD);
            assertThat(vehicle.offerings()).isEmpty();
            assertThat(vehicle.isRentable()).isFalse();
            assertThat(vehicle.peekDomainEvents()).singleElement().isInstanceOf(VehicleSold.class);
        }

        @Test
        @DisplayName("* un vehicule irreparable peut etre retire depuis la maintenance")
        void retrait_depuis_maintenance() {
            putInFleet();
            vehicle.sendToMaintenance();
            vehicle.retire(CLOCK);

            assertThat(vehicle.status()).isEqualTo(VehicleStatus.RETIRED);
            assertThat(vehicle.peekDomainEvents()).singleElement().isInstanceOf(VehicleWithdrawn.class);
        }
    }

    @Nested
    @DisplayName("Dommages")
    class Dommages {

        @Test
        @DisplayName("signaler un dommage sur un vehicule disponible l'envoie en maintenance")
        void dommage_en_service() {
            putInFleet();
            vehicle.reportDamage();

            assertThat(vehicle.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
            assertThat(vehicle.condition()).isEqualTo(VehicleCondition.DAMAGED);
            assertThat(vehicle.isRentable()).isFalse();
        }

        @Test
        @DisplayName("la sortie de maintenance repare le vehicule")
        void sortie_de_maintenance() {
            putInFleet();
            vehicle.reportDamage();
            vehicle.completeMaintenance();

            assertThat(vehicle.condition()).isEqualTo(VehicleCondition.USED);
            assertThat(vehicle.isRentable()).isTrue();
        }

        @Test
        @DisplayName("* un vehicule endommage en preparation ne peut pas etre mis en service")
        void dommage_en_preparation() {
            vehicle.startPreparation();
            vehicle.reportDamage();

            assertThatThrownBy(() -> vehicle.commission())
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VEHICLE_DAMAGED"));
        }
    }

    @Nested
    @DisplayName("Kilometrage")
    class Kilometrage {

        @Test
        @DisplayName("une saisie superieure met a jour le kilometrage et emet VehicleMileageUpdated")
        void saisie_superieure() {
            vehicle.pullDomainEvents();
            vehicle.updateMileage(Mileage.of(10_500), CLOCK);

            assertThat(vehicle.mileage()).isEqualTo(Mileage.of(10_500));
            assertThat(vehicle.peekDomainEvents()).singleElement()
                    .isInstanceOfSatisfying(VehicleMileageUpdated.class, e -> {
                        assertThat(e.previousKm()).isEqualTo(10_000);
                        assertThat(e.currentKm()).isEqualTo(10_500);
                    });
        }

        @Test
        @DisplayName("une saisie manuelle inferieure est refusee")
        void saisie_inferieure() {
            assertThatThrownBy(() -> vehicle.updateMileage(Mileage.of(9_999), CLOCK))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VEHICLE_MILEAGE_DECREASED"));
        }

        @Test
        @DisplayName("* un releve de retour rejoue deux fois ne produit qu'une mise a jour (idempotence)")
        void releve_de_retour_rejoue() {
            vehicle.pullDomainEvents();

            assertThat(vehicle.recordReturnMileage(Mileage.of(10_420), CLOCK)).isTrue();
            assertThat(vehicle.recordReturnMileage(Mileage.of(10_420), CLOCK)).isFalse();
            assertThat(vehicle.peekDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("* un releve de retour plus ancien, arrive en retard, est ignore sans erreur")
        void releve_de_retour_en_retard() {
            vehicle.recordReturnMileage(Mileage.of(11_000), CLOCK);

            assertThat(vehicle.recordReturnMileage(Mileage.of(10_500), CLOCK)).isFalse();
            assertThat(vehicle.mileage()).isEqualTo(Mileage.of(11_000));
        }
    }

    @Nested
    @DisplayName("Offres")
    class Offres {

        @Test
        @DisplayName("proposer a la vente emet VehicleOfferingChanged ; reproposer ne fait rien")
        void proposer() {
            vehicle.pullDomainEvents();
            vehicle.offer(Offering.FOR_SALE, CLOCK);
            vehicle.offer(Offering.FOR_SALE, CLOCK);

            assertThat(vehicle.offerings()).containsExactlyInAnyOrder(Offering.FOR_RENT, Offering.FOR_SALE);
            assertThat(vehicle.peekDomainEvents()).singleElement().isInstanceOf(VehicleOfferingChanged.class);
        }

        @Test
        @DisplayName("retirer FOR_RENT rend le vehicule non louable")
        void retirer_la_location() {
            putInFleet();
            vehicle.withdrawOffering(Offering.FOR_RENT, CLOCK);
            assertThat(vehicle.isRentable()).isFalse();
        }

        @Test
        @DisplayName("un vehicule vendu ne recoit plus d'offre")
        void offre_apres_vente() {
            putInFleet();
            vehicle.sell(CLOCK);
            assertThatThrownBy(() -> vehicle.offer(Offering.FOR_RENT, CLOCK))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VEHICLE_ALREADY_GONE"));
        }
    }
}
