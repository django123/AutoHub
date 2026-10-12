package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.ExpireOverdueReservationsUseCase;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.exception.ConcurrentRentalModificationException;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.time.Clock;
import java.util.List;

/**
 * Cas d'usage : faire expirer les reservations echues (traitement planifie).
 *
 * <h2>Une transaction par reservation</h2>
 * Si une seule transaction couvrait tout le lot, une reservation confirmee ou
 * annulee en parallele (conflit de version) annulerait l'expiration de toutes
 * les autres. Chaque reservation est donc traitee isolement, et un conflit est
 * simplement ignore : quelqu'un d'autre a deja fait evoluer cette location.
 *
 * <h2>Idempotent</h2>
 * {@code Rental.expireIfOverdue} ne fait rien sur une location deja expiree,
 * confirmee ou annulee. Deux instances de l'application qui executeraient ce
 * traitement en meme temps ne produiraient pas deux expirations : la seconde
 * echouerait sur la version, ou ne trouverait plus rien a faire.
 */
public final class ExpireOverdueReservationsService implements ExpireOverdueReservationsUseCase {

    /** Taille maximale d'un lot. Le reste sera traite au passage suivant. */
    static final int BATCH_SIZE = 100;

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
    public ExpireOverdueReservationsService(RentalRepository rentals, EventPublisher events, UnitOfWork unitOfWork,
                                            Clock clock) {
        this.rentals = rentals;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public int expireOverdueReservations() {
        List<RentalId> candidates =
                unitOfWork.execute(() -> rentals.findOverdueReservationIds(clock.instant(), BATCH_SIZE));
        int expired = 0;
        for (RentalId id : candidates) {
            try {
                if (unitOfWork.execute(() -> expireOne(id))) {
                    expired++;
                }
            } catch (ConcurrentRentalModificationException e) {
                // Confirmee ou annulee entre-temps : rien a faire, la location a deja evolue.
            }
        }
        return expired;
    }

    private boolean expireOne(RentalId id) {
        return rentals.findById(id)
                .filter(rental -> rental.expireIfOverdue(clock))
                .map(rental -> {
                    Rentals.saveAndPublish(rentals, events, rental);
                    return true;
                })
                .orElse(false);
    }
}
