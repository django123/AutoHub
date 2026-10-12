package com.ocarius.autohub.fleet.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : mettre en service un vehicule prepare.
 *
 * <p>Invariant garanti : seul un vehicule prepare et non endommage rejoint le
 * parc disponible.
 */
public interface CommissionVehicleUseCase {

    /** @param vehicleId le vehicule */
    void commission(UUID vehicleId);
}
