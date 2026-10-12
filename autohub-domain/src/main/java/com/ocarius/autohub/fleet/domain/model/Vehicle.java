package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.fleet.domain.event.VehicleAddedToFleet;
import com.ocarius.autohub.fleet.domain.event.VehicleMileageUpdated;
import com.ocarius.autohub.fleet.domain.event.VehicleOfferingChanged;
import com.ocarius.autohub.fleet.domain.event.VehicleSold;
import com.ocarius.autohub.fleet.domain.event.VehicleWithdrawn;
import com.ocarius.autohub.fleet.domain.exception.IllegalVehicleTransitionException;
import com.ocarius.autohub.shared.domain.AggregateRoot;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Agregat racine du contexte {@code fleet} : un vehicule physique du parc.
 *
 * <h2>Invariants proteges</h2>
 * <ol>
 *   <li>Le kilometrage ne decroit jamais.</li>
 *   <li>Seules les transitions de {@link VehicleStatus} sont possibles.</li>
 *   <li>Un vehicule vendu ou retire ne recoit plus d'offre.</li>
 *   <li>Un vehicule endommage ne reste pas dans le parc disponible : le
 *       signaler l'envoie en maintenance.</li>
 *   <li>Le tarif journalier de location est strictement positif.</li>
 * </ol>
 *
 * <h2>Ce que l'agregat ne garantit pas</h2>
 * <ul>
 *   <li><b>L'unicite du VIN et de l'immatriculation</b> : elle traverse les
 *       agregats, et vit dans les contraintes {@code UNIQUE} de la base.</li>
 *   <li><b>"Retirer FOR_RENT est refuse s'il existe des locations futures"</b>
 *       (roadmap §3.2) : l'agregat ne voit pas les locations, qui appartiennent
 *       a un autre contexte. Cette regle reviendra a un cas d'usage qui
 *       interroge {@code rental} ; elle est notee dans {@code BACKLOG.md}.</li>
 * </ul>
 *
 * <h2>Le tarif journalier, et pourquoi il est ici</h2>
 * Le tarif de location appartiendrait idealement a une grille tarifaire, pas
 * au vehicule. Il est porte ici par simplicite : un tarif par vehicule, fixe a
 * l'acquisition. Le contexte {@code rental} le lit par son propre catalogue,
 * sans dependre de cette classe. La separation en grille tarifaire est au
 * {@code BACKLOG.md}.
 */
public final class Vehicle extends AggregateRoot<VehicleId> {

    private final Vin vin;
    private final PlateNumber plate;
    private final VehicleModel model;
    private final Money dailyRentalRate;

    private Mileage mileage;
    private VehicleCondition condition;
    private VehicleStatus status;
    private final Set<Offering> offerings;

    private Vehicle(VehicleId id, Vin vin, PlateNumber plate, VehicleModel model, Money dailyRentalRate,
                    Mileage mileage, VehicleCondition condition, VehicleStatus status, Set<Offering> offerings) {
        super(id);
        this.vin = Objects.requireNonNull(vin, "Le VIN est obligatoire");
        this.plate = Objects.requireNonNull(plate, "L'immatriculation est obligatoire");
        this.model = Objects.requireNonNull(model, "Le modele est obligatoire");
        this.dailyRentalRate = Objects.requireNonNull(dailyRentalRate, "Le tarif journalier est obligatoire");
        this.mileage = Objects.requireNonNull(mileage, "Le kilometrage est obligatoire");
        this.condition = Objects.requireNonNull(condition, "L'etat est obligatoire");
        this.status = Objects.requireNonNull(status, "Le statut est obligatoire");
        // Copie defensive dans un EnumSet mutable interne : l'appelant garde sa
        // propre collection, et ne peut pas modifier celle de l'agregat.
        this.offerings = offerings.isEmpty() ? EnumSet.noneOf(Offering.class) : EnumSet.copyOf(offerings);
        BusinessRuleViolation.check(
                dailyRentalRate.isPositive(),
                "VEHICLE_DAILY_RATE_NOT_POSITIVE",
                "Le tarif journalier doit etre strictement positif : " + dailyRentalRate);
    }

    // =================================================================
    // Creation et reconstitution
    // =================================================================

