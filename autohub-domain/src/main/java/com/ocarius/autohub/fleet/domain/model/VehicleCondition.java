package com.ocarius.autohub.fleet.domain.model;

/**
 * Etat physique d'un vehicule, independant de son statut dans le parc.
 *
 * <p>Statut et etat sont deux axes distincts : un vehicule {@code IN_FLEET}
 * peut etre {@code NEW} ou {@code USED} ; un vehicule {@code DAMAGED} est
 * envoye en maintenance par {@link Vehicle#reportDamage}, et redevient
 * {@code USED} a la sortie.
 */
public enum VehicleCondition {
    /** Jamais loue ni immatricule au nom d'un client. */
    NEW,
    /** En etat de marche, deja utilise. */
    USED,
    /** Endommage : ne doit pas partir en location. */
    DAMAGED
}
