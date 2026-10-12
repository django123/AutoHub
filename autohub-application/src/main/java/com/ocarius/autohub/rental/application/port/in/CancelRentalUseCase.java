package com.ocarius.autohub.rental.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : annuler une location.
 *
 * <p>Invariant garanti : une location ne peut plus etre annulee une fois le
 * vehicule parti ; l'annulation libere immediatement le creneau.
 */
public interface CancelRentalUseCase {

    /**
     * @param rentalId la location a annuler
     * @param reason   motif, conserve dans l'evenement
     */
    void cancel(UUID rentalId, String reason);
}
