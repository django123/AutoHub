package com.ocarius.autohub.rental.domain.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Etats du cycle de vie d'une location, et transitions autorisees entre eux.
 *
 * <pre>
 *   DRAFT ──reserve()──► RESERVED ──confirm()──► CONFIRMED ──pickUp()──► PICKED_UP
 *                           │  │                     │                       │
 *                 expire()  │  └──cancel()──┐        └──cancel()──┐   returnVehicle()
 *                           ▼               ▼                     ▼          ▼
 *                        EXPIRED        CANCELLED             CANCELLED   RETURNED ──close()──► CLOSED
 * </pre>
 *
 * <h2>Pourquoi un enum avec un {@code switch} exhaustif (ADR 0003)</h2>
 * La table des transitions est ecrite dans {@link #allowedTargets()} sous forme
 * de {@code switch} sur {@code this}. Le compilateur exige qu'il couvre toutes
 * les constantes : ajouter un etat sans decider de ses transitions ne compile
 * pas. On obtient la lisibilite d'une table (option c de l'ADR), la securite
 * du compilateur (option b), et une persistance triviale : le nom de la
 * constante va dans une colonne {@code varchar}.
 *
 * <h2>Ce que l'enum ne fait pas</h2>
 * Il ne porte <b>aucune donnee</b> propre a un etat ({@code reservedUntil},
 * kilometrage de depart...). Ces donnees vivent dans {@link Rental}, qui
 * verifie leur coherence avec l'etat. C'est la concession assumee par rapport
 * aux sealed interfaces, discutee dans l'ADR.
 */
public enum RentalStatus {

    /** Location en cours de saisie, pas encore engageante. Ne bloque pas le vehicule. */
    DRAFT,
    /** Reservation provisoire, valable jusqu'a {@code reservedUntil}. Bloque le vehicule. */
    RESERVED,
    /** Reservation confirmee par le client. Bloque le vehicule. */
    CONFIRMED,
    /** Le client est parti avec le vehicule. Bloque le vehicule. */
    PICKED_UP,
    /** Le vehicule est revenu ; reste a clore administrativement (facture, penalites). */
    RETURNED,
    /** Dossier clos. Terminal. */
    CLOSED,
    /** Annulee avant le depart du vehicule. Terminal. */
    CANCELLED,
    /** Reservation non confirmee a temps. Terminal. */
    EXPIRED;

    /**
     * Etats dans lesquels la location occupe le vehicule sur sa periode.
     *
     * <p><b>Doit rester identique</b> a la clause {@code WHERE} de la
     * contrainte d'exclusion {@code rental_no_overlap} (migration V2). Si les
     * deux divergent, le code et la base n'ont plus la meme definition d'un
     * vehicule indisponible. Un test d'integration verifie cette egalite.
     */
    private static final Set<RentalStatus> BLOCKING = EnumSet.of(RESERVED, CONFIRMED, PICKED_UP);

    /**
     * Les etats vers lesquels on peut aller depuis celui-ci.
     *
     * <p>Le {@code switch} sans {@code default} est volontaire : c'est lui qui
     * force a statuer sur chaque nouvel etat.
     */
    public Set<RentalStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(RESERVED);
            case RESERVED -> EnumSet.of(CONFIRMED, CANCELLED, EXPIRED);
            case CONFIRMED -> EnumSet.of(PICKED_UP, CANCELLED);
            case PICKED_UP -> EnumSet.of(RETURNED);
            case RETURNED -> EnumSet.of(CLOSED);
            case CLOSED, CANCELLED, EXPIRED -> EnumSet.noneOf(RentalStatus.class);
        };
    }

    /** La transition de cet etat vers {@code target} est-elle permise ? */
    public boolean canTransitionTo(RentalStatus target) {
        Objects.requireNonNull(target);
        return allowedTargets().contains(target);
    }

    /** Un etat terminal n'a plus aucune transition sortante. */
    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }

    /** Une location dans cet etat rend-elle le vehicule indisponible sur sa periode ? */
    public boolean blocksVehicle() {
        return BLOCKING.contains(this);
    }

    /** Copie des etats bloquants, exposee pour le test de coherence avec la base. */
    public static Set<RentalStatus> blockingStatuses() {
        return EnumSet.copyOf(BLOCKING);
    }
}
