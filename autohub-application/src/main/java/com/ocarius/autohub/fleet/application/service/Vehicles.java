package com.ocarius.autohub.fleet.application.service;

import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.fleet.domain.exception.VehicleNotFoundException;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.util.UUID;

/**
 * Gestes communs aux cas d'usage du parc : charger un vehicule ou echouer,
 * puis l'enregistrer et publier ses evenements (voir {@code Rentals} cote
 * {@code rental} pour le raisonnement sur la publication).
 */
final class Vehicles {

    private Vehicles() {
    }

    static Vehicle load(VehicleRepository repository, UUID vehicleId) {
        VehicleId id = VehicleId.of(vehicleId);
        return repository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    static void saveAndPublish(VehicleRepository repository, EventPublisher events, Vehicle vehicle) {
        repository.save(vehicle);
        events.publishAll(vehicle.pullDomainEvents());
    }
}
