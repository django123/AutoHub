package com.ocarius.autohub.rental.infrastructure.in.rest.dto;

import com.ocarius.autohub.rental.application.port.in.HandoverCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

/**
 * Corps de {@code POST /api/rentals/{id}/pickup} et {@code .../return} : l'etat
 * des lieux.
 *
 * <p>Les bornes (kilometrage positif, carburant 0-100) sont aussi verifiees par
 * {@code VehicleHandover}. Exception assumee a la regle "pas de regle metier
 * dans les DTO" : ce sont des bornes de <i>forme</i>, et les verifier ici
 * produit un 400 qui pointe le champ fautif.
 *
 * @param at               date-heure de la remise
 * @param mileageKm        kilometrage au compteur
 * @param fuelLevelPercent carburant, de 0 a 100
 */
public record HandoverRequest(@NotNull LocalDateTime at, @PositiveOrZero long mileageKm,
                              @Min(0) @Max(100) int fuelLevelPercent) {

    /** Traduit la requete en commande du port entrant. */
    public HandoverCommand toCommand() {
        return new HandoverCommand(at, mileageKm, fuelLevelPercent);
    }
}
