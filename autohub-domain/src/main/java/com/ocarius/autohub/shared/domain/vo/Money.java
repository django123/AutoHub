package com.ocarius.autohub.shared.domain.vo;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Montant monetaire : un couple indissociable (valeur, devise).
 *
 * <h2>1. Pourquoi jamais de {@code double}</h2>
 * <pre>{@code
 * System.out.println(0.1 + 0.2);   // 0.30000000000000004
 * }</pre>
 * Un binaire flottant ne peut pas representer exactement 0,1 en base 10.
 * Sur un loyer journalier repete 30 fois, l'ecart devient visible sur une
 * facture. {@link BigDecimal} stocke les chiffres decimaux tels quels :
 * c'est le seul choix defendable pour de l'argent.
 *
 * <h2>2. Pourquoi la devise voyage avec le montant</h2>
 * Un {@code BigDecimal} nu ne sait pas s'il vaut des euros ou des francs CFA.
 * Les additionner produit un resultat faux que rien ne signale. Ici,
 * {@link #plus(Money)} leve une exception : l'erreur devient bruyante.
 *
 * <h2>3. Le piege du scale (a bien comprendre)</h2>
 * {@code BigDecimal.equals} compare aussi l'echelle :
 * <pre>{@code
 * new BigDecimal("2.50").equals(new BigDecimal("2.5"));     // false !
 * new BigDecimal("2.50").compareTo(new BigDecimal("2.5"));  // 0
 * }</pre>
 * Comme {@code Money} est un <b>record</b>, son {@code equals} genere delegue
 * a celui de {@code BigDecimal} et heriterait du probleme. La parade est dans
 * le constructeur compact ci-dessous : on normalise systematiquement l'echelle
 * sur celle de la devise (2 pour l'euro, 0 pour le yen). Deux montants egaux
 * economiquement sont alors egaux au sens de {@code equals}.
 *
 * <h2>4. Pourquoi un record, et pourquoi immuable</h2>
 * Un Value Object n'a pas d'identite : 10 EUR, c'est 10 EUR, peu importe
 * l'instance. L'immuabilite rend l'objet partageable sans copie defensive et
 * intrinsequement thread-safe. Chaque operation retourne un <i>nouveau</i>
 * {@code Money} : {@code a.plus(b)} ne modifie ni {@code a} ni {@code b}.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final Currency EUR = Currency.getInstance("EUR");

    /**
     * Constructeur compact : c'est ici que l'invariant est protege.
     * Impossible de construire un {@code Money} invalide, donc impossible
     * d'en rencontrer un ailleurs dans le code. Aucune verification defensive
     * n'est necessaire chez les appelants.
     */
    public Money {
        Objects.requireNonNull(amount, "Le montant ne peut pas etre null");
        Objects.requireNonNull(currency, "La devise ne peut pas etre null");
        // Normalisation de l'echelle : voir le point 3 de la javadoc.
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }

    // ---------- Fabriques ----------
    // On expose des fabriques nommees plutot que le constructeur : l'appel
    // Money.euros("49.90") se lit mieux que new Money(new BigDecimal("49.90"), EUR).

    /** Toujours partir d'une String : {@code BigDecimal.valueOf(49.90)} repasse par un double. */
    public static Money euros(String amount) {
        return new Money(new BigDecimal(amount), EUR);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money zeroEuros() {
        return zero(EUR);
    }

    // ---------- Operations ----------

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), currency);
    }

    /** Multiplication par une quantite : {@code tarifJournalier.times(7)}. */
    public Money times(long factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), currency);
    }

    /**
     * Applique un pourcentage de remise.
     * {@code Money.euros("100").minusPercent(new BigDecimal("20"))} donne 80,00 EUR.
     *
     * <h3>Pourquoi la division n'a besoin ni d'echelle ni de mode d'arrondi</h3>
     * {@code BigDecimal.divide(x)} ne leve {@code ArithmeticException} que si le
     * quotient exact n'a pas de representation decimale finie. Or diviser par 100
     * revient a decaler la virgule de deux rangs : le resultat termine toujours en
     * base 10. Une echelle intermediaire n'eviterait donc aucune exception ; elle
     * ne ferait qu'ajouter un arrondi dont personne n'a besoin.
     *
     * <h3>L'arrondi qui compte, lui, est ailleurs</h3>
     * Le constructeur compact ramene le resultat a l'echelle de la devise. C'est
     * voulu -- c'est ce qui fait marcher {@code equals} (point 3 de la javadoc de
     * classe) -- mais cela a une consequence a connaitre avant le week-end 8 :
     * enchainer deux remises de 10 % n'equivaut PAS a une remise unique de 19 %,
     * parce qu'on arrondit au centime a chaque maillon. Ce n'est pas un defaut de
     * {@code Money} : c'est une contrainte que la chaine de {@code PricingPolicy}
     * devra assumer explicitement. Voir
     * {@code MoneyTest.Operations.enchainer_des_remises_arrondit_a_chaque_etape}.
     */
    public Money minusPercent(BigDecimal percent) {
        BigDecimal keptRatio = BigDecimal.ONE.subtract(
                percent.divide(BigDecimal.valueOf(100)));
        return times(keptRatio);
    }

    // ---------- Predicats ----------
    //
    // Tell-Don't-Ask : on expose des questions metier plutot que le BigDecimal
    // interne. Le code appelant ecrit "if (total.isNegative())" et non
    // "if (total.amount().compareTo(BigDecimal.ZERO) < 0)" -- qui serait une
    // violation de la Loi de Demeter (on fouille dans les entrailles de l'objet).

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "L'operande ne peut pas etre null");
        BusinessRuleViolation.check(
                this.currency.equals(other.currency),
                "MONEY_CURRENCY_MISMATCH",
                "Operation impossible entre " + this.currency + " et " + other.currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
