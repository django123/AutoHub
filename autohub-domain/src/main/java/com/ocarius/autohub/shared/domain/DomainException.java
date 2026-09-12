package com.ocarius.autohub.shared.domain;

/**
 * Exception racine de toutes les violations de regles metier.
 *
 * <h2>Pourquoi une exception dediee ?</h2>
 * Le domaine ne doit jamais lever d'{@link IllegalArgumentException} nue pour
 * exprimer une regle metier. Une {@code IllegalArgumentException} dit
 * "le developpeur s'est trompe" ; une {@code DomainException} dit
 * "l'utilisateur a demande quelque chose que le metier interdit".
 * Ce ne sont pas les memes destinataires, ni le meme code HTTP.
 *
 * <h2>Pourquoi un code ?</h2>
 * Le champ {@code code} est un identifiant stable, lisible par une machine
 * ({@code RENTAL_PERIOD_INVALID}), que l'adaptateur REST traduira en reponse
 * normalisee. Le message, lui, est destine a l'humain et peut evoluer
 * librement sans casser les clients de l'API.
 *
 * <p>Noter que cette classe n'est pas {@code checked} : forcer chaque appelant
 * a declarer {@code throws} pollue les signatures du domaine sans rien
 * apporter, puisqu'une violation de regle metier n'est jamais rattrapable
 * localement.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Identifiant stable de la regle violee, destine aux machines. */
    public String code() {
        return code;
    }
}
