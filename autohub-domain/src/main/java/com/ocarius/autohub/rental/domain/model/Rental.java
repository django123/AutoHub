package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.fleet.domain.model.VehicleId;
import com.ocarius.autohub.rental.domain.event.RentalCancelled;
import com.ocarius.autohub.rental.domain.event.RentalConfirmed;
import com.ocarius.autohub.rental.domain.event.RentalExpired;
import com.ocarius.autohub.rental.domain.event.RentalReserved;
import com.ocarius.autohub.rental.domain.event.VehiclePickedUp;
import com.ocarius.autohub.rental.domain.event.VehicleReturned;
import com.ocarius.autohub.rental.domain.exception.IllegalRentalTransitionException;
import com.ocarius.autohub.shared.domain.AggregateRoot;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregat racine du contexte {@code rental} : la location d'un vehicule par un
 * client sur une periode donnee.
 *
 * <h2>Frontiere de l'agregat</h2>
 * <ul>
 *   <li><b>Le vehicule est reference par son {@link VehicleId}</b>, jamais
 *       contenu. Charger une location ne charge pas un vehicule, et une location
 *       ne peut pas modifier un vehicule : chaque agregat est sa propre
 *       transaction.</li>
 *   <li><b>Le prix est fige a la creation</b> ({@link #quotedPrice()}). Un
 *       changement de grille tarifaire ne doit pas modifier le prix d'une
 *       location deja engagee : le client a accepte un montant, pas une formule.</li>
 *   <li><b>Les etats des lieux</b> ({@link VehicleHandover}) sont des value
 *       objects internes.</li>
 * </ul>
 *
 * <h2>Ce que cet agregat NE garantit PAS</h2>
 * Il ne garantit pas qu'un vehicule n'est pas loue deux fois sur des periodes
 * qui se chevauchent. Un agregat ne protege que ce qu'il voit dans sa propre
 * transaction ; deux {@code Rental} crees en parallele ne se voient pas.
 * Cet invariant traverse les agregats : il est garanti par la contrainte
 * d'exclusion {@code rental_no_overlap} de PostgreSQL, et remonte sous forme
 * de {@link com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException}.
 *
 * <h2>Ce qu'il garantit</h2>
 * <ol>
 *   <li>Seules les transitions de {@link RentalStatus} sont possibles.</li>
 *   <li>Une reservation ne peut pas etre confirmee apres son echeance.</li>
 *   <li>Le depart a lieu pendant la periode louee.</li>
 *   <li>Au retour, le compteur ne recule pas et la date ne precede pas le depart.</li>
 *   <li>Les donnees propres a un etat ({@code reservedUntil}, etats des lieux)
 *       sont presentes si et seulement si l'etat les justifie.</li>
 * </ol>
 *
 * <h2>Le temps</h2>
 * Toute methode qui a besoin de "maintenant" recoit un {@link Clock}. L'agregat
 * ne lit jamais l'horloge systeme : les tests fixent le temps, et une regle
 * comme l'expiration se teste sans {@code Thread.sleep}.
 */
public final class Rental extends AggregateRoot<RentalId> {

    /** Duree de validite d'une reservation non confirmee (roadmap §3.3). */
    public static final Duration RESERVATION_TTL = Duration.ofMinutes(30);

    private final VehicleId vehicleId;
    private final CustomerId customerId;
    private final RentalPeriod period;
    private final Money quotedPrice;

    private RentalStatus status;
    /** Echeance de la reservation. Non null si et seulement si {@code status == RESERVED}. */
    private Instant reservedUntil;
    /** Etat des lieux au depart. Non null a partir de {@code PICKED_UP}. */
    private VehicleHandover pickup;
    /** Etat des lieux au retour. Non null a partir de {@code RETURNED}. */
    private VehicleHandover dropoff;

    private Rental(RentalId id, VehicleId vehicleId, CustomerId customerId, RentalPeriod period,
                   Money quotedPrice, RentalStatus status, Instant reservedUntil,
                   VehicleHandover pickup, VehicleHandover dropoff) {
        super(id);
        this.vehicleId = Objects.requireNonNull(vehicleId, "Le vehicule est obligatoire");
        this.customerId = Objects.requireNonNull(customerId, "Le client est obligatoire");
        this.period = Objects.requireNonNull(period, "La periode est obligatoire");
        this.quotedPrice = Objects.requireNonNull(quotedPrice, "Le prix est obligatoire");
        this.status = Objects.requireNonNull(status, "Le statut est obligatoire");
        this.reservedUntil = reservedUntil;
        this.pickup = pickup;
        this.dropoff = dropoff;
        BusinessRuleViolation.check(
                quotedPrice.isPositive(),
                "RENTAL_PRICE_NOT_POSITIVE",
                "Le prix d'une location doit etre strictement positif : " + quotedPrice);
    }

    // =================================================================
    // Creation et reconstitution
    // =================================================================

    /**
     * Cree une location au brouillon. Elle n'engage rien et ne bloque pas le
     * vehicule : c'est {@link #reserve(Clock)} qui la rend effective.
     *
     * @param id          identifiant genere par l'appelant (voir {@link RentalId#newId()})
     * @param vehicleId   vehicule demande
     * @param customerId  client
     * @param period      periode demandee
     * @param quotedPrice prix calcule par la tarification, fige des maintenant
     */
    public static Rental draft(RentalId id, VehicleId vehicleId, CustomerId customerId,
                               RentalPeriod period, Money quotedPrice) {
        return new Rental(id, vehicleId, customerId, period, quotedPrice,
                RentalStatus.DRAFT, null, null, null);
    }

    /**
     * Reconstitue une location relue depuis la persistance.
     *
     * <h3>Pourquoi ne pas rejouer les methodes metier</h3>
     * Rejouer {@code draft().reserve().confirm()...} reverifierait des regles
     * dependantes du moment (preavis, echeance) qui etaient vraies hier et ne
     * le sont plus : une location confirmee la semaine derniere ne pourrait plus
     * etre rechargee. On ne revalide donc pas l'<i>historique</i>.
     *
     * <h3>Pourquoi ne pas tout contourner pour autant</h3>
     * Les value objects passes en parametre se sont deja revalides a leur
     * construction (periode, prix, constats). Et cette methode verifie la
     * <i>coherence</i> entre l'etat et ses donnees : une ligne {@code PICKED_UP}
     * sans constat de depart est une corruption, qu'il vaut mieux voir echouer
     * au chargement que propager.
     *
     * <p>Aucun evenement n'est emis : rien ne vient de se produire.
     *
     * @throws IllegalStateException si les donnees ne sont pas coherentes avec l'etat
     */
    public static Rental restore(RentalId id, VehicleId vehicleId, CustomerId customerId,
                                 RentalPeriod period, Money quotedPrice, RentalStatus status,
                                 Instant reservedUntil, VehicleHandover pickup, VehicleHandover dropoff) {
        Rental rental = new Rental(id, vehicleId, customerId, period, quotedPrice,
                status, reservedUntil, pickup, dropoff);
        rental.checkStateConsistency();
        return rental;
    }

    // =================================================================
    // Comportements (une methode par transition)
    // =================================================================

    /**
     * Reserve le vehicule pour {@link #RESERVATION_TTL}. Le client doit
     * confirmer avant l'echeance, sinon la reservation expire.
     *
     * @param clock horloge de reference
     * @throws IllegalRentalTransitionException si la location n'est pas au brouillon
     * @throws BusinessRuleViolation            si la periode commence trop tot (preavis)
     */
    public void reserve(Clock clock) {
        ensureCanTransitionTo(RentalStatus.RESERVED);
        BusinessRuleViolation.check(
                period.respectsMinimumNoticeFrom(LocalDateTime.now(clock)),
                "RENTAL_NOTICE_TOO_SHORT",
                "Une location doit commencer au moins " + RentalPeriod.MINIMUM_NOTICE.toMinutes()
                        + " minutes apres la reservation : " + period);
        Instant now = clock.instant();
        this.reservedUntil = now.plus(RESERVATION_TTL);
        this.status = RentalStatus.RESERVED;
        registerEvent(new RentalReserved(UUID.randomUUID(), now, id(), vehicleId,
                period.start(), period.end(), reservedUntil));
    }

    /**
     * Confirme la reservation. Elle ne peut plus expirer.
     *
     * <p>L'echeance est semi-ouverte comme tout intervalle du projet : a
     * l'instant exact {@code reservedUntil}, il est deja trop tard.
     *
     * @throws IllegalRentalTransitionException si la location n'est pas reservee
     * @throws BusinessRuleViolation            si l'echeance est depassee
     */
    public void confirm(Clock clock) {
        ensureCanTransitionTo(RentalStatus.CONFIRMED);
        Instant now = clock.instant();
        BusinessRuleViolation.check(
                !isReservationOverdueAt(now),
                "RENTAL_RESERVATION_EXPIRED",
                "La reservation a expire le " + reservedUntil + " ; elle ne peut plus etre confirmee");
        this.reservedUntil = null;
        this.status = RentalStatus.CONFIRMED;
        registerEvent(new RentalConfirmed(UUID.randomUUID(), now, id(), vehicleId));
    }

    /**
     * Fait expirer la reservation si son echeance est depassee.
     *
     * <p>Methode "Tell, don't ask" : l'appelant (un traitement planifie, au
     * week-end 7) n'a pas a lire le statut ni l'echeance pour decider. Il le
     * demande a chaque location, qui decide seule. Appelee sur une location dans
     * un autre etat, elle ne fait rien : cela rend le traitement idempotent.
     *
     * @return {@code true} si la location vient d'expirer
     */
    public boolean expireIfOverdue(Clock clock) {
        Instant now = clock.instant();
        if (status != RentalStatus.RESERVED || !isReservationOverdueAt(now)) {
            return false;
        }
        this.reservedUntil = null;
        this.status = RentalStatus.EXPIRED;
        registerEvent(new RentalExpired(UUID.randomUUID(), now, id(), vehicleId));
        return true;
    }

    /**
     * Annule la location. Possible tant que le vehicule n'est pas parti.
     *
     * @param reason motif, conserve dans l'evenement
     * @throws IllegalRentalTransitionException apres le depart, ou depuis un etat terminal
     */
    public void cancel(String reason, Clock clock) {
        Objects.requireNonNull(reason, "Le motif d'annulation est obligatoire");
        ensureCanTransitionTo(RentalStatus.CANCELLED);
        this.reservedUntil = null;
        this.status = RentalStatus.CANCELLED;
        registerEvent(new RentalCancelled(UUID.randomUUID(), clock.instant(), id(), vehicleId, reason));
    }

    /**
     * Enregistre le depart du vehicule.
     *
     * @param handover etat des lieux au depart
     * @throws IllegalRentalTransitionException si la location n'est pas confirmee
     * @throws BusinessRuleViolation            si le depart a lieu hors de la periode louee
     */
    public void pickUp(VehicleHandover handover, Clock clock) {
        Objects.requireNonNull(handover, "L'etat des lieux de depart est obligatoire");
        ensureCanTransitionTo(RentalStatus.PICKED_UP);
        // Hors periode, le vehicule peut etre occupe par une autre location :
        // la contrainte d'exclusion ne protege que la periode reservee.
        BusinessRuleViolation.check(
                period.contains(handover.at()),
                "RENTAL_PICKUP_OUTSIDE_PERIOD",
                "Le depart (" + handover.at() + ") doit avoir lieu pendant la periode louee " + period);
        this.pickup = handover;
        this.status = RentalStatus.PICKED_UP;
        registerEvent(new VehiclePickedUp(UUID.randomUUID(), clock.instant(), id(), vehicleId,
                handover.at(), handover.mileageKm()));
    }

    /**
     * Enregistre le retour du vehicule.
     *
     * <p>Un retour anticipe est accepte et ne change pas le prix fige. Un retour
     * tardif est accepte aussi (on ne refuse pas une voiture au guichet) ; il est
     * signale par {@link #isLateReturn()} et les penalites seront calculees par
     * la tarification au week-end 9.
     *
     * @param handover etat des lieux au retour
     * @throws IllegalRentalTransitionException si le vehicule n'est pas parti
     * @throws BusinessRuleViolation            si le compteur recule ou si le retour precede le depart
     */
    public void returnVehicle(VehicleHandover handover, Clock clock) {
        Objects.requireNonNull(handover, "L'etat des lieux de retour est obligatoire");
        ensureCanTransitionTo(RentalStatus.RETURNED);
        BusinessRuleViolation.check(
                !handover.hasLowerMileageThan(pickup),
                "RENTAL_RETURN_MILEAGE_DECREASED",
                "Kilometrage au retour (" + handover.mileageKm()
                        + ") inferieur a celui du depart (" + pickup.mileageKm() + ")");
        BusinessRuleViolation.check(
                !handover.happenedBefore(pickup),
                "RENTAL_RETURN_BEFORE_PICKUP",
                "Le retour (" + handover.at() + ") ne peut pas preceder le depart (" + pickup.at() + ")");
        this.dropoff = handover;
        this.status = RentalStatus.RETURNED;
        registerEvent(new VehicleReturned(UUID.randomUUID(), clock.instant(), id(), vehicleId,
                handover.at(), handover.mileageKm(), isLateReturn()));
    }

    /**
     * Clot le dossier apres le retour. Pas d'evenement : la cloture est
     * administrative et n'interesse aucun autre contexte pour l'instant.
     *
     * @throws IllegalRentalTransitionException si le vehicule n'est pas revenu
     */
    public void close() {
        ensureCanTransitionTo(RentalStatus.CLOSED);
        this.status = RentalStatus.CLOSED;
    }

    // =================================================================
    // Questions metier
    // =================================================================

    /** La location occupe-t-elle actuellement le vehicule sur sa periode ? */
    public boolean blocksVehicle() {
        return status.blocksVehicle();
    }

    /** Le vehicule est-il revenu apres la fin prevue ? {@code false} tant qu'il n'est pas revenu. */
    public boolean isLateReturn() {
        return dropoff != null && period.endsBefore(dropoff.at());
    }

    /** Distance parcourue pendant la location, connue seulement apres le retour. */
    public Optional<Long> kilometersDriven() {
        return dropoff == null ? Optional.empty() : Optional.of(dropoff.kilometersSince(pickup));
    }

    // =================================================================
    // Lecture de l'etat
    // =================================================================
    // Ces accesseurs existent pour le mapper de persistance et, plus tard, pour
    // les DTO de lecture. Ils ne doivent pas servir a prendre une decision a la
    // place de l'agregat : si tu ecris "if (rental.status() == ...)" dans un
    // service, la regle appartient probablement a une methode de cette classe.

    public VehicleId vehicleId() {
        return vehicleId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public RentalPeriod period() {
        return period;
    }

    public Money quotedPrice() {
        return quotedPrice;
    }

    public RentalStatus status() {
        return status;
    }

    /** Echeance de la reservation, presente uniquement en {@code RESERVED}. */
    public Optional<Instant> reservedUntil() {
        return Optional.ofNullable(reservedUntil);
    }

    /** Etat des lieux de depart, present a partir de {@code PICKED_UP}. */
    public Optional<VehicleHandover> pickup() {
        return Optional.ofNullable(pickup);
    }

    /** Etat des lieux de retour, present a partir de {@code RETURNED}. */
    public Optional<VehicleHandover> dropoff() {
        return Optional.ofNullable(dropoff);
    }

    // =================================================================
    // Mecanique interne
    // =================================================================

    /** Leve l'exception de transition si la machine a etats refuse le passage. */
    private void ensureCanTransitionTo(RentalStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalRentalTransitionException(id(), status, target);
        }
    }

    /** Echeance semi-ouverte : la reservation est valide sur {@code [reservation, reservedUntil)}. */
    private boolean isReservationOverdueAt(Instant now) {
        return !now.isBefore(reservedUntil);
    }

    /**
     * Verifie que les donnees optionnelles sont presentes exactement quand
     * l'etat les justifie. Utilisee a la reconstitution.
     */
    private void checkStateConsistency() {
        boolean hasPickupState = status == RentalStatus.PICKED_UP
                || status == RentalStatus.RETURNED
                || status == RentalStatus.CLOSED;
        boolean hasDropoffState = status == RentalStatus.RETURNED || status == RentalStatus.CLOSED;

        requireConsistent((reservedUntil != null) == (status == RentalStatus.RESERVED), "reservedUntil");
        requireConsistent((pickup != null) == hasPickupState, "pickup");
        requireConsistent((dropoff != null) == hasDropoffState, "dropoff");
    }

    private void requireConsistent(boolean consistent, String field) {
        if (!consistent) {
            throw new IllegalStateException(
                    "Location " + id().value() + " incoherente : champ " + field + " incompatible avec le statut " + status);
        }
    }
}
