package com.ocarius.autohub.fleet.application.service;

import com.ocarius.autohub.fleet.application.port.in.AddVehicleCommand;
import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.fleet.domain.event.VehicleAddedToFleet;
import com.ocarius.autohub.fleet.domain.event.VehicleMileageUpdated;
import com.ocarius.autohub.fleet.domain.exception.IllegalVehicleTransitionException;
import com.ocarius.autohub.fleet.domain.exception.VehicleNotFoundException;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.shared.application.fake.DirectUnitOfWork;
import com.ocarius.autohub.shared.application.fake.MutableClock;
import com.ocarius.autohub.shared.application.fake.RecordingEventPublisher;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.VehicleId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests des cas d'usage du contexte fleet, avec des fakes en memoire. */
class FleetUseCasesTest {

    /** Fake du port : une map, rien de plus. */
    private static final class InMemoryVehicleRepository implements VehicleRepository {
        private final Map<VehicleId, Vehicle> vehicles = new HashMap<>();
        private int saves;

        @Override
        public void save(Vehicle vehicle) {
            saves++;
            vehicles.put(vehicle.id(), vehicle);
        }

        @Override
        public Optional<Vehicle> findById(VehicleId id) {
            return Optional.ofNullable(vehicles.get(id));
        }
    }

    private final MutableClock clock = new MutableClock(Instant.parse("2027-01-01T10:00:00Z"), ZoneId.of("Europe/Paris"));
    private final InMemoryVehicleRepository vehicles = new InMemoryVehicleRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final DirectUnitOfWork unitOfWork = new DirectUnitOfWork();

    private final AddVehicleToFleetService add = new AddVehicleToFleetService(vehicles, events, unitOfWork, clock);
    private final PrepareVehicleService prepare = new PrepareVehicleService(vehicles, events, unitOfWork);
    private final CommissionVehicleService commission = new CommissionVehicleService(vehicles, events, unitOfWork);
    private final GetVehicleService get = new GetVehicleService(vehicles, unitOfWork);
    private final RecordReturnMileageService recordMileage =
            new RecordReturnMileageService(vehicles, events, unitOfWork, clock);

    private static AddVehicleCommand command(String condition, Set<String> offerings) {
        return new AddVehicleCommand("vf3abcdef12345678", "ab-123-cd", "Peugeot", "208", 2024, 10_000,
                condition, offerings, new BigDecimal("45.00"), "EUR");
    }

    @Test
    @DisplayName("ajouter, preparer, mettre en service : le vehicule devient louable")
    void cycle_d_entree_dans_le_parc() {
        UUID id = add.add(command("NEW", Set.of("FOR_RENT")));
        assertThat(get.get(id).rentable()).isFalse();

        prepare.startPreparation(id);
        commission.commission(id);

        assertThat(get.get(id).status()).isEqualTo("IN_FLEET");
        assertThat(get.get(id).rentable()).isTrue();
        assertThat(get.get(id).vin()).isEqualTo("VF3ABCDEF12345678");
        assertThat(events.published(VehicleAddedToFleet.class)).hasSize(1);
    }

    @Test
    @DisplayName("* un etat ou une offre inconnus sont une saisie invalide, pas une erreur interne")
    void enum_inconnu() {
        assertThatThrownBy(() -> add.add(command("NEUF", Set.of())))
                .isInstanceOf(BusinessRuleViolation.class)
                .satisfies(e -> assertThat(((BusinessRuleViolation) e).code()).isEqualTo("VEHICLE_CONDITION_UNKNOWN"));
        assertThatThrownBy(() -> add.add(command("NEW", Set.of("FOR_LEASE"))))
                .satisfies(e -> assertThat(((BusinessRuleViolation) e).code()).isEqualTo("VEHICLE_OFFERING_UNKNOWN"));
    }

    @Test
    @DisplayName("mettre en service un vehicule non prepare est refuse")
    void mise_en_service_sans_preparation() {
        UUID id = add.add(command("NEW", Set.of("FOR_RENT")));
        assertThatThrownBy(() -> commission.commission(id)).isInstanceOf(IllegalVehicleTransitionException.class);
    }

    @Test
    @DisplayName("un vehicule inconnu leve VehicleNotFoundException")
    void vehicule_inconnu() {
        assertThatThrownBy(() -> get.get(UUID.randomUUID())).isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    @DisplayName("* le kilometrage d'un retour rejoue deux fois n'est enregistre qu'une fois")
    void kilometrage_de_retour_idempotent() {
        UUID id = add.add(command("NEW", Set.of("FOR_RENT")));
        int savesBefore = vehicles.saves;

        assertThat(recordMileage.recordReturnMileage(id, 10_480)).isTrue();
        assertThat(recordMileage.recordReturnMileage(id, 10_480)).isFalse();

        assertThat(get.get(id).mileageKm()).isEqualTo(10_480);
        assertThat(vehicles.saves - savesBefore).as("aucune ecriture pour un releve deja connu").isEqualTo(1);
        assertThat(events.published(VehicleMileageUpdated.class)).hasSize(1);
    }
}
