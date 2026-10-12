package com.ocarius.autohub.rental.domain.event;

import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.domain.DomainEvent;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Le vehicule est revenu en retard : une penalite s'ajoute au prix.
 *
 * <p>Emis en plus de {@link VehicleReturned}, et seulement quand la penalite
 * est non nulle : un consommateur (facturation, relance client) n'a pas a
 * filtrer les retours a l'heure.
 *
 * @param eventId    identifiant unique de l'occurrence
 * @param occurredOn instant du fait metier
 * @param rentalId   la location concernee
 * @param vehicleId  le vehicule concerne
 * @param penalty    montant de la penalite, strictement positif
 */
public record LateReturnPenaltyApplied(UUID eventId, Instant occurredOn, RentalId rentalId,
                                       VehicleId vehicleId, Money penalty) implements DomainEvent {

    /** Refuse les champs absents. */
    public LateReturnPenaltyApplied {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredOn);
        Objects.requireNonNull(rentalId);
        Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(penalty);
    }
}
