package com.ocarius.autohub.fleet.domain.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Cycle de vie d'un vehicule dans le parc.
 *
 * <pre>
 * ACQUIRED ──► IN_PREPARATION ──► IN_FLEET ──┬──► IN_MAINTENANCE ──┐
 *                                            │◄────────────────────┘
 *                                            ├──► SOLD     (terminal)
 *                                            └──► RETIRED  (terminal, aussi depuis IN_MAINTENANCE)
 * </pre>
 *
 * <p>Meme construction que {@code RentalStatus} (ADR 0003) : un {@code switch}
 * exhaustif sur {@code this}, que le compilateur oblige a completer quand on
 * ajoute un etat.
 *
 * <p>Deux transitions meritent l'attention :
 * <ul>
 *   <li><b>Pas de vente depuis la maintenance</b> : on ne vend pas un vehicule
 *       dont on ne connait pas l'etat final.</li>
 *   <li><b>Retrait possible depuis la maintenance</b> : c'est le cas du
 *       vehicule juge irreparable. Ecart assume avec le schema de la roadmap,
 *       qui ne le prevoyait que depuis {@code IN_FLEET}.</li>
 * </ul>
 */
public enum VehicleStatus {

    /** Achete, pas encore prepare. */
    ACQUIRED,
    /** En preparation (nettoyage, controle, immatriculation). */
    IN_PREPARATION,
    /** Disponible dans le parc. Seul etat ou un vehicule peut etre loue ou vendu. */
    IN_FLEET,
    /** Immobilise pour entretien ou reparation. */
    IN_MAINTENANCE,
    /** Vendu. Terminal. */
    SOLD,
    /** Retire du parc (fin de vie, epave). Terminal. */
    RETIRED;

    /** Etats atteignables depuis celui-ci. */
    public Set<VehicleStatus> allowedTargets() {
        return switch (this) {
            case ACQUIRED -> EnumSet.of(IN_PREPARATION);
            case IN_PREPARATION -> EnumSet.of(IN_FLEET);
            case IN_FLEET -> EnumSet.of(IN_MAINTENANCE, SOLD, RETIRED);
            case IN_MAINTENANCE -> EnumSet.of(IN_FLEET, RETIRED);
            case SOLD, RETIRED -> EnumSet.noneOf(VehicleStatus.class);
        };
    }

    /** La transition vers {@code target} est-elle permise ? */
    public boolean canTransitionTo(VehicleStatus target) {
        Objects.requireNonNull(target);
        return allowedTargets().contains(target);
    }

    /** Un etat terminal n'a plus de transition sortante. */
    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }
}
