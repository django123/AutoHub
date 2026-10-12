package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.ReturnVehicleUseCase;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import com.ocarius.autohub.rental.application.port.in.HandoverCommand;

import java.time.Clock;
import java.util.UUID;

/**
 * Cas d'usage : enregistrer le retour du vehicule.
 *
 * <p>La mise a jour du kilometrage du vehicule n'est <b>pas</b> faite ici :
 * appeler le contexte {@code fleet} depuis {@code rental} creerait le couplage
 * qu'ADR 0004 ecarte. L'evenement {@code VehicleReturned}, publie par
 * l'agregat, est consomme par {@code fleet} apres le commit.
 *
 * <p>Charger, demander a l'agregat, enregistrer, publier : le cas d'usage
 * n'orchestre que. La regle elle-meme est dans {@code Rental}.
 */
public final class ReturnVehicleService implements ReturnVehicleUseCase {

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
    public ReturnVehicleService(RentalRepository rentals, EventPublisher events, UnitOfWork unitOfWork, Clock clock) {
        this.rentals = rentals;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public void returnVehicle(UUID rentalId, HandoverCommand handover) {
        unitOfWork.run(() -> {
            Rental rental = Rentals.load(rentals, rentalId);
            rental.returnVehicle(Handovers.toDomain(handover), clock);
            Rentals.saveAndPublish(rentals, events, rental);
        });
    }
}
