package com.ocarius.autohub.fleet.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Donnees d'un vehicule a enregistrer, en types standard.
 *
 * @param vin               numero VIN
 * @param plateNumber       immatriculation AA-123-AA
 * @param brand             marque
 * @param model             modele
 * @param year              annee-modele
 * @param mileageKm         kilometrage a l'acquisition
 * @param condition         {@code NEW}, {@code USED} ou {@code DAMAGED}
 * @param offerings         {@code FOR_RENT} et/ou {@code FOR_SALE}
 * @param dailyRentalRate   tarif journalier de location
 * @param currency          devise du tarif (ISO 4217)
 */
public record AddVehicleCommand(String vin, String plateNumber, String brand, String model, int year,
                                long mileageKm, String condition, Set<String> offerings,
                                BigDecimal dailyRentalRate, String currency) {

    /** Refuse les champs absents et copie l'ensemble des offres. */
    public AddVehicleCommand {
        Objects.requireNonNull(vin, "vin");
        Objects.requireNonNull(plateNumber, "plateNumber");
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(dailyRentalRate, "dailyRentalRate");
        Objects.requireNonNull(currency, "currency");
        offerings = offerings == null ? Set.of() : Set.copyOf(offerings);
    }
}
