package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.EntityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'une location.
 *
 * <p>Genere par le domaine, et non par la base : un agregat doit avoir son
 * identite des sa creation, avant toute persistance. C'est ce qui permet
 * d'emettre un evenement {@code RentalReserved} portant deja l'identifiant,
 * et d'ecrire des tests du domaine sans aucune base de donnees.
 *
 * @param value UUID technique
 */
public record RentalId(UUID value) implements EntityId {

    /** Refuse un identifiant absent. */
    public RentalId {
        Objects.requireNonNull(value, "L'identifiant de la location est obligatoire");
    }

    /** Genere un nouvel identifiant aleatoire, pour une location qui commence sa vie. */
    public static RentalId newId() {
        return new RentalId(UUID.randomUUID());
    }

    /** Reconstitue un identifiant connu (relecture depuis la base). */
    public static RentalId of(UUID value) {
        return new RentalId(value);
    }
}
