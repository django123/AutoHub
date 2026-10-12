package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.CloseRentalUseCase;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.util.UUID;

/**
 * Cas d'usage : clore une location rendue. Sans horloge : la cloture ne
 * depend pas du moment ou elle a lieu.
 */
public final class CloseRentalService implements CloseRentalUseCase {

    private final RentalRepository rentals;
    private final EventPublisher events;
    private final UnitOfWork unitOfWork;

    /**
     * @param rentals    stockage des locations
     * @param events     publication des evenements de domaine
     * @param unitOfWork frontiere transactionnelle
     */
    public CloseRentalService(RentalRepository rentals, EventPublisher events, UnitOfWork unitOfWork) {
        this.rentals = rentals;
        this.events = events;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public void close(UUID rentalId) {
        unitOfWork.run(() -> {
            Rental rental = Rentals.load(rentals, rentalId);
            rental.close();
            Rentals.saveAndPublish(rentals, events, rental);
        });
    }
}
