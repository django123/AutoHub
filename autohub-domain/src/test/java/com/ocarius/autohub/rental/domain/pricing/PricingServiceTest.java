package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la tarification : politiques isolees, composition, ordre et arrondi.
 */
class PricingServiceTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2027, 3, 8, 8, 0);
    private static final Money RATE = Money.euros("100.00");

    private static PricingContext context(Money rate, LocalDateTime start, LocalDateTime end) {
        return new PricingContext(rate, RentalPeriod.of(start, end));
    }

    private static PricingContext days(int days) {
        return context(RATE, LUNDI_8H, LUNDI_8H.plusDays(days));
    }

    /** Majoration fixe, utilisee uniquement pour montrer la non-commutativite. */
    private static final PricingPolicy SURCHARGE_15_EUR = (ctx, subtotal) ->
            Optional.of(new PriceLine("TEST_SURCHARGE", "Majoration 15 EUR", Money.euros("15.00")));

    @Nested
    @DisplayName("Tarif de base")
    class TarifDeBase {

        @Test
        @DisplayName("deux jours pleins coutent deux fois le tarif journalier")
        void jours_pleins() {
            assertThat(PricingService.standard().quote(days(2)).total()).isEqualTo(Money.euros("200.00"));
        }

        @Test
        @DisplayName("* 25 heures se facturent deux jours")
        void jour_entame() {
            PriceBreakdown price = PricingService.standard().quote(context(RATE, LUNDI_8H, LUNDI_8H.plusHours(25)));
            assertThat(price.total()).isEqualTo(Money.euros("200.00"));
        }
    }

    @Nested
    @DisplayName("Remise longue duree")
    class RemiseLongueDuree {

        @Test
        @DisplayName("6 jours : pas de remise")
        void six_jours() {
            assertThat(PricingService.standard().quote(days(6)).lines()).hasSize(1);
        }

        @Test
        @DisplayName("exactement 7 jours : -10 %")
        void sept_jours_pile() {
            PriceBreakdown price = PricingService.standard().quote(days(7));
            assertThat(price.lines()).extracting(PriceLine::code)
                    .containsExactly(BaseDailyRatePolicy.CODE, LongDurationDiscountPolicy.CODE);
            assertThat(price.total()).isEqualTo(Money.euros("630.00"));
        }

        @Test
        @DisplayName("* 6 jours et 23 heures : facture 7 jours, mais sans remise (seuil sur la duree reelle)")
        void juste_sous_le_seuil() {
            PriceBreakdown price = PricingService.standard()
                    .quote(context(RATE, LUNDI_8H, LUNDI_8H.plusDays(6).plusHours(23)));
            assertThat(price.lines()).hasSize(1);
            assertThat(price.total()).isEqualTo(Money.euros("700.00"));
        }

        @Test
        @DisplayName("exactement 30 jours : -20 %, et non -10 % puis -20 %")
        void trente_jours() {
            PriceBreakdown price = PricingService.standard().quote(days(30));
            assertThat(price.lines()).hasSize(2);
            assertThat(price.total()).isEqualTo(Money.euros("2400.00"));
        }
    }

    @Nested
    @DisplayName("Composition et ordre")
    class CompositionEtOrdre {

        @Test
        @DisplayName("* l'ordre d'application change le resultat : la composition n'est pas commutative")
        void non_commutativite() {
            PricingService remisePuisMajoration = new PricingService(List.of(
                    new BaseDailyRatePolicy(), new LongDurationDiscountPolicy(), SURCHARGE_15_EUR));
            PricingService majorationPuisRemise = new PricingService(List.of(
                    new BaseDailyRatePolicy(), SURCHARGE_15_EUR, new LongDurationDiscountPolicy()));

            // 3 000 - 20 % + 15 = 2 415 ; (3 000 + 15) - 20 % = 2 412
            assertThat(remisePuisMajoration.quote(days(30)).total()).isEqualTo(Money.euros("2415.00"));
            assertThat(majorationPuisRemise.quote(days(30)).total()).isEqualTo(Money.euros("2412.00"));
        }

        @Test
        @DisplayName("* la chaine standard applique le tarif de base puis la remise, dans cet ordre")
        void ordre_de_la_chaine_standard() {
            // Ce test fige l'ordre : s'il change, c'est une decision metier,
            // pas un effet de bord d'un conteneur d'injection.
            assertThat(PricingService.standard().quote(days(8)).lines())
                    .extracting(PriceLine::code)
                    .containsExactly(BaseDailyRatePolicy.CODE, LongDurationDiscountPolicy.CODE);
        }

        @Test
        @DisplayName("le meme calcul repete donne toujours le meme resultat")
        void determinisme() {
            PricingService service = PricingService.standard();
            assertThat(service.quote(days(9))).isEqualTo(service.quote(days(9)));
        }

        @Test
        @DisplayName("le total est exactement la somme des lignes affichees")
        void total_egal_somme_des_lignes() {
            PriceBreakdown price = PricingService.standard().quote(days(10));
            Money sum = price.lines().stream().map(PriceLine::amount).reduce(Money.zeroEuros(), Money::plus);
            assertThat(price.total()).isEqualTo(sum);
        }
    }

    @Nested
    @DisplayName("Arrondi")
    class Arrondi {

        @Test
        @DisplayName("* la remise est arrondie au centime, et la ligne affichee est exactement le montant retire")
        void arrondi_de_la_remise() {
            // 7 x 33,33 = 233,31 ; 10 % = 23,331 -> remise affichee 23,33 ; total 209,98
            PriceBreakdown price = PricingService.standard()
                    .quote(context(Money.euros("33.33"), LUNDI_8H, LUNDI_8H.plusDays(7)));
            assertThat(price.lines().get(1).amount()).isEqualTo(Money.euros("-23.33"));
            assertThat(price.total()).isEqualTo(Money.euros("209.98"));
        }

        @Test
        @DisplayName("* arrondir a chaque ligne peut differer d'un centime d'un arrondi unique a la fin")
        void arrondi_par_ligne_contre_arrondi_final() {
            // 7 x 10,05 = 70,35 ; remise 10 % = 7,035 -> 7,04 (HALF_UP) ; total 63,31.
            // Un calcul exact puis un arrondi final donnerait 63,315 -> 63,32.
            // L'ecart d'un centime est le prix d'une facture dont chaque ligne
            // est verifiable (choix documente dans PricingService).
            PriceBreakdown price = PricingService.standard()
                    .quote(context(Money.euros("10.05"), LUNDI_8H, LUNDI_8H.plusDays(7)));
            assertThat(price.total()).isEqualTo(Money.euros("63.31"));
        }
    }

    @Nested
    @DisplayName("Garde-fous")
    class GardeFous {

        @Test
        @DisplayName("* une chaine qui produirait un total negatif ou nul est refusee")
        void total_non_positif() {
            PricingPolicy remiseExcessive = (ctx, subtotal) ->
                    Optional.of(new PriceLine("TEST", "Remise excessive", Money.zeroEuros().minus(subtotal)));
            PricingService service = new PricingService(List.of(new BaseDailyRatePolicy(), remiseExcessive));

            assertThatThrownBy(() -> service.quote(days(3)))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .satisfies(e -> assertThat(((BusinessRuleViolation) e).code()).isEqualTo("PRICE_NOT_POSITIVE"));
        }

        @Test
        @DisplayName("une chaine sans politique est refusee a la construction")
        void chaine_vide() {
            assertThatThrownBy(() -> new PricingService(List.of())).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
