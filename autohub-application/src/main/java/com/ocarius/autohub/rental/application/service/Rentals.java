package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.exception.RentalNotFoundException;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;

import java.util.UUID;

/**
 * Gestes communs a tous les cas d'usage qui modifient une location : la
 * charger ou echouer, puis l'enregistrer et publier ses evenements.
 *
 * <p>Une classe utilitaire de paquet plutot qu'une classe mere : les cas
 * d'usage restent des classes independantes, sans hierarchie a comprendre.
 */
final class Rentals {

    private Rentals() {
    }

    /** Charge la location, ou leve {@link RentalNotFoundException}. */
    static Rental load(RentalRepository repository, UUID rentalId) {
        RentalId id = RentalId.of(rentalId);
        return repository.findById(id).orElseThrow(() -> new RentalNotFoundException(id));
    }

    /**
     * Enregistre la location puis publie ses evenements, dans cet ordre et
     * dans la transaction courante.
     *
     * <h2>Publier dans la transaction, delivrer apres le commit</h2>
     * Les evenements sont remis a l'{@link EventPublisher} avant le commit,
     * mais l'adaptateur Spring ne les delivre aux consommateurs qu'apres le
     * commit ({@code @TransactionalEventListener(AFTER_COMMIT)}). Si la
     * transaction est annulee, ils ne partent jamais. Publier reellement avant
     * le commit serait un bug, pas un detail de timing : un consommateur
     * pourrait agir sur un fait qu'un rollback efface ensuite.
     *
     * <p>{@code save} passe avant {@code publish} : si l'enregistrement echoue
     * (conflit de periode), aucun evenement n'est meme remis.
     */
    static void saveAndPublish(RentalRepository repository, EventPublisher events, Rental rental) {
        repository.save(rental);
        events.publishAll(rental.pullDomainEvents());
    }
}
