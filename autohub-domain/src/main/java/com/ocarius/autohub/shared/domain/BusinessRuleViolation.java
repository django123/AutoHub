package com.ocarius.autohub.shared.domain;

/**
 * Violation generique d'une regle metier, utilisable quand creer une classe
 * d'exception dediee serait excessif.
 *
 * <p><b>Attention a l'usage.</b> La facilite de cette classe est un piege :
 * si tu l'utilises partout, tu perds la capacite d'attraper precisement un cas
 * donne. Regle pratique : des qu'un appelant a besoin de distinguer ce cas
 * des autres, cree une sous-classe de {@link DomainException} dediee
 * (par exemple {@code VehicleNotAvailableException}).
 */
public class BusinessRuleViolation extends DomainException {

    public BusinessRuleViolation(String code, String message) {
        super(code, message);
    }

    /**
     * Garde defensive lisible : {@code BusinessRuleViolation.check(...)}.
     *
     * <p>Preferer cette forme aux {@code if (...) throw ...} disperses :
     * l'intention ("ceci est un invariant") devient visible d'un coup d'oeil.
     */
    public static void check(boolean condition, String code, String message) {
        if (!condition) {
            throw new BusinessRuleViolation(code, message);
        }
    }
}
