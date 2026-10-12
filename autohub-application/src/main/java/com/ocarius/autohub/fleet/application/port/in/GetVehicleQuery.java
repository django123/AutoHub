package com.ocarius.autohub.fleet.application.port.in;

import java.util.UUID;

/** PORT ENTRANT (lecture) : consulter un vehicule du parc. */
public interface GetVehicleQuery {

    /**
     * @throws com.ocarius.autohub.fleet.domain.exception.VehicleNotFoundException s'il n'existe pas
     */
    VehicleView get(UUID vehicleId);
}
