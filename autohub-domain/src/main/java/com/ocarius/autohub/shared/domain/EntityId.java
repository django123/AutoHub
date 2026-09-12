package com.ocarius.autohub.shared.domain;

import java.util.UUID;

/**
 * Contrat commun a tous les identifiants d'entites.
 *
 * <h2>Pourquoi ne pas utiliser {@code UUID} directement ?</h2>
 * Parce que {@code UUID} ne dit rien de ce qu'il identifie. Considere :
 *
 * <pre>{@code
 * // Sans type dedie : compile, et explose en production
 * rentalService.reserve(customerId, vehicleId);   // bon ordre
 * rentalService.reserve(vehicleId, customerId);   // inverse... compile quand meme
 *
 * // Avec types dedies : le compilateur refuse le second appel
 * void reserve(CustomerId customer, VehicleId vehicle)
 * }</pre>
 *
 * C'est l'application directe du principe <i>Primitive Obsession</i> :
 * un identifiant metier merite un type metier. Le cout est de trois lignes
 * de code par identifiant ; le benefice est une classe entiere de bugs
 * rendue impossible a la compilation.
 *
 * <p>Implementation typique, en une ligne grace aux records :
 * <pre>{@code
 * public record VehicleId(UUID value) implements EntityId {
 *     public static VehicleId newId() { return new VehicleId(UUID.randomUUID()); }
 * }
 * }</pre>
 */
public interface EntityId {

    /** La valeur technique sous-jacente. Utilisee uniquement par la persistance. */
    UUID value();
}
