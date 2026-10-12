package com.ocarius.autohub.rental.domain.event;

import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La reservation n'a pas ete confirmee a temps : le vehicule est libere.
 *
 * <p>Comme tous les evenements, il est enregistre par l'agregat puis recolte
 * par la couche application apres commit : voir {@code AggregateRoot}.
 *
 * @param eventId    identifiant unique de l'occurrence (deduplication)
 * @param occurredOn instant du fait metier, lu sur l'horloge injectee
 * @param rentalId   la location concernee
 * @param vehicleId  le vehicule concerne, pour que les consommateurs (fleet)
 *                   n'aient pas a recharger la location
 */
public record RentalExpired(UUID eventId, Instant occurredOn, RentalId rentalId, VehicleId vehicleId)
        implements DomainEvent {

    /** Refuse les champs obligatoires absents : un evenement incomplet est inexploitable. */
    public RentalExpired {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredOn);
        Objects.requireNonNull(rentalId);
        Objects.requireNonNull(vehicleId);
    }
}
