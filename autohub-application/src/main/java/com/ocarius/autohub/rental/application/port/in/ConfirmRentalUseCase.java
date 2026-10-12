package com.ocarius.autohub.rental.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : confirmer une reservation.
 *
 * <p>Invariant garanti : seule une reservation non echue peut etre confirmee ;
 * une fois confirmee, elle n'expire plus.
 */
public interface ConfirmRentalUseCase {

    /** @param rentalId la location a confirmer */
    void confirm(UUID rentalId);
}
