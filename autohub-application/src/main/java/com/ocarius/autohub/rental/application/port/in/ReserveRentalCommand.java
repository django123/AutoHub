package com.ocarius.autohub.rental.application.port.in;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Donnees d'une demande de reservation, en types standard : la conversion en
 * objets du domaine (et donc leur validation) a lieu dans le cas d'usage.
 *
 * @param vehicleId  vehicule demande
 * @param customerId client
 * @param start      debut de la location, heure nominale a l'agence (inclus)
 * @param end        fin de la location (exclue)
 */
public record ReserveRentalCommand(UUID vehicleId, UUID customerId, LocalDateTime start, LocalDateTime end) {

    /** Refuse les champs absents : le controleur les a deja valides, mais un job pourrait appeler ce port. */
    public ReserveRentalCommand {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }
}
