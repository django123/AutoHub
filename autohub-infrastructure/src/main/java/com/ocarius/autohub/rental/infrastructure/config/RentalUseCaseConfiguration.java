package com.ocarius.autohub.rental.infrastructure.config;

import com.ocarius.autohub.rental.application.port.in.CancelRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.CloseRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.ConfirmRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.ExpireOverdueReservationsUseCase;
import com.ocarius.autohub.rental.application.port.in.FindAvailableVehiclesUseCase;
import com.ocarius.autohub.rental.application.port.in.GetRentalQuery;
import com.ocarius.autohub.rental.application.port.in.PickUpVehicleUseCase;
import com.ocarius.autohub.rental.application.port.in.ReserveRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.ReturnVehicleUseCase;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.application.port.out.VehicleCatalog;
import com.ocarius.autohub.rental.application.service.CancelRentalService;
import com.ocarius.autohub.rental.application.service.CloseRentalService;
import com.ocarius.autohub.rental.application.service.ConfirmRentalService;
import com.ocarius.autohub.rental.application.service.ExpireOverdueReservationsService;
import com.ocarius.autohub.rental.application.service.FindAvailableVehiclesService;
import com.ocarius.autohub.rental.application.service.GetRentalService;
import com.ocarius.autohub.rental.application.service.PickUpVehicleService;
import com.ocarius.autohub.rental.application.service.ReserveRentalService;
import com.ocarius.autohub.rental.application.service.ReturnVehicleService;
import com.ocarius.autohub.rental.domain.pricing.PricingService;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Assemble les cas d'usage du contexte {@code rental}.
 *
 * <h2>Pourquoi des {@code @Bean} et pas {@code @Service}</h2>
 * Les services d'application sont des classes Java ordinaires : la couche
 * application n'a pas Spring sur son classpath, donc pas d'annotation. C'est
 * l'infrastructure qui les instancie et les branche sur leurs adaptateurs.
 * Chaque bean est expose sous le type de son <b>port entrant</b> : un
 * controleur ne peut injecter que l'interface, jamais l'implementation.
 *
 * <h2>La tarification</h2>
 * {@link PricingService#standard()} fixe l'ordre des politiques dans le
 * domaine. Aucune {@code PricingPolicy} n'est un bean : Spring ne peut donc pas
 * en changer l'ordre.
 */
@Configuration(proxyBeanMethods = false)
public class RentalUseCaseConfiguration {

    /** La chaine de tarification standard, ordre fixe par le domaine. */
    @Bean
    public PricingService pricingService() {
        return PricingService.standard();
    }

    @Bean
    public ReserveRentalUseCase reserveRentalUseCase(RentalRepository rentals, VehicleCatalog catalog,
                                                     PricingService pricing, EventPublisher events,
                                                     UnitOfWork unitOfWork, Clock clock) {
        return new ReserveRentalService(rentals, catalog, pricing, events, unitOfWork, clock);
    }

    @Bean
    public ConfirmRentalUseCase confirmRentalUseCase(RentalRepository rentals, EventPublisher events,
                                                     UnitOfWork unitOfWork, Clock clock) {
        return new ConfirmRentalService(rentals, events, unitOfWork, clock);
    }

    @Bean
    public CancelRentalUseCase cancelRentalUseCase(RentalRepository rentals, EventPublisher events,
                                                   UnitOfWork unitOfWork, Clock clock) {
        return new CancelRentalService(rentals, events, unitOfWork, clock);
    }

    @Bean
    public PickUpVehicleUseCase pickUpVehicleUseCase(RentalRepository rentals, EventPublisher events,
                                                     UnitOfWork unitOfWork, Clock clock) {
        return new PickUpVehicleService(rentals, events, unitOfWork, clock);
    }

    @Bean
    public ReturnVehicleUseCase returnVehicleUseCase(RentalRepository rentals, EventPublisher events,
                                                     UnitOfWork unitOfWork, Clock clock) {
        return new ReturnVehicleService(rentals, events, unitOfWork, clock);
    }

    @Bean
    public CloseRentalUseCase closeRentalUseCase(RentalRepository rentals, EventPublisher events,
                                                 UnitOfWork unitOfWork) {
        return new CloseRentalService(rentals, events, unitOfWork);
    }

    @Bean
    public GetRentalQuery getRentalQuery(RentalRepository rentals, UnitOfWork unitOfWork) {
        return new GetRentalService(rentals, unitOfWork);
    }

    @Bean
    public FindAvailableVehiclesUseCase findAvailableVehiclesUseCase(VehicleCatalog catalog, PricingService pricing,
                                                                     UnitOfWork unitOfWork, Clock clock) {
        return new FindAvailableVehiclesService(catalog, pricing, unitOfWork, clock);
    }

    @Bean
    public ExpireOverdueReservationsUseCase expireOverdueReservationsUseCase(RentalRepository rentals,
                                                                             EventPublisher events,
                                                                             UnitOfWork unitOfWork, Clock clock) {
        return new ExpireOverdueReservationsService(rentals, events, unitOfWork, clock);
    }
}
