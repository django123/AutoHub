package com.ocarius.autohub.rental.domain.event;

import com.ocarius.autohub.fleet.domain.model.VehicleId;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.domain.DomainEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Le vehicule est revenu a l'agence.
 *
 * <p>Destine en particulier au contexte {@code fleet}, qui mettra a jour le
 * kilometrage du vehicule (week-end 9) -- sans que {@code rental} n'appelle
 * jamais {@code Vehicle} directement.
 *
 * <p>Comme tous les evenements, il est enregistre par l'agregat puis recolte
 * par la couche application apres commit : voir {@code AggregateRoot}.
 *
 * @param eventId    identifiant unique de l'occurrence (deduplication)
 * @param occurredOn instant du fait metier, lu sur l'horloge injectee
 * @param rentalId   la location concernee
 * @param vehicleId  le vehicule concerne, pour que les consommateurs (fleet)
 *                   n'aient pas a recharger la location
 * @param returnedAt date-heure nominale du retour
 * @param mileageKm  kilometrage releve au retour
 * @param late       le retour a-t-il eu lieu apres la fin prevue ?
 */
public record VehicleReturned(UUID eventId, Instant occurredOn, RentalId rentalId, VehicleId vehicleId, LocalDateTime returnedAt, long mileageKm, boolean late)
        implements DomainEvent {

    /** Refuse les champs obligatoires absents : un evenement incomplet est inexploitable. */
    public VehicleReturned {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredOn);
        Objects.requireNonNull(rentalId);
        Objects.requireNonNull(vehicleId);
    }
}
