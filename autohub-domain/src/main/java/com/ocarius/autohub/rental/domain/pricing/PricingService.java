package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service de domaine : applique une chaine <b>ordonnee</b> de politiques et
 * produit le detail du prix.
 *
 * <h2>L'ordre est une donnee du domaine</h2>
 * Les politiques ne commutent pas (voir {@link PricingPolicy}). Si la chaine
 * etait un {@code List<PricingPolicy>} injecte par Spring, son ordre
 * dependrait de l'ordre de decouverte des beans -- qui peut changer d'une
 * version a l'autre, ou en renommant une classe. Ce serait un bug non
 * deterministe, le pire genre.
 *
 * <p>Ici, l'ordre est ecrit en clair dans {@link #standard()}, et teste. Deux
 * alternatives ont ete ecartees :
 * <ul>
 *   <li>{@code @Order} sur chaque bean : l'ordre est eparpille dans N fichiers,
 *       et les politiques dependraient de Spring -- interdit dans le domaine ;</li>
 *   <li>une methode {@code priority()} sur chaque politique : meme
 *       eparpillement, et deux politiques de meme priorite retombent dans
 *       l'indeterminisme.</li>
 * </ul>
 *
 * <h2>Arrondi : a chaque ligne</h2>
 * Chaque ligne est arrondie au centime, et le total est la somme des lignes.
 * C'est l'option 1 de {@code BACKLOG.md} ("une politique = une ligne de
 * facture") : le client peut verifier chaque montant affiche, et le total ne
 * contient aucun centime invisible. L'ecart avec un arrondi unique a la fin
 * existe (jusqu'a un centime par ligne), il est assume et teste.
 */
public final class PricingService {

    private final List<PricingPolicy> policies;

    /**
     * @param policies politiques, dans l'ordre exact d'application ; au moins une
     */
    public PricingService(List<PricingPolicy> policies) {
        Objects.requireNonNull(policies);
        this.policies = List.copyOf(policies);
        if (this.policies.isEmpty()) {
            throw new IllegalArgumentException("Une tarification compte au moins une politique");
        }
    }

    /**
     * La chaine standard d'AutoHub. Deux politiques seulement (week-end 7) ;
     * majoration week-end et saisonnalite sont au {@code BACKLOG.md}. La
     * penalite de retard n'est pas une politique de prix mais une regle de
     * retour, portee par {@code Rental}.
     */
    public static PricingService standard() {
        return new PricingService(List.of(
                new BaseDailyRatePolicy(),
                new LongDurationDiscountPolicy()));
    }

    /**
     * Calcule le prix d'une location.
     *
     * @throws BusinessRuleViolation si le total n'est pas strictement positif :
     *                               une chaine de remises mal reglee ne doit pas
     *                               produire une location gratuite ou payee au client
     */
    public PriceBreakdown quote(PricingContext context) {
        Objects.requireNonNull(context);
        List<PriceLine> lines = new ArrayList<>();
        Money subtotal = Money.zero(context.dailyRate().currency());
        for (PricingPolicy policy : policies) {
            Optional<PriceLine> line = policy.apply(context, subtotal);
            if (line.isPresent()) {
                lines.add(line.get());
                subtotal = subtotal.plus(line.get().amount());
            }
        }
        BusinessRuleViolation.check(
                !lines.isEmpty() && subtotal.isPositive(),
                "PRICE_NOT_POSITIVE",
                "Le prix calcule doit etre strictement positif : " + subtotal);
        return new PriceBreakdown(lines);
    }
}
