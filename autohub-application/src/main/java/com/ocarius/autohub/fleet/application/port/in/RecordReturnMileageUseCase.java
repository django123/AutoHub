package com.ocarius.autohub.fleet.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : reporter sur le vehicule le kilometrage releve au retour d'une
 * location.
 *
 * <p>Appele par le consommateur de l'evenement {@code VehicleReturned}
 * (ADR 0004), jamais directement par le contexte {@code rental}.
 *
 * <p>Invariant garanti : le kilometrage du vehicule ne recule jamais, et
 * rejouer le meme releve ne change rien (idempotence).
 */
public interface RecordReturnMileageUseCase {

    /**
     * @param vehicleId le vehicule
     * @param mileageKm kilometrage releve au retour
     * @return {@code true} si le kilometrage du vehicule a change
     */
    boolean recordReturnMileage(UUID vehicleId, long mileageKm);
}
