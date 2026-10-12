package com.ocarius.autohub.fleet.infrastructure.in.rest.dto;

import com.ocarius.autohub.fleet.application.port.in.AddVehicleCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Corps de {@code POST /api/vehicles}. Validation de forme uniquement : le
 * format du VIN et de l'immatriculation est verifie par le domaine (422).
 *
 * @param vin             VIN
 * @param plateNumber     immatriculation AA-123-AA
 * @param brand           marque
 * @param model           modele
 * @param year            annee-modele
 * @param mileageKm       kilometrage
 * @param condition       NEW, USED ou DAMAGED
 * @param offerings       FOR_RENT et/ou FOR_SALE
 * @param dailyRentalRate tarif journalier
 * @param currency        devise ISO 4217
 */
public record AddVehicleRequest(@NotBlank String vin, @NotBlank String plateNumber, @NotBlank String brand,
                                @NotBlank String model, int year, @PositiveOrZero long mileageKm,
                                @NotBlank String condition, Set<String> offerings,
                                @NotNull @Positive BigDecimal dailyRentalRate, @NotBlank String currency) {

    /** Traduit la requete en commande du port entrant. */
    public AddVehicleCommand toCommand() {
        return new AddVehicleCommand(vin, plateNumber, brand, model, year, mileageKm, condition, offerings,
                dailyRentalRate, currency);
    }
}
