package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.PickUpVehicleUseCase;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import com.ocarius.autohub.rental.application.port.in.HandoverCommand;

import java.time.Clock;
import java.util.UUID;

/**
 * Cas d'usage : enregistrer le depart du vehicule.
 *
 * <p>Charger, demander a l'agregat, enregistrer, publier : le cas d'usage
 * n'orchestre que. La regle elle-meme est dans {@code Rental}.
 */
public final class PickUpVehicleService implements PickUpVehicleUseCase {

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
    public PickUpVehicleService(RentalRepository rentals, EventPublisher events, UnitOfWork unitOfWork, Clock clock) {
        this.rentals = rentals;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public void pickUp(UUID rentalId, HandoverCommand handover) {
        unitOfWork.run(() -> {
            Rental rental = Rentals.load(rentals, rentalId);
            rental.pickUp(Handovers.toDomain(handover), clock);
            Rentals.saveAndPublish(rentals, events, rental);
        });
    }
}
