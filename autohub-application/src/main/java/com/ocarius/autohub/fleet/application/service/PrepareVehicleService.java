package com.ocarius.autohub.fleet.application.service;

import com.ocarius.autohub.fleet.application.port.in.PrepareVehicleUseCase;
import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.util.UUID;

/**
 * Cas d'usage : commencer la preparation d'un vehicule acquis.
 *
 * <p>La transition et ses conditions sont dans {@code Vehicle} ; ce cas
 * d'usage charge, demande, enregistre.
 */
public final class PrepareVehicleService implements PrepareVehicleUseCase {

    private final VehicleRepository vehicles;
    private final EventPublisher events;
    private final UnitOfWork unitOfWork;

    /**
     * @param vehicles   stockage des vehicules
     * @param events     publication des evenements
     * @param unitOfWork frontiere transactionnelle
     */
    public PrepareVehicleService(VehicleRepository vehicles, EventPublisher events, UnitOfWork unitOfWork) {
        this.vehicles = vehicles;
        this.events = events;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public void startPreparation(UUID vehicleId) {
        unitOfWork.run(() -> {
            Vehicle vehicle = Vehicles.load(vehicles, vehicleId);
            vehicle.startPreparation();
            Vehicles.saveAndPublish(vehicles, events, vehicle);
        });
    }
}
