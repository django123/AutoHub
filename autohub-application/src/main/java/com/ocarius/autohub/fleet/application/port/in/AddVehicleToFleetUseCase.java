package com.ocarius.autohub.fleet.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : enregistrer un vehicule qui vient d'etre achete.
 *
 * <p>Invariant garanti : VIN et immatriculation valides et uniques dans le
 * parc ; le vehicule entre en {@code ACQUIRED} et n'est pas louable avant
 * d'avoir ete prepare puis mis en service.
 */
public interface AddVehicleToFleetUseCase {

    /**
     * @return l'identifiant du vehicule cree
     * @throws com.ocarius.autohub.fleet.domain.exception.VehicleAlreadyRegisteredException VIN ou plaque deja connus
     */
    UUID add(AddVehicleCommand command);
}
