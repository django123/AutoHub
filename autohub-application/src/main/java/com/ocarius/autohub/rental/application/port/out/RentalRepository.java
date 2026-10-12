package com.ocarius.autohub.rental.application.port.out;

import com.ocarius.autohub.rental.domain.exception.ConcurrentRentalModificationException;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PORT SORTANT : stockage des locations.
 *
 * <p>Defini ici, dans la couche application, et implemente par
 * l'infrastructure ({@code RentalRepositoryAdapter}). Son vocabulaire ne trahit
 * aucune technologie : ni "entity", ni "flush", ni "SQLState". Une
 * implementation en memoire, pour tester un cas d'usage, s'ecrit en vingt lignes.
 *
 * <h2>Contrat de {@link #save(Rental)}</h2>
 * L'implementation doit traduire toute violation de la non-superposition en
 * {@link VehicleNotAvailableException}, et le faire <b>pendant l'appel</b> :
 * l'appelant doit pouvoir l'attraper ici, pas la decouvrir au commit, sous une
 * forme technique, quelque part dans un intercepteur transactionnel.
 */
public interface RentalRepository {

    /**
     * Enregistre une location nouvelle ou modifiee. Doit etre appele dans une
     * transaction ouverte par le cas d'usage ({@code UnitOfWork}).
     *
     * @throws VehicleNotAvailableException          si la location bloque le vehicule sur
     *                                               une periode deja occupee
     * @throws ConcurrentRentalModificationException si la location a ete modifiee par
     *                                               une autre transaction depuis son chargement
     */
    void save(Rental rental);

    /** Recharge une location par son identifiant. */
    Optional<Rental> findById(RentalId id);

    /**
     * Reservations echues (statut {@code RESERVED}, echeance au plus tard a
     * {@code now}) qui bloquent encore le vehicule sur une periode chevauchant
     * {@code period}.
     *
     * <p>Sert a les faire expirer juste avant d'en reserver une nouvelle : sans
     * cela, la contrainte d'exclusion, qui ne connait que le statut stocke,
     * refuserait la reservation tant que le traitement planifie n'est pas passe.
     */
    List<Rental> findOverdueReservationsOverlapping(VehicleId vehicleId, RentalPeriod period, Instant now);

    /**
     * Identifiants des reservations echues, toutes vehicules confondus, pour le
     * traitement planifie d'expiration. Borne par {@code limit} : un traitement
     * en retard de plusieurs heures ne doit pas charger des milliers de
     * locations d'un coup.
     */
    List<RentalId> findOverdueReservationIds(Instant now, int limit);
}
