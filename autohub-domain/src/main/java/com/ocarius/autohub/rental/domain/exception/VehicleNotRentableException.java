package com.ocarius.autohub.rental.domain.exception;

import com.ocarius.autohub.shared.domain.ConflictException;
import com.ocarius.autohub.shared.domain.VehicleId;

/**
 * Le vehicule existe, mais n'est pas proposable a la location aujourd'hui :
 * en preparation, en maintenance, endommage, vendu, ou non propose a la
 * location.
 *
 * <p>A distinguer de {@link VehicleNotAvailableException} : celle-ci dit "le
 * vehicule est louable, mais deja pris sur cette periode". Les deux sont des
 * conflits, mais le client n'y repond pas pareil (changer de vehicule, ou
 * changer de dates).
 */
public class VehicleNotRentableException extends ConflictException {

    /** @param vehicleId le vehicule demande */
    public VehicleNotRentableException(VehicleId vehicleId) {
        super("VEHICLE_NOT_RENTABLE", "Le vehicule " + vehicleId.value() + " n'est pas propose a la location");
    }
}
