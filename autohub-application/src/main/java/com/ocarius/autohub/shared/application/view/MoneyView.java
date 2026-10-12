package com.ocarius.autohub.shared.application.view;

import com.ocarius.autohub.shared.domain.vo.Money;

import java.math.BigDecimal;

/**
 * Representation d'un montant pour les appelants de la couche application
 * (controleurs, jobs).
 *
 * <p>Les vues exposees par les ports entrants ne contiennent que des types
 * standard : un controleur n'a jamais a importer une classe du domaine (regle
 * ArchUnit). {@code Money} reste ainsi libre d'evoluer sans casser l'API.
 *
 * @param amount   montant, a l'echelle de la devise
 * @param currency code ISO 4217
 */
public record MoneyView(BigDecimal amount, String currency) {

    /** Convertit un montant du domaine ; {@code null} reste {@code null}. */
    public static MoneyView of(Money money) {
        return money == null ? null : new MoneyView(money.amount(), money.currency().getCurrencyCode());
    }
}
