package com.ocarius.autohub.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Fait metier revolu, digne d'interet en dehors de l'agregat qui l'a produit.
 *
 * <h2>Regles de nommage</h2>
 * Un evenement se nomme TOUJOURS au passe : {@code RentalConfirmed},
 * {@code VehicleReturned}, {@code SaleOrderSigned}. Jamais
 * {@code ConfirmRental} (ca, c'est une commande) ni {@code RentalConfirmation}
 * (ca, c'est un document).
 *
 * <p>Le passe n'est pas cosmetique : il rappelle qu'un evenement est
 * <b>immuable et non refusable</b>. On ne peut pas "annuler" un
 * {@code VehicleReturned} ; on peut seulement emettre un nouvel evenement
 * qui le compense.
 *
 * <h2>Pourquoi les evenements vivent dans le domaine</h2>
 * Un evenement de domaine est du vocabulaire metier, pas un detail de
 * messagerie. Kafka, RabbitMQ ou un simple appel de methode sont des choix
 * d'<i>infrastructure</i> qui viendront plus tard (phase 5, via la table
 * outbox). Le domaine ignore comment ses evenements voyagent.
 */
public interface DomainEvent {

    /** Identifiant unique de cette occurrence. Sert a la deduplication cote consommateur. */
    UUID eventId();

    /** Date de survenue du fait metier (pas celle de sa publication). */
    Instant occurredOn();

    /**
     * Nom stable du type d'evenement, utilise pour le routage et la
     * serialisation. Par defaut le nom simple de la classe, ce qui suffit
     * tant qu'on ne renomme pas ses classes a la legere.
     */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