    /**
     * Enregistre un vehicule qui vient d'etre achete. Il entre dans le parc en
     * {@link VehicleStatus#ACQUIRED} : il devra etre prepare puis mis en
     * service avant de pouvoir etre loue.
     *
     * @param offerings ce que l'agence compte en faire ; peut etre vide
     */
    public static Vehicle acquire(VehicleId id, Vin vin, PlateNumber plate, VehicleModel model,
                                  Money dailyRentalRate, Mileage mileage, VehicleCondition condition,
                                  Set<Offering> offerings, Clock clock) {
        Vehicle vehicle = new Vehicle(id, vin, plate, model, dailyRentalRate, mileage, condition,
                VehicleStatus.ACQUIRED, offerings);
        vehicle.registerEvent(new VehicleAddedToFleet(UUID.randomUUID(), clock.instant(), id));
        return vehicle;
    }

    /**
     * Reconstitue un vehicule relu depuis la persistance, sans evenement. Les
     * value objects se sont revalides a leur construction ; l'historique
     * (kilometrages successifs, transitions) n'est pas rejoue.
     */
    public static Vehicle restore(VehicleId id, Vin vin, PlateNumber plate, VehicleModel model,
                                  Money dailyRentalRate, Mileage mileage, VehicleCondition condition,
                                  VehicleStatus status, Set<Offering> offerings) {
        return new Vehicle(id, vin, plate, model, dailyRentalRate, mileage, condition, status, offerings);
    }

    // =================================================================
    // Cycle de vie
    // =================================================================

    /** Commence la preparation (nettoyage, controle, immatriculation). */
    public void startPreparation() {
        transitionTo(VehicleStatus.IN_PREPARATION);
    }

    /**
     * Met le vehicule en service : il rejoint le parc disponible.
     *
     * @throws BusinessRuleViolation si le vehicule est endommage
     */
    public void commission() {
        ensureNotDamaged();
        transitionTo(VehicleStatus.IN_FLEET);
    }

    /** Immobilise le vehicule pour entretien. */
    public void sendToMaintenance() {
        transitionTo(VehicleStatus.IN_MAINTENANCE);
    }

    /**
     * Sortie de maintenance : le vehicule revient dans le parc, repare. Un
     * vehicule endommage sort donc en etat {@link VehicleCondition#USED}.
     */
    public void completeMaintenance() {
        transitionTo(VehicleStatus.IN_FLEET);
        if (condition == VehicleCondition.DAMAGED) {
            condition = VehicleCondition.USED;
        }
    }

    /**
     * Signale un dommage. Un vehicule disponible part aussitot en maintenance :
     * il ne doit pas pouvoir etre loue dans cet etat. Signaler un dommage sur
     * un vehicule en preparation ou deja en maintenance ne change que son etat.
     */
    public void reportDamage() {
        BusinessRuleViolation.check(
                !status.isTerminal(),
                "VEHICLE_ALREADY_GONE",
                "Le vehicule " + id().value() + " a quitte le parc (" + status + ")");
        condition = VehicleCondition.DAMAGED;
        if (status == VehicleStatus.IN_FLEET) {
            transitionTo(VehicleStatus.IN_MAINTENANCE);
        }
    }

    /**
     * Vend le vehicule. Il quitte definitivement le parc et perd ses offres.
     *
     * @throws IllegalVehicleTransitionException hors de {@code IN_FLEET}, notamment en maintenance
     */
    public void sell(Clock clock) {
        transitionTo(VehicleStatus.SOLD);
        offerings.clear();
        registerEvent(new VehicleSold(UUID.randomUUID(), clock.instant(), id()));
    }

    /** Retire le vehicule du parc (fin de vie). Il perd ses offres. */
    public void retire(Clock clock) {
        transitionTo(VehicleStatus.RETIRED);
        offerings.clear();
        registerEvent(new VehicleWithdrawn(UUID.randomUUID(), clock.instant(), id()));
    }

    // =================================================================
    // Kilometrage
    // =================================================================

    /**
     * Saisie manuelle d'un nouveau releve, par un agent. Stricte : un releve
     * inferieur au precedent est une erreur de saisie ou une fraude, on la
     * refuse.
     *
     * @throws BusinessRuleViolation si le compteur recule
     */
    public void updateMileage(Mileage reading, Clock clock) {
        Objects.requireNonNull(reading);
        BusinessRuleViolation.check(
                !reading.isLowerThan(mileage),
                "VEHICLE_MILEAGE_DECREASED",
                "Le kilometrage ne peut pas reculer : " + reading + " < " + mileage);
        applyMileage(reading, clock);
    }

