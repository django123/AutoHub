package com.ocarius.autohub.fleet.domain.exception;

import com.ocarius.autohub.shared.domain.ConflictException;
import com.ocarius.autohub.shared.domain.VehicleId;

/**
 * Le vehicule a ete modifie par une autre transaction depuis son chargement.
 * Pendant de {@code ConcurrentRentalModificationException} pour le contexte
 * {@code fleet} : levee par l'adaptateur de persistance, qui traduit l'echec
 * du verrouillage optimiste.
 */
public class ConcurrentVehicleModificationException extends ConflictException {

    /**
     * @param vehicleId le vehicule modifie concurremment
     * @param cause     l'exception technique d'origine
     */
    public ConcurrentVehicleModificationException(VehicleId vehicleId, Throwable cause) {
        super("VEHICLE_CONCURRENT_MODIFICATION",
                "Le vehicule " + vehicleId.value() + " a ete modifie entre-temps ; rechargez-le avant de reessayer");
        initCause(cause);
    }
}
