package com.ocarius.autohub.rental.application.port.out;

import com.ocarius.autohub.rental.domain.exception.ConcurrentRentalModificationException;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;

import java.util.Optional;

/**
 * PORT SORTANT : stockage des locations.
 *
 * <p>Defini ici, dans la couche application, et implemente par
 * l'infrastructure ({@code RentalRepositoryAdapter}). Son vocabulaire ne trahit
 * aucune technologie : ni "entity", ni "flush", ni "SQLState". Une
 * implementation en memoire, pour tester un cas d'usage, s'ecrit en dix lignes.
 *
 * <h2>Contrat de {@link #save(Rental)}</h2>
 * L'implementation doit traduire toute violation de la non-superposition en
 * {@link VehicleNotAvailableException}, et le faire <b>pendant l'appel</b> :
 * l'appelant doit pouvoir l'attraper ici, pas la decouvrir au commit, sous une
 * forme technique, quelque part dans un intercepteur transactionnel.
 */
public interface RentalRepository {

    /**
     * Enregistre une location nouvelle ou modifiee.
     *
     * @throws VehicleNotAvailableException          si la location bloque le vehicule sur
     *                                               une periode deja occupee
     * @throws ConcurrentRentalModificationException si la location a ete modifiee par
     *                                               une autre transaction depuis son chargement
     */
    void save(Rental rental);

    /** Recharge une location par son identifiant. */
    Optional<Rental> findById(RentalId id);
}
