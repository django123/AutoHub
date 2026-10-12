package com.ocarius.autohub.rental.application.port.in;

/**
 * PORT ENTRANT : faire expirer les reservations echues.
 *
 * <p>Appele par un traitement planifie, pas par un utilisateur. Invariant
 * garanti : une reservation non confirmee 30 minutes apres sa creation finit
 * par passer en {@code EXPIRED} et liberer son creneau, meme si personne ne la
 * relit.
 */
public interface ExpireOverdueReservationsUseCase {

    /** @return le nombre de reservations expirees par cet appel */
    int expireOverdueReservations();
}
