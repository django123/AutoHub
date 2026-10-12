package com.ocarius.autohub.rental.application.port.in;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Etat des lieux saisi au depart ou au retour du vehicule.
 *
 * @param at               date-heure nominale de la remise
 * @param mileageKm        kilometrage au compteur
 * @param fuelLevelPercent niveau de carburant, de 0 a 100
 */
public record HandoverCommand(LocalDateTime at, long mileageKm, int fuelLevelPercent) {

    /** Refuse une date absente. */
    public HandoverCommand {
        Objects.requireNonNull(at, "at");
    }
}
