package com.ocarius.autohub.rental.domain.event;

import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La location a ete annulee avant le depart du vehicule, qui redevient disponible.
 *
 * <p>Comme tous les evenements, il est enregistre par l'agregat puis recolte
 * par la couche application apres commit : voir {@code AggregateRoot}.
 *
 * @param eventId    identifiant unique de l'occurrence (deduplication)
 * @param occurredOn instant du fait metier, lu sur l'horloge injectee
 * @param rentalId   la location concernee
 * @param vehicleId  le vehicule concerne, pour que les consommateurs (fleet)
 *                   n'aient pas a recharger la location
 * @param reason motif libre de l'annulation, pour l'historique
 */
public record RentalCancelled(UUID eventId, Instant occurredOn, RentalId rentalId, VehicleId vehicleId, String reason)
        implements DomainEvent {

    /** Refuse les champs obligatoires absents : un evenement incomplet est inexploitable. */
    public RentalCancelled {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredOn);
        Objects.requireNonNull(rentalId);
        Objects.requireNonNull(vehicleId);
    }
}
