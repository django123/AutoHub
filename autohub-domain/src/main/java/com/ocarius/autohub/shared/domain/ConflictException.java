package com.ocarius.autohub.shared.domain;

/**
 * Categorie de violations metier ou la demande est valide en elle-meme, mais
 * <b>contredit l'etat actuel</b> du systeme.
 *
 * <h2>Deux familles de refus</h2>
 * <ul>
 *   <li><b>La demande est invalide</b>, quel que soit l'etat du systeme : une
 *       location d'une demi-heure, un prix negatif. La renvoyer telle quelle
 *       echouera toujours. C'est un {@link BusinessRuleViolation}.</li>
 *   <li><b>La demande est valide, mais pas maintenant</b> : le vehicule est deja
 *       loue sur cette periode, la location a deja ete annulee, quelqu'un l'a
 *       modifiee entre-temps. La meme demande pourrait reussir apres avoir
 *       recharge l'etat, ou sur une autre ressource. C'est une
 *       {@code ConflictException}.</li>
 * </ul>
 * Un appelant ne reagit pas de la meme facon aux deux : on corrige sa saisie
 * dans le premier cas, on recharge et on reconsidere dans le second.
 *
 * <h2>Pourquoi cette distinction vit dans le domaine</h2>
 * Le domaine ne connait pas HTTP, et ce n'est pas un "409" deguise : c'est une
 * categorie metier, que chaque adaptateur traduit a sa maniere. L'adaptateur
 * REST en fait un {@code 409 Conflict}, un consumer de messages pourrait
 * decider de rejouer. Sans cette classe de base, l'adaptateur REST devrait
 * enumerer chaque exception concernee, et une nouvelle exception de conflit
 * oubliee dans la liste partirait silencieusement en 422.
 */
public abstract class ConflictException extends DomainException {

    /**
     * @param code    identifiant stable du conflit, destine aux machines
     * @param message explication destinee a l'humain
     */
    protected ConflictException(String code, String message) {
        super(code, message);
    }
}
