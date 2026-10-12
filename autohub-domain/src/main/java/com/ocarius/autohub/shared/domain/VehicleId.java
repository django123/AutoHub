package com.ocarius.autohub.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'un vehicule du parc.
 *
 * <h2>Pourquoi dans le noyau partage, et pas dans {@code fleet}</h2>
 * Le vehicule appartient au contexte {@code fleet}. Mais {@code rental}, et
 * bientot {@code sales}, doivent pouvoir le <i>designer</i>. Tant que ce type
 * vivait dans {@code fleet.domain.model}, {@code Rental} dependait du paquet
 * {@code fleet}, et la regle ArchUnit "rental ne depend pas de fleet" (ADR 0004)
 * aurait du prevoir une exception.
 *
 * <p>Un identifiant est le seul element qu'on peut partager sans couplage reel :
 * il n'a aucun comportement, et sa forme (un UUID) ne changera pas. C'est le
 * <i>shared kernel</i> du DDD, volontairement minuscule. Si un jour on y
 * ajoutait le modele ou le kilometrage du vehicule, ce serait un couplage
 * deguise : le reste du vehicule ne voyage entre contextes que par evenement.
 *
 * <h2>Reference, jamais objet</h2>
 * {@code Rental} reference un vehicule par cet identifiant, pas par l'agregat
 * {@code Vehicle} : charger 100 locations ne doit pas charger 100 vehicules, et
 * une location n'a aucun droit de modifier un vehicule (ce serait deux agregats
 * dans une meme transaction).
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
