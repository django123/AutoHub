package com.ocarius.autohub.fleet.application.port.out;

import com.ocarius.autohub.fleet.domain.exception.ConcurrentVehicleModificationException;
import com.ocarius.autohub.fleet.domain.exception.VehicleAlreadyRegisteredException;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.util.Optional;

/**
 * PORT SORTANT : stockage des vehicules du parc.
 *
 * <p>Utilise uniquement par le contexte {@code fleet}. Le contexte
 * {@code rental} a son propre port ({@code VehicleCatalog}) : appeler celui-ci
 * depuis {@code rental} est interdit par ArchUnit (ADR 0004).
 */
public interface VehicleRepository {

    /**
     * Enregistre un vehicule nouveau ou modifie, dans la transaction du cas d'usage.
     *
     * @throws VehicleAlreadyRegisteredException       VIN ou immatriculation deja presents
     * @throws ConcurrentVehicleModificationException  modifie par une autre transaction
     */
    void save(Vehicle vehicle);

    /** Recharge un vehicule par son identifiant. */
    Optional<Vehicle> findById(VehicleId id);
}
