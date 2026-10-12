package com.ocarius.autohub.fleet.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : commencer la preparation d'un vehicule acquis.
 *
 * <p>Invariant garanti : seul un vehicule {@code ACQUIRED} peut entrer en preparation.
 */
public interface PrepareVehicleUseCase {

    /** @param vehicleId le vehicule */
    void startPreparation(UUID vehicleId);
}