    /**
     * Releve transmis par le retour d'une location (evenement
     * {@code VehicleReturned}). <b>Idempotent</b>, contrairement a
     * {@link #updateMileage} :
     * <ul>
     *   <li>un releve egal au kilometrage actuel est ignore : c'est le meme
     *       evenement recu deux fois ;</li>
     *   <li>un releve inferieur est ignore aussi : c'est un evenement ancien
     *       arrive en retard, apres un releve plus recent. Le refuser ferait
     *       echouer le traitement de l'evenement, sans rien corriger.</li>
     * </ul>
     *
     * @return {@code true} si le kilometrage a effectivement change
     */
    public boolean recordReturnMileage(Mileage reading, Clock clock) {
        Objects.requireNonNull(reading);
        if (!reading.isGreaterThan(mileage)) {
            return false;
        }
        applyMileage(reading, clock);
        return true;
    }

    // =================================================================
    // Offres
    // =================================================================

    /**
     * Propose le vehicule pour cette offre. Sans effet si elle l'etait deja.
     *
     * @throws BusinessRuleViolation si le vehicule a quitte le parc
     */
    public void offer(Offering offering, Clock clock) {
        Objects.requireNonNull(offering);
        ensureStillInFleet();
        if (offerings.add(offering)) {
            registerEvent(new VehicleOfferingChanged(UUID.randomUUID(), clock.instant(), id(), offerings));
        }
    }

    /** Retire une offre. Sans effet si elle n'etait pas proposee. */
    public void withdrawOffering(Offering offering, Clock clock) {
        Objects.requireNonNull(offering);
        ensureStillInFleet();
        if (offerings.remove(offering)) {
            registerEvent(new VehicleOfferingChanged(UUID.randomUUID(), clock.instant(), id(), offerings));
        }
    }

    // =================================================================
    // Questions metier
    // =================================================================

    /**
     * Le vehicule peut-il partir en location ? Il doit etre dans le parc
     * disponible, en etat de rouler, et propose a la location.
     *
     * <p>Meme regle que la requete de disponibilite du contexte {@code rental}
     * ({@code JdbcVehicleCatalog}) ; si l'une change, l'autre doit suivre.
     */
    public boolean isRentable() {
        return status == VehicleStatus.IN_FLEET
                && condition != VehicleCondition.DAMAGED
                && offerings.contains(Offering.FOR_RENT);
    }

    /** Le vehicule est-il propose pour cette offre ? */
    public boolean isOfferedFor(Offering offering) {
        return offerings.contains(offering);
    }

    // =================================================================
    // Lecture de l'etat (mapper, vues)
    // =================================================================

    public Vin vin() {
        return vin;
    }

    public PlateNumber plate() {
        return plate;
    }

    public VehicleModel model() {
        return model;
    }

    public Money dailyRentalRate() {
        return dailyRentalRate;
    }

    public Mileage mileage() {
        return mileage;
    }

    public VehicleCondition condition() {
        return condition;
    }

    public VehicleStatus status() {
        return status;
    }

    /** Copie immuable : modifier les offres passe par {@link #offer} et {@link #withdrawOffering}. */
    public Set<Offering> offerings() {
        return Set.copyOf(offerings);
    }

    // =================================================================
    // Mecanique interne
    // =================================================================

    private void transitionTo(VehicleStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalVehicleTransitionException(id(), status, target);
        }
        status = target;
    }

    private void applyMileage(Mileage reading, Clock clock) {
        Mileage previous = mileage;
        mileage = reading;
        if (reading.isGreaterThan(previous)) {
            registerEvent(new VehicleMileageUpdated(UUID.randomUUID(), clock.instant(), id(),
                    previous.kilometers(), reading.kilometers()));
        }
    }

    private void ensureNotDamaged() {
        BusinessRuleViolation.check(
                condition != VehicleCondition.DAMAGED,
                "VEHICLE_DAMAGED",
                "Le vehicule " + id().value() + " est endommage : il doit passer en maintenance");
    }

    private void ensureStillInFleet() {
        BusinessRuleViolation.check(
                !status.isTerminal(),
                "VEHICLE_ALREADY_GONE",
                "Le vehicule " + id().value() + " a quitte le parc (" + status + ") : ses offres sont figees");
    }
}
