package com.ocarius.autohub.shared.domain.vo;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de {@link Money}.
 *
 * <p>Aucun {@code @SpringBootTest}, aucun mock, aucune base : cette classe
 * s'execute en quelques millisecondes. C'est le dividende direct d'un domaine
 * sans dependance. Quand les tests sont instantanes, on les lance en continu ;
 * quand ils prennent trente secondes, on les lance avant de partir le soir.
 *
 * <p>Noter aussi le style de nommage : les methodes decrivent une REGLE, pas
 * une mecanique. {@code additionner_deux_devises_differentes_est_refuse} se lit
 * comme une specification ; {@code testPlus2} ne dit rien a personne.
 */
class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Nested
    @DisplayName("Construction et normalisation")
    class Construction {

        @Test
        @DisplayName("l'echelle est normalisee sur celle de la devise")
        void l_echelle_est_normalisee() {
            // C'est LE test qui documente le piege du scale de BigDecimal.
            Money deuxEurosCinquante = Money.euros("2.5");

            assertThat(deuxEurosCinquante.amount().scale()).isEqualTo(2);
            assertThat(deuxEurosCinquante.amount()).isEqualByComparingTo("2.50");
        }

        @Test
        @DisplayName("deux montants economiquement egaux sont egaux au sens de equals")
        void egalite_par_valeur() {
            // Sans la normalisation du constructeur compact, cette assertion
            // echouerait : new BigDecimal("2.50").equals(new BigDecimal("2.5"))
            // vaut false. C'est le genre de bug qui survit des annees dans une
            // base de code, cache derriere un compareTo() de contournement.
            assertThat(Money.euros("2.50")).isEqualTo(Money.euros("2.5"));
            assertThat(Money.euros("2.50")).hasSameHashCodeAs(Money.euros("2.5"));
        }

        @Test
        @DisplayName("l'arrondi au centime suit la regle HALF_UP")
        void arrondi_half_up() {
            assertThat(Money.of(new BigDecimal("1.005"), Money.EUR))
                    .isEqualTo(Money.euros("1.01"));
        }
    }

    @Nested
    @DisplayName("Operations arithmetiques")
    class Operations {

        @Test
        @DisplayName("additionner deux montants de meme devise")
        void addition() {
            assertThat(Money.euros("49.90").plus(Money.euros("10.10")))
                    .isEqualTo(Money.euros("60.00"));
        }

        @Test
        @DisplayName("multiplier un tarif journalier par un nombre de jours")
        void multiplication() {
            Money tarifJournalier = Money.euros("49.90");
            assertThat(tarifJournalier.times(7)).isEqualTo(Money.euros("349.30"));
        }

        @Test
        @DisplayName("appliquer une remise en pourcentage")
        void remise() {
            assertThat(Money.euros("100.00").minusPercent(new BigDecimal("20")))
                    .isEqualTo(Money.euros("80.00"));
        }

        @Test
        @DisplayName("enchainer deux remises n'equivaut pas a la remise unique correspondante")
        void enchainer_des_remises_arrondit_a_chaque_etape() {
            // Mathematiquement, 0,9 x 0,9 = 0,81 : deux remises de 10 % devraient
            // donner exactement le meme resultat qu'une remise unique de 19 %.
            // Ce n'est pas le cas, parce que le constructeur de Money ramene chaque
            // resultat intermediaire a l'echelle de la devise. On arrondit donc au
            // centime a CHAQUE maillon de la chaine.
            //
            // Ce test ne denonce pas un bug : il FIGE le comportement, pour que la
            // conception de PricingPolicy au week-end 8 se fasse en connaissance de
            // cause plutot que de le decouvrir sur une facture.
            Money base = Money.euros("100.03");

            Money enChaine = base
                    .minusPercent(new BigDecimal("10"))
                    .minusPercent(new BigDecimal("10"));
            Money remiseUnique = base.minusPercent(new BigDecimal("19"));

            assertThat(enChaine)
                    .as("l'arrondi intermediaire fait diverger les deux chemins d'un centime")
                    .isEqualTo(Money.euros("81.03"))
                    .isNotEqualTo(remiseUnique);
            assertThat(remiseUnique).isEqualTo(Money.euros("81.02"));
        }

        @Test
        @DisplayName("les operations ne modifient jamais les operandes")
        void immuabilite() {
            Money original = Money.euros("100.00");
            original.plus(Money.euros("50.00"));   // resultat volontairement ignore

            assertThat(original)
                    .as("un Value Object est immuable : l'original est intact")
                    .isEqualTo(Money.euros("100.00"));
        }
    }

    @Nested
    @DisplayName("Invariants proteges")
    class Invariants {

        @Test
        @DisplayName("additionner deux devises differentes est refuse")
        void devises_incompatibles() {
            Money euros = Money.euros("10.00");
            Money dollars = Money.of(new BigDecimal("10.00"), USD);

            // On verifie le CODE, pas le message : le code est le contrat
            // stable, le message est du texte destine a l'humain.
            assertThatThrownBy(() -> euros.plus(dollars))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting(exception -> ((BusinessRuleViolation) exception).code())
                    .isEqualTo("MONEY_CURRENCY_MISMATCH");
        }

        @Test
        @DisplayName("comparer deux devises differentes est refuse")
        void comparaison_devises_incompatibles() {
            assertThatThrownBy(() ->
                    Money.euros("10.00").isGreaterThan(Money.of(BigDecimal.TEN, USD)))
                    .isInstanceOf(BusinessRuleViolation.class);
        }
    }

    @Nested
    @DisplayName("Predicats metier (Tell-Don't-Ask)")
    class Predicats {

        @Test
        void zero_negatif_positif() {
            assertThat(Money.zeroEuros().isZero()).isTrue();
            assertThat(Money.euros("-5.00").isNegative()).isTrue();
            assertThat(Money.euros("5.00").isPositive()).isTrue();
        }

        @Test
        void comparaisons() {
            assertThat(Money.euros("100.00").isGreaterThan(Money.euros("99.99"))).isTrue();
            assertThat(Money.euros("100.00").isLessThan(Money.euros("100.01"))).isTrue();
        }
    }
}
