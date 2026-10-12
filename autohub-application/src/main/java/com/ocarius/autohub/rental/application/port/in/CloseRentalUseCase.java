package com.ocarius.autohub.rental.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : clore administrativement une location rendue.
 *
 * <p>Invariant garanti : seule une location dont le vehicule est revenu peut
 * etre close.
 */
public interface CloseRentalUseCase {

    /** @param rentalId la location a clore */
    void close(UUID rentalId);
}
