package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.Optional;

/**
 * Tarif de base : tarif journalier multiplie par le nombre de jours
 * facturables. Une location de 25 heures se facture 2 jours
 * ({@code RentalPeriod.billableDays()}, arrondi superieur).
 *
 * <p>Toujours applicable, et toujours premiere dans la chaine : les autres
 * politiques s'appliquent a ce montant.
 */
public final class BaseDailyRatePolicy implements PricingPolicy {

    /** Code stable de la ligne produite. */
    public static final String CODE = "BASE_DAILY_RATE";

    /** {@inheritDoc} Le sous-total est ignore : c'est la premiere ligne. */
    @Override
    public Optional<PriceLine> apply(PricingContext context, Money subtotal) {
        long days = context.period().billableDays();
        Money rate = context.dailyRate();
        return Optional.of(new PriceLine(CODE,
                days + " jour(s) x " + rate.amount() + " " + rate.currency(),
                rate.times(days)));
    }
}
