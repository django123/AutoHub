package com.ocarius.autohub.fleet.domain.event;

import com.ocarius.autohub.shared.domain.DomainEvent;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Le kilometrage du vehicule a augmente.
 *
 * @param eventId    identifiant unique de l'occurrence (deduplication)
 * @param occurredOn instant du fait metier, lu sur l'horloge injectee
 * @param vehicleId  le vehicule concerne
 * @param previousKm kilometrage avant la mise a jour
 * @param currentKm  kilometrage apres la mise a jour
 */
public record VehicleMileageUpdated(UUID eventId, Instant occurredOn, VehicleId vehicleId, long previousKm, long currentKm) implements DomainEvent {

    /** Refuse les champs obligatoires absents. */
    public VehicleMileageUpdated {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredOn);
        Objects.requireNonNull(vehicleId);
    }
}
