package com.ocarius.autohub.rental.application.port.in;

import com.ocarius.autohub.shared.application.view.MoneyView;

import java.util.List;
import java.util.UUID;

/**
 * Un vehicule disponible, avec le devis de la periode demandee.
 *
 * @param vehicleId   identifiant
 * @param description modele et immatriculation
 * @param dailyRate   tarif journalier
 * @param total       prix total de la periode, remises comprises
 * @param priceLines  detail du prix, ligne par ligne
 */
public record AvailableVehicleView(UUID vehicleId, String description, MoneyView dailyRate, MoneyView total,
                                   List<PriceLineView> priceLines) {

    /**
     * Une ligne du devis.
     *
     * @param code   politique de prix qui l'a produite
     * @param label  libelle
     * @param amount montant, negatif pour une remise
     */
    public record PriceLineView(String code, String label, MoneyView amount) {
    }
}
