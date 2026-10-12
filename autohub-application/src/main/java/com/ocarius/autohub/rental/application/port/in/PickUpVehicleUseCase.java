package com.ocarius.autohub.rental.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : enregistrer le depart du vehicule.
 *
 * <p>Invariant garanti : le depart n'est possible que depuis une location
 * confirmee, et pendant la periode louee.
 */
public interface PickUpVehicleUseCase {

    /**
     * @param rentalId la location
     * @param handover etat des lieux de depart
     */
    void pickUp(UUID rentalId, HandoverCommand handover);
}
