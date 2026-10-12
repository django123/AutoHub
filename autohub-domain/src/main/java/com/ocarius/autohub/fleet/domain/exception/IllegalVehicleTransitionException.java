package com.ocarius.autohub.fleet.domain.exception;

import com.ocarius.autohub.fleet.domain.model.VehicleStatus;
import com.ocarius.autohub.shared.domain.ConflictException;
import com.ocarius.autohub.shared.domain.VehicleId;

/**
 * Transition refusee par la machine a etats du vehicule : vendre un vehicule
 * en maintenance, mettre en service un vehicule non prepare, etc.
 *
 * <p>Un conflit, pas une donnee invalide : la meme demande peut etre valable
 * quand le vehicule sera dans un autre etat.
 */
public class IllegalVehicleTransitionException extends ConflictException {

    /**
     * @param vehicleId le vehicule concerne
     * @param from      son etat actuel
     * @param to        l'etat demande
     */
    public IllegalVehicleTransitionException(VehicleId vehicleId, VehicleStatus from, VehicleStatus to) {
        super("VEHICLE_TRANSITION_FORBIDDEN",
                "Le vehicule " + vehicleId.value() + " ne peut pas passer de " + from + " a " + to);
    }
}
