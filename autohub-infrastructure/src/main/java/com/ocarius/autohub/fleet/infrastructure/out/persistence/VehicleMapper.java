package com.ocarius.autohub.fleet.infrastructure.out.persistence;

import com.ocarius.autohub.fleet.domain.model.Mileage;
import com.ocarius.autohub.fleet.domain.model.Offering;
import com.ocarius.autohub.fleet.domain.model.PlateNumber;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.fleet.domain.model.VehicleCondition;
import com.ocarius.autohub.fleet.domain.model.VehicleModel;
import com.ocarius.autohub.fleet.domain.model.VehicleStatus;
import com.ocarius.autohub.fleet.domain.model.Vin;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.Currency;
import java.util.EnumSet;
import java.util.Set;

/**
 * Traduction explicite entre {@link Vehicle} et {@link VehicleJpaEntity}.
 *
 * <p>La relecture repasse par les constructeurs des value objects (VIN,
 * immatriculation, modele, kilometrage), qui revalident leur format, puis par
 * {@code Vehicle.restore}.
 */
final class VehicleMapper {

    private VehicleMapper() {
    }

    /** Reconstitue l'agregat depuis une ligne relue. */
    static Vehicle toDomain(VehicleJpaEntity entity) {
        Set<Offering> offerings = EnumSet.noneOf(Offering.class);
        if (entity.getForRent()) {
            offerings.add(Offering.FOR_RENT);
        }
        if (entity.getForSale()) {
            offerings.add(Offering.FOR_SALE);
        }
        return Vehicle.restore(
                VehicleId.of(entity.getId()),
                Vin.of(entity.getVin()),
                PlateNumber.of(entity.getPlateNumber()),
                new VehicleModel(entity.getBrand(), entity.getModel(), entity.getModelYear()),
                Money.of(entity.getDailyRateAmount(), Currency.getInstance(entity.getDailyRateCurrency())),
                Mileage.of(entity.getMileageKm()),
                VehicleCondition.valueOf(entity.getCondition()),
                VehicleStatus.valueOf(entity.getStatus()),
                offerings);
    }

    /** Recopie l'etat de l'agregat dans une entite nouvelle ou geree (preserve la version). */
    static void copyToEntity(Vehicle vehicle, VehicleJpaEntity entity) {
        entity.setVin(vehicle.vin().value());
        entity.setPlateNumber(vehicle.plate().value());
        entity.setBrand(vehicle.model().brand());
        entity.setModel(vehicle.model().name());
        entity.setModelYear((short) vehicle.model().year());
        entity.setMileageKm(vehicle.mileage().kilometers());
        entity.setStatus(vehicle.status().name());
        entity.setCondition(vehicle.condition().name());
        entity.setForRent(vehicle.isOfferedFor(Offering.FOR_RENT));
        entity.setForSale(vehicle.isOfferedFor(Offering.FOR_SALE));
        entity.setDailyRateAmount(vehicle.dailyRentalRate().amount());
        entity.setDailyRateCurrency(vehicle.dailyRentalRate().currency().getCurrencyCode());
    }
}
