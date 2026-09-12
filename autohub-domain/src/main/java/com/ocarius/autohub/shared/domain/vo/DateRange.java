package com.ocarius.autohub.shared.domain.vo;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Intervalle temporel <b>semi-ouvert</b> : {@code [start, end)}.
 * Le debut est inclus, la fin est exclue.
 *
 * <h2>Pourquoi semi-ouvert, et pourquoi c'est la decision la plus
 * importante de cette classe</h2>
 *
 * Prenons deux locations qui se suivent :
 * <ul>
 *   <li>Location A : du 10 mars 08:00 au 12 mars 08:00</li>
 *   <li>Location B : du 12 mars 08:00 au 14 mars 08:00</li>
 * </ul>
 *
 * Avec un intervalle <b>ferme</b> {@code [start, end]}, ces deux locations se
 * chevauchent le 12 mars a 08:00. Il faudrait ecrire partout des
 * {@code end.minusSeconds(1)} ou {@code minusNanos(1)} : laid, dependant de la
 * precision du stockage, et source de bugs qui n'apparaissent qu'en production.
 *
 * <p>Avec un intervalle <b>semi-ouvert</b>, A se termine juste avant l'instant
 * ou B commence. Aucun chevauchement, aucune soustraction arbitraire. Deux
 * intervalles adjacents sont naturellement compatibles.
 *
 * <p>Bonus decisif pour la suite du projet : c'est exactement la semantique
 * par defaut du type {@code tstzrange} de PostgreSQL, note {@code '[)'}.
 * Notre modele Java et notre contrainte d'exclusion GiST partageront donc la
 * meme definition du chevauchement -- sans quoi la base et le code se
 * contrediraient sur les cas limites.
 *
 * <h2>Rappel sur le fuseau horaire</h2>
 * On utilise {@link LocalDateTime} par simplicite pedagogique. Pour un service
 * reellement multi-agences, il faudrait {@code ZonedDateTime} ou stocker en UTC :
 * un retour de vehicule a "18:00" ne designe pas le meme instant a Paris et a
 * Douala. C'est note dans BACKLOG.md.
 */
public record DateRange(LocalDateTime start, LocalDateTime end) {

    /**
     * Protege l'invariant fondamental : un intervalle a une duree strictement
     * positive. {@code start == end} est refuse -- un intervalle vide n'a aucun
     * sens metier et se propagerait silencieusement dans les calculs de prix.
     */
    public DateRange {
        Objects.requireNonNull(start, "La date de debut est obligatoire");
        Objects.requireNonNull(end, "La date de fin est obligatoire");
        BusinessRuleViolation.check(
                end.isAfter(start),
                "DATE_RANGE_INVALID",
                "La fin (" + end + ") doit etre strictement posterieure au debut (" + start + ")");
    }

    public static DateRange of(LocalDateTime start, LocalDateTime end) {
        return new DateRange(start, end);
    }

    /**
     * Deux intervalles se chevauchent-ils ?
     *
     * <p>La formule canonique est {@code start < other.end && other.start < end}.
     * Elle merite d'etre comprise plutot que recopiee : deux intervalles
     * <b>ne</b> se chevauchent <b>pas</b> si l'un se termine avant que l'autre
     * ne commence. La negation de cette condition donne la formule ci-dessous.
     *
     * <p>Les comparaisons sont strictes des deux cotes : c'est ce qui rend les
     * intervalles adjacents non chevauchants.
     */
    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other);
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    /** L'instant est-il dans l'intervalle ? Rappel : la borne de fin est exclue. */
    public boolean contains(LocalDateTime instant) {
        Objects.requireNonNull(instant);
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    /** Cet intervalle englobe-t-il entierement l'autre ? */
    public boolean containsRange(DateRange other) {
        Objects.requireNonNull(other);
        return !other.start.isBefore(this.start) && !other.end.isAfter(this.end);
    }

    public boolean isBefore(DateRange other) {
        return !this.end.isAfter(other.start);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * Nombre de jours <b>facturables</b>, arrondi a l'entier superieur.
     *
     * <p>C'est une regle metier, pas un calcul technique : 25 heures de location
     * se facturent 2 jours. Elle vit ici, dans le Value Object, plutot que dans
     * un service de tarification -- parce qu'elle decoule de la nature meme de
     * l'intervalle et sera identique pour toutes les politiques de prix.
     *
     * <p>Si demain la regle devient "tolerance d'une heure de depassement",
     * un seul endroit change, et les tests de cette classe le couvrent deja.
     */
    public long billableDays() {
        Duration duration = duration();
        long fullDays = duration.toDays();
        boolean hasRemainder = duration.minusDays(fullDays).toNanos() > 0;
        return hasRemainder ? fullDays + 1 : fullDays;
    }

    @Override
    public String toString() {
        return "[" + start + " -> " + end + ")";
    }
}
