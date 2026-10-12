package com.ocarius.autohub.shared.domain;

/**
 * Categorie de refus ou la demande designe une ressource qui n'existe pas.
 *
 * <p>Troisieme famille, a cote de {@link BusinessRuleViolation} (demande
 * invalide) et de {@link ConflictException} (demande valide mais contredite
 * par l'etat actuel). La distinguer permet a l'adaptateur REST de repondre
 * {@code 404}, sans enumerer chaque exception "introuvable" du projet.
 *
 * <p>Comme {@code ConflictException}, c'est une categorie metier, pas un code
 * HTTP : "cette location n'existe pas" a un sens pour n'importe quel appelant.
 */
public abstract class NotFoundException extends DomainException {

    /**
     * @param code    identifiant stable, destine aux machines
     * @param message explication destinee a l'humain
     */
    protected NotFoundException(String code, String message) {
        super(code, message);
    }
}
