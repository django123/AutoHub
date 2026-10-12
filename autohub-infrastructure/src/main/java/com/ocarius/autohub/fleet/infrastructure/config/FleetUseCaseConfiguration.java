package com.ocarius.autohub.fleet.infrastructure.config;

import com.ocarius.autohub.fleet.application.port.in.AddVehicleToFleetUseCase;
import com.ocarius.autohub.fleet.application.port.in.CommissionVehicleUseCase;
import com.ocarius.autohub.fleet.application.port.in.GetVehicleQuery;
import com.ocarius.autohub.fleet.application.port.in.PrepareVehicleUseCase;
import com.ocarius.autohub.fleet.application.port.in.RecordReturnMileageUseCase;
import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.fleet.application.service.AddVehicleToFleetService;
import com.ocarius.autohub.fleet.application.service.CommissionVehicleService;
import com.ocarius.autohub.fleet.application.service.GetVehicleService;
import com.ocarius.autohub.fleet.application.service.PrepareVehicleService;
import com.ocarius.autohub.fleet.application.service.RecordReturnMileageService;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Assemble les cas d'usage du contexte {@code fleet}, exposes sous le type de
 * leur port entrant (voir {@code RentalUseCaseConfiguration}).
 */
@Configuration(proxyBeanMethods = false)
public class FleetUseCaseConfiguration {

    @Bean
    public AddVehicleToFleetUseCase addVehicleToFleetUseCase(VehicleRepository vehicles, EventPublisher events,
                                                             UnitOfWork unitOfWork, Clock clock) {
        return new AddVehicleToFleetService(vehicles, events, unitOfWork, clock);
    }

    @Bean
    public PrepareVehicleUseCase prepareVehicleUseCase(VehicleRepository vehicles, EventPublisher events,
                                                       UnitOfWork unitOfWork) {
        return new PrepareVehicleService(vehicles, events, unitOfWork);
    }

    @Bean
    public CommissionVehicleUseCase commissionVehicleUseCase(VehicleRepository vehicles, EventPublisher events,
                                                             UnitOfWork unitOfWork) {
        return new CommissionVehicleService(vehicles, events, unitOfWork);
    }

    @Bean
    public GetVehicleQuery getVehicleQuery(VehicleRepository vehicles, UnitOfWork unitOfWork) {
        return new GetVehicleService(vehicles, unitOfWork);
    }

    @Bean
    public RecordReturnMileageUseCase recordReturnMileageUseCase(VehicleRepository vehicles, EventPublisher events,
                                                                 UnitOfWork unitOfWork, Clock clock) {
        return new RecordReturnMileageService(vehicles, events, unitOfWork, clock);
    }
}
