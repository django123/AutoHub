package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.Objects;

/**
 * Une ligne de la facture : un montant positif (tarif, majoration) ou negatif
 * (remise), avec son libelle.
 *
 * <p>Chaque ligne est un {@link Money}, donc arrondie au centime. C'est un
 * choix (voir {@link PricingService}) : le client peut verifier chaque ligne,
 * et la somme des lignes affichees est exactement le total facture.
 *
 * @param code   identifiant stable de la politique qui a produit la ligne
 * @param label  libelle lisible, destine a la facture
 * @param amount montant de la ligne, eventuellement negatif
 */
public record PriceLine(String code, String label, Money amount) {

    /** Refuse les champs absents. */
    public PriceLine {
        Objects.requireNonNull(code);
        Objects.requireNonNull(label);
        Objects.requireNonNull(amount);
    }
}
