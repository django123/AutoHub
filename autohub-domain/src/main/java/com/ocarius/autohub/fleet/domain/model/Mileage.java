package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;

import java.util.Objects;

/**
 * Kilometrage releve au compteur d'un vehicule, en kilometres.
 *
 * <p>Le value object ne porte que l'invariant "jamais negatif". L'invariant
 * "ne decroit jamais" n'est pas une propriete d'<i>un</i> kilometrage, mais de
 * la <i>suite</i> des kilometrages d'un vehicule : il vit donc dans
 * {@link Vehicle}, qui connait la valeur precedente.
 *
 * @param kilometers nombre de kilometres, positif ou nul
 */
public record Mileage(long kilometers) implements Comparable<Mileage> {

    /** Refuse une valeur negative. */
    public Mileage {
        BusinessRuleViolation.check(
                kilometers >= 0,
                "MILEAGE_NEGATIVE",
                "Un kilometrage ne peut pas etre negatif : " + kilometers);
    }

    /** Fabrique lisible : {@code Mileage.of(12_000)}. */
    public static Mileage of(long kilometers) {
        return new Mileage(kilometers);
    }

    /** Ce releve est-il strictement inferieur a l'autre ? */
    public boolean isLowerThan(Mileage other) {
        Objects.requireNonNull(other);
        return kilometers < other.kilometers;
    }

    /** Ce releve est-il strictement superieur a l'autre ? */
    public boolean isGreaterThan(Mileage other) {
        Objects.requireNonNull(other);
        return kilometers > other.kilometers;
    }

    @Override
    public int compareTo(Mileage other) {
        return Long.compare(kilometers, other.kilometers);
    }

    @Override
    public String toString() {
        return kilometers + " km";
    }
}
