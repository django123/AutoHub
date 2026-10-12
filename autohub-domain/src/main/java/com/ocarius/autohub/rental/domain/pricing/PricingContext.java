package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.Objects;

/**
 * Les donnees d'entree d'un calcul de prix : le tarif journalier du vehicule et
 * la periode demandee.
 *
 * <p>Un objet plutot que deux parametres : ajouter une donnee (categorie du
 * vehicule pour une future politique saisonniere) ne changera pas la
 * signature de chaque politique.
 *
 * @param dailyRate tarif journalier du vehicule
 * @param period    periode de location demandee
 */
public record PricingContext(Money dailyRate, RentalPeriod period) {

    /** Refuse les donnees absentes. */
    public PricingContext {
        Objects.requireNonNull(dailyRate, "Le tarif journalier est obligatoire");
        Objects.requireNonNull(period, "La periode est obligatoire");
    }
}
