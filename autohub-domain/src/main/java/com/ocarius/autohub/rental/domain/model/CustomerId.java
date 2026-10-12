package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.EntityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant du client qui loue.
 *
 * <p>Il n'existe pas (encore) de contexte "client" dans AutoHub : ce type est
 * donc une simple reference opaque, sans table ni agregat derriere. Le typer
 * malgre tout evite d'inverser un {@code CustomerId} et un {@code VehicleId}
 * dans une signature, erreur que le compilateur detecte alors gratuitement.
 *
 * @param value UUID technique
 */
public record CustomerId(UUID value) implements EntityId {

    /** Refuse un identifiant absent. */
    public CustomerId {
        Objects.requireNonNull(value, "L'identifiant du client est obligatoire");
    }

    /** Genere un nouvel identifiant aleatoire. */
    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }

    /** Reconstitue un identifiant connu. */
    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }
}
