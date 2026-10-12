package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.out.VehicleCatalog;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake du catalogue de vehicules. La disponibilite n'est pas calculee : le test
 * declare lui-meme quels vehicules la recherche renvoie, la requete reelle
 * etant testee contre PostgreSQL.
 */
final class FakeVehicleCatalog implements VehicleCatalog {

    private final Map<VehicleId, CatalogVehicle> vehicles = new LinkedHashMap<>();

    /** Ajoute un vehicule louable, au tarif donne. */
    VehicleId addRentable(String dailyRate) {
        return add(dailyRate, true);
    }

    /** Ajoute un vehicule non louable (en maintenance, vendu...). */
    VehicleId addNotRentable(String dailyRate) {
        return add(dailyRate, false);
    }

    private VehicleId add(String dailyRate, boolean rentable) {
        VehicleId id = VehicleId.newId();
        vehicles.put(id, new CatalogVehicle(id, "Peugeot 208 (2024) AB-123-CD", Money.euros(dailyRate), rentable));
        return id;
    }

    @Override
    public Optional<CatalogVehicle> findById(VehicleId vehicleId) {
        return Optional.ofNullable(vehicles.get(vehicleId));
    }

    @Override
    public List<CatalogVehicle> findAvailable(RentalPeriod period, Instant now) {
        return vehicles.values().stream().filter(CatalogVehicle::rentable).toList();
    }
}
