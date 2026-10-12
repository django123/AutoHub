package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.vo.DateRange;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Periode d'une location : un {@link DateRange} semi-ouvert {@code [start, end)}
 * enrichi des regles propres a la location.
 *
 * <h2>Pourquoi envelopper {@code DateRange} plutot que l'etendre</h2>
 * Un record ne peut pas heriter, mais meme si c'etait possible, ce serait une
 * erreur : un {@code RentalPeriod} n'est pas "un {@code DateRange} avec des
 * options". Il est <i>plus restrictif</i> (duree minimale, maximale, precision
 * a la minute). Par composition, on reutilise la logique de chevauchement deja
 * testee, sans laisser fuir un intervalle d'une nanoseconde dans une location.
 *
 * <h2>Les invariants verifies a la construction</h2>
 * <ol>
 *   <li><b>Bornes a la minute pres</b> (secondes et nanosecondes nulles).
 *       C'est la reponse au point 2 de l'ADR 0002 : la colonne {@code timestamp}
 *       de PostgreSQL a une precision d'une microseconde, {@code LocalDateTime}
 *       d'une nanoseconde. Un intervalle sub-microseconde valide en Java
 *       deviendrait <i>vide</i> en base, et un intervalle vide ne chevauche
 *       rien : la contrainte d'exclusion ne jouerait plus. On refuse plutot
 *       que de tronquer : tronquer modifierait silencieusement la demande du
 *       client, refuser rend l'erreur visible.</li>
 *   <li><b>Duree minimale d'une heure</b> : en dessous, ce n'est pas une
 *       location mais une erreur de saisie.</li>
 *   <li><b>Duree maximale de 90 jours</b> : au-dela, le metier parle de
 *       location longue duree, qui est un autre produit.</li>
 * </ol>
 *
 * <h2>Pourquoi le preavis n'est PAS verifie ici</h2>
 * Le preavis ("on ne reserve pas pour dans dix minutes") depend de
 * <i>maintenant</i>. Le verifier dans le constructeur obligerait a lire
 * l'horloge a chaque construction, y compris quand on relit depuis la base
 * une location passee -- qui serait alors refusee. Il est donc expose par
 * {@link #respectsMinimumNoticeFrom(LocalDateTime)} et applique par
 * {@link Rental#reserve(java.time.Clock)}, au seul moment ou il a du sens.
 *
 * <p><b>Attention a l'evolution des regles.</b> Toute regle placee dans ce
 * constructeur s'applique aussi a la relecture des donnees existantes. Si un
 * jour la duree maximale passe de 90 a 60 jours, les locations de 75 jours
 * deja en base ne pourront plus etre rechargees. Il faudra alors deplacer la
 * regle vers {@link Rental#draft}, ou migrer les donnees.
 *
 * @param range l'intervalle semi-ouvert sous-jacent
 */
public record RentalPeriod(DateRange range) {

    /** En dessous d'une heure, une location n'a pas de sens commercial. */
    public static final Duration MINIMUM_DURATION = Duration.ofHours(1);

    /** Au-dela, on sort de la location courte duree. */
    public static final Duration MAXIMUM_DURATION = Duration.ofDays(90);

    /** Delai minimal entre la reservation et le debut de la location. */
    public static final Duration MINIMUM_NOTICE = Duration.ofHours(1);

    /** Verifie les trois invariants decrits dans la javadoc de la classe. */
    public RentalPeriod {
        Objects.requireNonNull(range, "L'intervalle de la location est obligatoire");
        BusinessRuleViolation.check(
                isWholeMinute(range.start()) && isWholeMinute(range.end()),
                "RENTAL_PERIOD_NOT_MINUTE_ALIGNED",
                "Les bornes d'une location s'expriment a la minute pres : " + range);
        BusinessRuleViolation.check(
                range.duration().compareTo(MINIMUM_DURATION) >= 0,
                "RENTAL_PERIOD_TOO_SHORT",
                "Une location dure au moins " + MINIMUM_DURATION.toHours() + " h : " + range);
        BusinessRuleViolation.check(
                range.duration().compareTo(MAXIMUM_DURATION) <= 0,
                "RENTAL_PERIOD_TOO_LONG",
                "Une location dure au plus " + MAXIMUM_DURATION.toDays() + " jours : " + range);
    }

    /**
     * Fabrique lisible a partir des deux bornes.
     *
     * @param start debut, inclus
     * @param end   fin, exclue
     */
    public static RentalPeriod of(LocalDateTime start, LocalDateTime end) {
        return new RentalPeriod(DateRange.of(start, end));
    }

    /** Debut de la location (inclus). Delegation pour eviter {@code period.range().start()}. */
    public LocalDateTime start() {
        return range.start();
    }

    /** Fin prevue de la location (exclue). */
    public LocalDateTime end() {
        return range.end();
    }

    /**
     * Deux periodes se chevauchent-elles ? Meme definition que l'operateur
     * {@code &&} de PostgreSQL sur un {@code tsrange '[)'} : deux periodes
     * adjacentes ne se chevauchent pas.
     */
    public boolean overlaps(RentalPeriod other) {
        Objects.requireNonNull(other);
        return range.overlaps(other.range);
    }

    /** L'instant tombe-t-il dans la periode ? La fin est exclue. */
    public boolean contains(LocalDateTime instant) {
        return range.contains(instant);
    }

    /**
     * La periode commence-t-elle assez tard par rapport a {@code now} ?
     *
     * @param now l'instant de la demande, lu sur une horloge injectee
     * @return {@code true} si le debut est au moins {@link #MINIMUM_NOTICE} apres {@code now}
     */
    public boolean respectsMinimumNoticeFrom(LocalDateTime now) {
        Objects.requireNonNull(now);
        return !range.start().isBefore(now.plus(MINIMUM_NOTICE));
    }

    /** L'instant donne est-il posterieur a la fin prevue ? Sert a detecter un retour tardif. */
    public boolean endsBefore(LocalDateTime instant) {
        Objects.requireNonNull(instant);
        return range.end().isBefore(instant);
    }

    /** Duree prevue de la location. */
    public Duration duration() {
        return range.duration();
    }

    /** Nombre de jours facturables, arrondi au superieur (voir {@link DateRange#billableDays()}). */
    public long billableDays() {
        return range.billableDays();
    }

    private static boolean isWholeMinute(LocalDateTime instant) {
        return instant.getSecond() == 0 && instant.getNano() == 0;
    }

    @Override
    public String toString() {
        return range.toString();
    }
}
