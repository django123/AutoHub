package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.ConfirmRentalUseCase;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.time.Clock;
import java.util.UUID;

/**
 * Cas d'usage : confirmer une reservation avant son echeance.
 *
 * <p>Charger, demander a l'agregat, enregistrer, publier : le cas d'usage
 * n'orchestre que. La regle elle-meme est dans {@code Rental}.
 */
public final class ConfirmRentalService implements ConfirmRentalUseCase {

    private final RentalRepository rentals;
    private final EventPublisher events;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    /**
     * @param rentals    stockage des locations
     * @param events     publication des evenements de domaine
     * @param unitOfWork frontiere transactionnelle
     * @param clock      horloge de l'agence
     */
    public ConfirmRentalService(RentalRepository rentals, EventPublisher events, UnitOfWork unitOfWork, Clock clock) {
        this.rentals = rentals;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public void confirm(UUID rentalId) {
        unitOfWork.run(() -> {
            Rental rental = Rentals.load(rentals, rentalId);
            rental.confirm(clock);
            Rentals.saveAndPublish(rentals, events, rental);
        });
    }
}
