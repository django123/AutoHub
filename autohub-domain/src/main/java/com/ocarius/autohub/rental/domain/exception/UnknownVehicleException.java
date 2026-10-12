package com.ocarius.autohub.rental.domain.exception;

import com.ocarius.autohub.shared.domain.NotFoundException;
import com.ocarius.autohub.shared.domain.VehicleId;

/**
 * Le catalogue de location ne connait aucun vehicule portant cet identifiant.
 *
 * <p>Pendant cote {@code rental} du {@code VehicleNotFoundException} de
 * {@code fleet} : le contexte {@code rental} ne depend pas de {@code fleet}
 * (ADR 0004), il ne peut donc pas lever l'exception de l'autre contexte.
 */
public class UnknownVehicleException extends NotFoundException {

    /** @param vehicleId l'identifiant demande */
    public UnknownVehicleException(VehicleId vehicleId) {
        super("VEHICLE_NOT_FOUND", "Aucun vehicule " + vehicleId.value() + " dans le catalogue de location");
    }
}
