package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.Objects;

/**
 * Devis fige a la creation d'une location : le tarif journalier applique et le
 * prix total accepte par le client.
 *
 * <h2>Pourquoi figer aussi le tarif journalier</h2>
 * Le total suffit a facturer la periode prevue. Mais la penalite de retard se
 * calcule en jours de tarif : si on relisait le tarif <i>actuel</i> du
 * vehicule au moment du retour, une hausse de grille entre-temps changerait la
 * penalite d'un contrat deja signe. Le tarif fait partie du contrat.
 *
 * @param dailyRate tarif journalier en vigueur a la reservation
 * @param total     prix total de la periode prevue, remises comprises
 */
public record RentalQuote(Money dailyRate, Money total) {

    /** Les deux montants sont positifs et dans la meme devise. */
    public RentalQuote {
        Objects.requireNonNull(dailyRate, "Le tarif journalier est obligatoire");
        Objects.requireNonNull(total, "Le total est obligatoire");
        BusinessRuleViolation.check(
                dailyRate.isPositive() && total.isPositive(),
                "RENTAL_PRICE_NOT_POSITIVE",
                "Le tarif journalier et le prix doivent etre strictement positifs : " + dailyRate + ", " + total);
        BusinessRuleViolation.check(
                dailyRate.currency().equals(total.currency()),
                "RENTAL_QUOTE_CURRENCY_MISMATCH",
                "Le tarif et le total doivent etre dans la meme devise : " + dailyRate + ", " + total);
    }

    /** Fabrique lisible. */
    public static RentalQuote of(Money dailyRate, Money total) {
        return new RentalQuote(dailyRate, total);
    }
}
