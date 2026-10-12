package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.GetRentalQuery;
import com.ocarius.autohub.rental.application.port.in.RentalView;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.util.UUID;

/**
 * Cas d'usage en lecture : consulter une location.
 *
 * <p>La vue est construite <b>dans</b> la transaction : avec
 * {@code open-in-view: false}, aucun chargement paresseux n'est possible apres
 * sa fin. L'agregat n'a pas de collection paresseuse aujourd'hui, mais la
 * regle evite une {@code LazyInitializationException} le jour ou il en aura.
 */
public final class GetRentalService implements GetRentalQuery {

    private final RentalRepository rentals;
    private final UnitOfWork unitOfWork;

    /**
     * @param rentals    stockage des locations
     * @param unitOfWork frontiere transactionnelle
     */
    public GetRentalService(RentalRepository rentals, UnitOfWork unitOfWork) {
        this.rentals = rentals;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public RentalView get(UUID rentalId) {
        return unitOfWork.execute(() -> RentalView.from(Rentals.load(rentals, rentalId)));
    }
}
