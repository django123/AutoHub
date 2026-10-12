package com.ocarius.autohub.fleet.domain.event;

import com.ocarius.autohub.shared.domain.DomainEvent;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un vehicule vient d'etre acquis et enregistre dans le parc.
 *
 * @param eventId    identifiant unique de l'occurrence (deduplication)
 * @param occurredOn instant du fait metier, lu sur l'horloge injectee
 * @param vehicleId  le vehicule concerne
 */
public record VehicleAddedToFleet(UUID eventId, Instant occurredOn, VehicleId vehicleId) implements DomainEvent {

    /** Refuse les champs obligatoires absents. */
    public VehicleAddedToFleet {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredOn);
        Objects.requireNonNull(vehicleId);
    }
}
