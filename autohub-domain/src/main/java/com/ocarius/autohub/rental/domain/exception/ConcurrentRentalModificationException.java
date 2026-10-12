package com.ocarius.autohub.rental.domain.exception;

import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.domain.DomainException;

/**
 * La location a ete modifiee par quelqu'un d'autre entre le moment ou on l'a
 * chargee et le moment ou on a voulu enregistrer ses propres changements.
 *
 * <h2>Pourquoi une exception du domaine pour un mecanisme technique</h2>
 * Le mecanisme qui la detecte (le champ {@code version} et le verrouillage
 * optimiste) est technique, et le domaine l'ignore. Mais la <b>situation</b> a
 * un sens metier : "un agent a annule la location pendant que tu la
 * confirmais". La couche application doit pouvoir l'attraper sans connaitre
 * Spring ni JPA, et l'API devra la presenter comme un conflit (409) qu'on
 * resout en rechargeant, pas comme une panne (500).
 *
 * <p>Comme {@link VehicleNotAvailableException}, elle est declaree ici mais
 * levee par l'adaptateur de persistance, qui traduit
 * {@code OptimisticLockingFailureException}.
 *
 * <h2>Pourquoi ne pas rejouer automatiquement</h2>
 * Rejouer l'operation sur la version fraiche serait faux en general : si
 * l'autre modification etait une annulation, "confirmer quand meme" ecraserait
 * une decision metier. C'est a l'appelant (ou a l'utilisateur) de decider en
 * voyant le nouvel etat.
 */
public class ConcurrentRentalModificationException extends DomainException {

    /**
     * @param rentalId la location modifiee concurremment
     * @param cause    l'exception technique d'origine, conservee pour le diagnostic
     */
    public ConcurrentRentalModificationException(RentalId rentalId, Throwable cause) {
        super("RENTAL_CONCURRENT_MODIFICATION",
                "La location " + rentalId.value() + " a ete modifiee entre-temps ; rechargez-la avant de reessayer");
        initCause(cause);
    }
}
