package com.ocarius.autohub.fleet.domain.exception;

import com.ocarius.autohub.shared.domain.NotFoundException;
import com.ocarius.autohub.shared.domain.VehicleId;

/** Aucun vehicule du parc ne porte cet identifiant. */
public class VehicleNotFoundException extends NotFoundException {

    /** @param vehicleId l'identifiant demande */
    public VehicleNotFoundException(VehicleId vehicleId) {
        super("VEHICLE_NOT_FOUND", "Aucun vehicule " + vehicleId.value() + " dans le parc");
    }
}
