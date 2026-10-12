package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.shared.domain.vo.Money;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Remise longue duree : -10 % des 7 jours, -20 % des 30 jours.
 *
 * <h2>Le seuil porte sur la duree reelle, pas sur les jours facturables</h2>
 * Une location de 6 jours et 23 heures se facture 7 jours (arrondi
 * superieur), mais elle ne dure pas 7 jours. Si le seuil portait sur
 * {@code billableDays()}, elle obtiendrait la remise -- et un client malin
 * louerait 6 j 1 h pour payer 7 jours remises. On compare donc la
 * {@link Duration} exacte au seuil.
 *
 * <h2>Arrondi de la remise</h2>
 * La remise est arrondie au centime (HALF_UP, via {@link Money}) puis
 * soustraite. Arrondir la remise plutot que le total garantit que la ligne
 * affichee sur la facture est exactement le montant retranche.
 */
public final class LongDurationDiscountPolicy implements PricingPolicy {

    /** Code stable de la ligne produite. */
    public static final String CODE = "LONG_DURATION_DISCOUNT";

    static final Duration WEEK = Duration.ofDays(7);
    static final Duration MONTH = Duration.ofDays(30);

    /** {@inheritDoc} Vide en dessous de 7 jours de duree reelle. */
    @Override
    public Optional<PriceLine> apply(PricingContext context, Money subtotal) {
        Duration duration = context.period().duration();
        int percent;
        if (duration.compareTo(MONTH) >= 0) {
            percent = 20;
        } else if (duration.compareTo(WEEK) >= 0) {
            percent = 10;
        } else {
            return Optional.empty();
        }
        Money discount = subtotal.times(BigDecimal.valueOf(percent).movePointLeft(2));
        return Optional.of(new PriceLine(CODE, "Remise longue duree -" + percent + " %",
                Money.zero(subtotal.currency()).minus(discount)));
    }
}
