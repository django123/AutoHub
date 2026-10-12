package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immatriculation francaise au format SIV : {@code AA-123-AA}.
 *
 * <h2>Les regles verifiees</h2>
 * <ul>
 *   <li>Deux lettres, tiret, trois chiffres, tiret, deux lettres.</li>
 *   <li>Les lettres I, O et U sont exclues (confusion avec 1, 0 et V).</li>
 *   <li>Le bloc numerique {@code 000} n'est pas attribue.</li>
 * </ul>
 * La saisie est normalisee : majuscules, et les espaces acceptes a la place
 * des tirets ("ab 123 cd" devient "AB-123-CD").
 *
 * <p>Format volontairement restreint a la France : AutoHub est mono-agence
 * (voir {@code BACKLOG.md}). Un vehicule immatricule a l'etranger demanderait
 * un autre type, pas un assouplissement de celui-ci.
 *
 * @param value l'immatriculation normalisee
 */
public record PlateNumber(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("[A-HJ-NP-TV-Z]{2}-(?!000)[0-9]{3}-[A-HJ-NP-TV-Z]{2}");

    /** Normalise puis verifie le format. */
    public PlateNumber {
        Objects.requireNonNull(value, "L'immatriculation est obligatoire");
        value = value.strip().toUpperCase(Locale.ROOT).replace(' ', '-');
        BusinessRuleViolation.check(
                FORMAT.matcher(value).matches(),
                "PLATE_NUMBER_INVALID",
                "Une immatriculation suit le format AA-123-AA, sans I, O ni U : " + value);
    }

    /** Fabrique lisible. */
    public static PlateNumber of(String value) {
        return new PlateNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
