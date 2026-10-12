package com.ocarius.autohub.rental.domain.exception;

import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalStatus;
import com.ocarius.autohub.shared.domain.DomainException;

/**
 * Levee quand on demande a une location une transition que sa machine a etats
 * interdit : annuler apres le depart du vehicule, confirmer une location
 * annulee, etc.
 *
 * <p>Classe dediee plutot que {@code BusinessRuleViolation} : l'adaptateur REST
 * voudra la traduire en {@code 409 Conflict} (la ressource n'est pas dans le
 * bon etat), et non en {@code 422} comme une donnee invalide. Un appelant doit
 * donc pouvoir l'attraper precisement.
 */
public class IllegalRentalTransitionException extends DomainException {

    private final RentalStatus from;
    private final RentalStatus to;

    /**
     * @param rentalId la location concernee, pour le message
     * @param from     l'etat actuel
     * @param to       l'etat demande
     */
    public IllegalRentalTransitionException(RentalId rentalId, RentalStatus from, RentalStatus to) {
        super("RENTAL_TRANSITION_FORBIDDEN",
                "La location " + rentalId.value() + " ne peut pas passer de " + from + " a " + to);
        this.from = from;
        this.to = to;
    }

    /** Etat dans lequel se trouvait la location. */
    public RentalStatus from() {
        return from;
    }

    /** Etat qui a ete refuse. */
    public RentalStatus to() {
        return to;
    }
}
