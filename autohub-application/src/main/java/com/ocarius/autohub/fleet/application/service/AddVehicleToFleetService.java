package com.ocarius.autohub.fleet.application.service;

import com.ocarius.autohub.fleet.application.port.in.AddVehicleCommand;
import com.ocarius.autohub.fleet.application.port.in.AddVehicleToFleetUseCase;
import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.fleet.domain.model.Mileage;
import com.ocarius.autohub.fleet.domain.model.Offering;
import com.ocarius.autohub.fleet.domain.model.PlateNumber;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.fleet.domain.model.VehicleCondition;
import com.ocarius.autohub.fleet.domain.model.VehicleModel;
import com.ocarius.autohub.fleet.domain.model.Vin;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.time.Clock;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cas d'usage : enregistrer un vehicule achete.
 *
 * <p>Les value objects sont construits avant d'ouvrir la transaction : une
 * saisie invalide echoue sans toucher a la base. L'unicite du VIN et de
 * l'immatriculation, elle, est arbitree par la base (contraintes UNIQUE).
 */
public final class AddVehicleToFleetService implements AddVehicleToFleetUseCase {

    private final VehicleRepository vehicles;
    private final EventPublisher events;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    /**
     * @param vehicles   stockage des vehicules
     * @param events     publication des evenements
     * @param unitOfWork frontiere transactionnelle
     * @param clock      horloge de l'agence
     */
    public AddVehicleToFleetService(VehicleRepository vehicles, EventPublisher events, UnitOfWork unitOfWork,
                                    Clock clock) {
        this.vehicles = vehicles;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public UUID add(AddVehicleCommand command) {
        Vehicle vehicle = Vehicle.acquire(
                VehicleId.newId(),
                Vin.of(command.vin()),
                PlateNumber.of(command.plateNumber()),
                new VehicleModel(command.brand(), command.model(), command.year()),
                Money.of(command.dailyRentalRate(), currency(command.currency())),
                Mileage.of(command.mileageKm()),
                parse(VehicleCondition.class, command.condition(), "VEHICLE_CONDITION_UNKNOWN"),
                command.offerings().stream()
                        .map(o -> parse(Offering.class, o, "VEHICLE_OFFERING_UNKNOWN"))
                        .collect(Collectors.toSet()),
                clock);
        unitOfWork.run(() -> Vehicles.saveAndPublish(vehicles, events, vehicle));
        return vehicle.id().value();
    }

    /**
     * Convertit un libelle en constante d'enum. Une valeur inconnue est une
     * saisie invalide (422), pas une erreur interne : sans ce garde,
     * {@code valueOf} leverait une {@code IllegalArgumentException} qui
     * finirait en 500.
     */
    private static <E extends Enum<E>> E parse(Class<E> type, String value, String code) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        throw new BusinessRuleViolation(code, "Valeur inconnue : " + value + ". Attendu : "
                + Set.of(type.getEnumConstants()));
    }

    private static Currency currency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolation("CURRENCY_UNKNOWN", "Devise inconnue : " + code);
        }
    }
}
