package com.ocarius.autohub.rental.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT (lecture) : consulter une location.
 *
 * <p>Renvoie une vue, jamais l'agregat : l'appelant ne peut ni modifier la
 * location par ce biais, ni dependre de sa structure interne.
 */
public interface GetRentalQuery {

    /**
     * @throws com.ocarius.autohub.rental.domain.exception.RentalNotFoundException si elle n'existe pas
     */
    RentalView get(UUID rentalId);
}
