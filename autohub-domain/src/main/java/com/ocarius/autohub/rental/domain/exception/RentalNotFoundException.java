package com.ocarius.autohub.rental.domain.exception;

import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.domain.NotFoundException;

/** Aucune location ne porte cet identifiant. */
public class RentalNotFoundException extends NotFoundException {

    /** @param rentalId l'identifiant demande */
    public RentalNotFoundException(RentalId rentalId) {
        super("RENTAL_NOT_FOUND", "Aucune location " + rentalId.value());
    }
}
