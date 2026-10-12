package com.ocarius.autohub.fleet.application.service;

import com.ocarius.autohub.fleet.application.port.in.RecordReturnMileageUseCase;
import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.fleet.domain.model.Mileage;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.time.Clock;
import java.util.UUID;

/**
 * Cas d'usage : reporter le kilometrage d'un retour de location sur le vehicule.
 *
 * <p>Idempotent grace a {@code Vehicle.recordReturnMileage} : un releve deja
 * connu, ou plus ancien que le kilometrage courant, ne change rien. Rien n'est
 * enregistre dans ce cas, pour ne pas incrementer la version sans raison.
 */
public final class RecordReturnMileageService implements RecordReturnMileageUseCase {

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
    public RecordReturnMileageService(VehicleRepository vehicles, EventPublisher events, UnitOfWork unitOfWork,
                                      Clock clock) {
        this.vehicles = vehicles;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public boolean recordReturnMileage(UUID vehicleId, long mileageKm) {
        Mileage reading = Mileage.of(mileageKm);
        return unitOfWork.execute(() -> {
            Vehicle vehicle = Vehicles.load(vehicles, vehicleId);
            boolean changed = vehicle.recordReturnMileage(reading, clock);
            if (changed) {
                Vehicles.saveAndPublish(vehicles, events, vehicle);
            }
            return changed;
        });
    }
}
