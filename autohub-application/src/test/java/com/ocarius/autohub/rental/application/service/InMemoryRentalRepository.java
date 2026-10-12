package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.rental.domain.model.RentalStatus;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake en memoire du port {@link RentalRepository}.
 *
 * <p>Il imite la contrainte d'exclusion : sauvegarder une location bloquante
 * qui chevauche une autre location bloquante du meme vehicule leve
 * {@link VehicleNotAvailableException}. Sans cela, les tests des cas d'usage
 * ne pourraient pas montrer qu'une reservation echue doit etre expiree
 * <i>avant</i> d'enregistrer la nouvelle. La vraie garantie reste celle de
 * PostgreSQL, prouvee par {@code RentalConcurrencyTest}.
 */
final class InMemoryRentalRepository implements RentalRepository {

    private final Map<RentalId, Rental> rentals = new LinkedHashMap<>();

    @Override
    public void save(Rental rental) {
        if (rental.blocksVehicle()) {
            boolean conflict = rentals.values().stream()
                    .filter(other -> !other.id().equals(rental.id()))
                    .anyMatch(other -> other.blocksVehicle()
                            && other.vehicleId().equals(rental.vehicleId())
                            && other.period().overlaps(rental.period()));
            if (conflict) {
                throw new VehicleNotAvailableException(rental.vehicleId(), rental.period(), null);
            }
        }
        rentals.put(rental.id(), rental);
    }

    @Override
    public Optional<Rental> findById(RentalId id) {
        return Optional.ofNullable(rentals.get(id));
    }

    @Override
    public List<Rental> findOverdueReservationsOverlapping(VehicleId vehicleId, RentalPeriod period, Instant now) {
        return rentals.values().stream()
                .filter(r -> r.vehicleId().equals(vehicleId) && r.period().overlaps(period))
                .filter(r -> isOverdue(r, now))
                .toList();
    }

    @Override
    public List<RentalId> findOverdueReservationIds(Instant now, int limit) {
        return rentals.values().stream().filter(r -> isOverdue(r, now)).map(Rental::id).limit(limit).toList();
    }

    private static boolean isOverdue(Rental rental, Instant now) {
        return rental.status() == RentalStatus.RESERVED
                && rental.reservedUntil().map(until -> !now.isBefore(until)).orElse(false);
    }

    /** Nombre de locations stockees. */
    int size() {
        return rentals.size();
    }
}
