package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.List;
import java.util.Objects;

/**
 * Detail d'un prix : les lignes produites par chaque politique, dans l'ordre
 * ou elles ont ete appliquees, et leur total.
 *
 * <p>Le total est <b>calcule</b> a partir des lignes, jamais stocke a part :
 * il ne peut pas diverger de ce que la facture affiche.
 *
 * @param lines lignes de la facture, au moins une
 */
public record PriceBreakdown(List<PriceLine> lines) {

    /** Copie defensive, et refuse une facture vide. */
    public PriceBreakdown {
        Objects.requireNonNull(lines);
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Un prix compte au moins une ligne");
        }
    }

    /** Somme des lignes. */
    public Money total() {
        Money total = Money.zero(lines.get(0).amount().currency());
        for (PriceLine line : lines) {
            total = total.plus(line.amount());
        }
        return total;
    }
}
