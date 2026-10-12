package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.shared.domain.EntityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'un vehicule du parc.
 *
 * <h2>Pourquoi ce type existe avant l'agregat {@code Vehicle}</h2>
 * L'agregat {@code Vehicle} (week-end 3) n'est pas encore ecrit, mais
 * {@code Rental} a besoin de designer un vehicule. Il le fait par son
 * <b>identifiant</b>, jamais par l'objet : charger 100 locations ne doit pas
 * charger 100 vehicules, et une location n'a aucun droit de modifier un
 * vehicule (ce serait deux agregats dans une meme transaction).
 *
 * <h2>Pourquoi dans le contexte {@code fleet}</h2>
 * Le vehicule appartient au parc. Les autres contextes ({@code rental},
 * plus tard {@code sales}) ne connaissent de lui que cet identifiant : c'est
 * la seule dependance inter-contextes toleree, car un identifiant est un
 * element de langage publie, sans comportement.
 *
 * @param value UUID technique, genere par le domaine des la creation
 */
public record VehicleId(UUID value) implements EntityId {

    /** Refuse un identifiant absent : un vehicule sans identite n'existe pas. */
    public VehicleId {
        Objects.requireNonNull(value, "L'identifiant du vehicule est obligatoire");
    }

    /** Genere un nouvel identifiant aleatoire, pour un vehicule qui entre dans le parc. */
    public static VehicleId newId() {
        return new VehicleId(UUID.randomUUID());
    }

    /** Reconstitue un identifiant connu, typiquement relu depuis la base ou une requete. */
    public static VehicleId of(UUID value) {
        return new VehicleId(value);
    }
}
