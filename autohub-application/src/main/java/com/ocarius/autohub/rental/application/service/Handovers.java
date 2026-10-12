package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.HandoverCommand;
import com.ocarius.autohub.rental.domain.model.VehicleHandover;

/** Conversion de la commande d'etat des lieux en value object du domaine (qui la valide). */
final class Handovers {

    private Handovers() {
    }

    static VehicleHandover toDomain(HandoverCommand command) {
        return new VehicleHandover(command.at(), command.mileageKm(), command.fuelLevelPercent());
    }
}
