package com.ocarius.autohub.fleet.domain.event;

import com.ocarius.autohub.fleet.domain.model.Offering;
import com.ocarius.autohub.shared.domain.DomainEvent;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Les offres du vehicule (location, vente) ont change.
 *
 * <p>Interessera le contexte {@code rental} (un vehicule retire de la location
 * ne doit plus apparaitre dans les disponibilites) et, en phase 4, {@code sales}.
 *
 * @param eventId    identifiant unique de l'occurrence (deduplication)
 * @param occurredOn instant du fait metier, lu sur l'horloge injectee
 * @param vehicleId  le vehicule concerne
 * @param offerings les offres apres le changement (copie immuable)
 */
public record VehicleOfferingChanged(UUID eventId, Instant occurredOn, VehicleId vehicleId, Set<Offering> offerings) implements DomainEvent {

    /** Refuse les champs obligatoires absents. */
    public VehicleOfferingChanged {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredOn);
        Objects.requireNonNull(vehicleId);
        offerings = Set.copyOf(offerings);
    }
}
