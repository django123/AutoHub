package com.ocarius.autohub.rental.infrastructure.in.rest.dto;

import com.ocarius.autohub.rental.application.port.in.AvailableVehicleView;
import com.ocarius.autohub.shared.application.view.MoneyView;

import java.util.List;
import java.util.UUID;

/**
 * Reponse JSON d'un vehicule disponible, avec son devis.
 *
 * @param vehicleId   identifiant
 * @param description modele et immatriculation
 * @param dailyRate   tarif journalier
 * @param total       total de la periode
 * @param priceLines  detail du prix
 */
public record AvailableVehicleResponse(UUID vehicleId, String description, MoneyView dailyRate, MoneyView total,
                                       List<PriceLine> priceLines) {

    /** Construit la reponse depuis la vue applicative. */
    public static AvailableVehicleResponse from(AvailableVehicleView view) {
        return new AvailableVehicleResponse(view.vehicleId(), view.description(), view.dailyRate(), view.total(),
                view.priceLines().stream().map(l -> new PriceLine(l.code(), l.label(), l.amount())).toList());
    }

    /**
     * Ligne du devis.
     *
     * @param code   politique de prix
     * @param label  libelle
     * @param amount montant (negatif pour une remise)
     */
    public record PriceLine(String code, String label, MoneyView amount) {
    }
}
