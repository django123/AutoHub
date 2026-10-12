package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Etat des lieux releve lors d'une remise de vehicule : au depart (pick-up)
 * ou au retour (drop-off).
 *
 * <h2>Pourquoi un value object, et pas une entite interne</h2>
 * Un etat des lieux est un <b>constat fige</b> : une fois signe, on ne le
 * modifie pas, on en etablit un autre. Il n'a pas de cycle de vie propre et
 * personne ne le designe par un identifiant ("l'etat des lieux n°42") en
 * dehors de sa location. Deux constats identiques (meme heure, meme
 * kilometrage, meme carburant) sont interchangeables : c'est la definition
 * d'un value object.
 *
 * <p>Si un jour l'etat des lieux recoit des photos, des dommages annotes et une
 * signature qu'on corrige apres coup, il gagnera une identite et deviendra une
 * entite interne a l'agregat. Ce n'est pas le cas aujourd'hui.
 *
 * @param at                 date-heure nominale de la remise, a l'agence
 * @param mileageKm          kilometrage releve au compteur, en km
 * @param fuelLevelPercent   niveau de carburant, de 0 a 100
 */
public record VehicleHandover(LocalDateTime at, long mileageKm, int fuelLevelPercent) {

    /** Verifie que le constat est physiquement plausible. */
    public VehicleHandover {
        Objects.requireNonNull(at, "La date de remise est obligatoire");
        BusinessRuleViolation.check(
                mileageKm >= 0,
                "HANDOVER_MILEAGE_NEGATIVE",
                "Le kilometrage ne peut pas etre negatif : " + mileageKm);
        BusinessRuleViolation.check(
                fuelLevelPercent >= 0 && fuelLevelPercent <= 100,
                "HANDOVER_FUEL_LEVEL_INVALID",
                "Le niveau de carburant s'exprime entre 0 et 100 % : " + fuelLevelPercent);
    }

    /**
     * Le compteur de ce constat est-il en recul par rapport a un constat anterieur ?
     * Un compteur qui recule signale une fraude ou une erreur de saisie.
     */
    public boolean hasLowerMileageThan(VehicleHandover earlier) {
        Objects.requireNonNull(earlier);
        return mileageKm < earlier.mileageKm;
    }

    /** Ce constat est-il date strictement avant l'autre ? */
    public boolean happenedBefore(VehicleHandover other) {
        Objects.requireNonNull(other);
        return at.isBefore(other.at);
    }

    /** Distance parcourue depuis un constat anterieur, en km. */
    public long kilometersSince(VehicleHandover earlier) {
        Objects.requireNonNull(earlier);
        return mileageKm - earlier.mileageKm;
    }
}
