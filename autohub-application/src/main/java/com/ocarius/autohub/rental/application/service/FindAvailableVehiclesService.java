package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.AvailableVehicleView;
import com.ocarius.autohub.rental.application.port.in.AvailableVehicleView.PriceLineView;
import com.ocarius.autohub.rental.application.port.in.FindAvailableVehiclesUseCase;
import com.ocarius.autohub.rental.application.port.out.VehicleCatalog;
import com.ocarius.autohub.rental.application.port.out.VehicleCatalog.CatalogVehicle;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.rental.domain.pricing.PriceBreakdown;
import com.ocarius.autohub.rental.domain.pricing.PricingContext;
import com.ocarius.autohub.rental.domain.pricing.PricingService;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import com.ocarius.autohub.shared.application.view.MoneyView;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cas d'usage en lecture : vehicules libres sur une periode, avec leur devis.
 *
 * <p>Le resultat est une photographie : entre cette recherche et la
 * reservation, un autre client peut prendre le vehicule. Seule la reservation,
 * arbitree par la contrainte d'exclusion, fait foi.
 */
public final class FindAvailableVehiclesService implements FindAvailableVehiclesUseCase {

    private final VehicleCatalog catalog;
    private final PricingService pricing;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    /**
     * @param catalog    vue des vehicules louables
     * @param pricing    tarification
     * @param unitOfWork frontiere transactionnelle
     * @param clock      horloge de l'agence
     */
    public FindAvailableVehiclesService(VehicleCatalog catalog, PricingService pricing, UnitOfWork unitOfWork,
                                        Clock clock) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public List<AvailableVehicleView> findAvailable(LocalDateTime start, LocalDateTime end) {
        RentalPeriod period = RentalPeriod.of(start, end);
        List<CatalogVehicle> vehicles = unitOfWork.execute(() -> catalog.findAvailable(period, clock.instant()));
        return vehicles.stream().map(vehicle -> toView(vehicle, period)).toList();
    }

    private AvailableVehicleView toView(CatalogVehicle vehicle, RentalPeriod period) {
        PriceBreakdown price = pricing.quote(new PricingContext(vehicle.dailyRate(), period));
        List<PriceLineView> lines = price.lines().stream()
                .map(line -> new PriceLineView(line.code(), line.label(), MoneyView.of(line.amount())))
                .toList();
        return new AvailableVehicleView(vehicle.id().value(), vehicle.description(),
                MoneyView.of(vehicle.dailyRate()), MoneyView.of(price.total()), lines);
    }
}
