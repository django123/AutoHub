package com.ocarius.autohub.fleet.application.service;

import com.ocarius.autohub.fleet.application.port.in.GetVehicleQuery;
import com.ocarius.autohub.fleet.application.port.in.VehicleView;
import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.util.UUID;

/** Cas d'usage en lecture : consulter un vehicule. La vue est construite dans la transaction. */
public final class GetVehicleService implements GetVehicleQuery {

    private final VehicleRepository vehicles;
    private final UnitOfWork unitOfWork;

    /**
     * @param vehicles   stockage des vehicules
     * @param unitOfWork frontiere transactionnelle
     */
    public GetVehicleService(VehicleRepository vehicles, UnitOfWork unitOfWork) {
        this.vehicles = vehicles;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public VehicleView get(UUID vehicleId) {
        return unitOfWork.execute(() -> VehicleView.from(Vehicles.load(vehicles, vehicleId)));
    }
}
