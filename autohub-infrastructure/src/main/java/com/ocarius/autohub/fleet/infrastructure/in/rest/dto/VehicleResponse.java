package com.ocarius.autohub.fleet.infrastructure.in.rest.dto;

import com.ocarius.autohub.fleet.application.port.in.VehicleView;
import com.ocarius.autohub.shared.application.view.MoneyView;

import java.util.Set;
import java.util.UUID;

/**
 * Reponse JSON d'un vehicule.
 *
 * @param id              identifiant
 * @param vin             VIN
 * @param plateNumber     immatriculation
 * @param brand           marque
 * @param model           modele
 * @param year            annee-modele
 * @param mileageKm       kilometrage
 * @param condition       etat
 * @param status          statut dans le parc
 * @param offerings       offres
 * @param dailyRentalRate tarif journalier
 * @param rentable        louable aujourd'hui ?
 */
public record VehicleResponse(UUID id, String vin, String plateNumber, String brand, String model, int year,
                              long mileageKm, String condition, String status, Set<String> offerings,
                              MoneyView dailyRentalRate, boolean rentable) {

    /** Construit la reponse depuis la vue applicative. */
    public static VehicleResponse from(VehicleView v) {
        return new VehicleResponse(v.id(), v.vin(), v.plateNumber(), v.brand(), v.model(), v.year(),
                v.mileageKm(), v.condition(), v.status(), v.offerings(), v.dailyRentalRate(), v.rentable());
    }
}
