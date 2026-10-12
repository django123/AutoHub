package com.ocarius.autohub.fleet.application.port.in;

import com.ocarius.autohub.fleet.domain.model.Offering;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.shared.application.view.MoneyView;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vue en lecture d'un vehicule, en types standard.
 *
 * @param id              identifiant
 * @param vin             VIN
 * @param plateNumber     immatriculation
 * @param brand           marque
 * @param model           modele
 * @param year            annee-modele
 * @param mileageKm       kilometrage courant
 * @param condition       etat physique
 * @param status          statut dans le parc
 * @param offerings       offres en cours
 * @param dailyRentalRate tarif journalier
 * @param rentable        peut-il partir en location ?
 */
public record VehicleView(UUID id, String vin, String plateNumber, String brand, String model, int year,
                          long mileageKm, String condition, String status, Set<String> offerings,
                          MoneyView dailyRentalRate, boolean rentable) {

    /** Construit la vue depuis l'agregat. */
    public static VehicleView from(Vehicle vehicle) {
        return new VehicleView(
                vehicle.id().value(),
                vehicle.vin().value(),
                vehicle.plate().value(),
                vehicle.model().brand(),
                vehicle.model().name(),
                vehicle.model().year(),
                vehicle.mileage().kilometers(),
                vehicle.condition().name(),
                vehicle.status().name(),
                vehicle.offerings().stream().map(Offering::name).collect(Collectors.toUnmodifiableSet()),
                MoneyView.of(vehicle.dailyRentalRate()),
                vehicle.isRentable());
    }
}
