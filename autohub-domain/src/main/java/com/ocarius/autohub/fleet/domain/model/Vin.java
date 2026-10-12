package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Numero d'identification d'un vehicule (VIN, norme ISO 3779).
 *
 * <h2>Les regles verifiees</h2>
 * <ul>
 *   <li>Exactement 17 caracteres.</li>
 *   <li>Chiffres et lettres majuscules, <b>sauf I, O et Q</b> : la norme les
 *       exclut parce qu'ils se confondent avec 1 et 0. C'est le cas que la
 *       plupart des validations maison oublient.</li>
 * </ul>
 * La saisie est normalisee en majuscules avant verification : "vf3..." et
 * "VF3..." designent le meme vehicule, et doivent donc etre egaux.
 *
 * <p>Le chiffre de controle (9e caractere) n'est pas verifie : il n'est
 * obligatoire qu'en Amerique du Nord, et un VIN europeen valide peut ne pas le
 * respecter.
 *
 * @param value le VIN normalise en majuscules
 */
public record Vin(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");

    /** Normalise puis verifie le format. */
    public Vin {
        Objects.requireNonNull(value, "Le VIN est obligatoire");
        value = value.strip().toUpperCase(Locale.ROOT);
        BusinessRuleViolation.check(
                FORMAT.matcher(value).matches(),
                "VIN_INVALID",
                "Un VIN compte 17 caracteres, chiffres et lettres hors I, O et Q : " + value);
    }

    /** Fabrique lisible. */
    public static Vin of(String value) {
        return new Vin(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
