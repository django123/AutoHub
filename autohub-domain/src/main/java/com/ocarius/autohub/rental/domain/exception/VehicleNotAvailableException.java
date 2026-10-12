package com.ocarius.autohub.rental.domain.exception;

import com.ocarius.autohub.fleet.domain.model.VehicleId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.shared.domain.DomainException;

/**
 * Le vehicule est deja occupe par une autre location sur une periode qui
 * chevauche celle demandee.
 *
 * <h2>Une exception du domaine que le domaine ne leve jamais</h2>
 * C'est le point le plus contre-intuitif du sprint. La non-superposition des
 * locations n'est <b>pas</b> un invariant de l'agregat {@code Rental} : une
 * instance de {@code Rental} ne voit pas les autres, et deux threads qui
 * creent chacun la leur en meme temps n'ont aucun moyen de se connaitre.
 * L'invariant est garanti par la contrainte d'exclusion PostgreSQL.
 *
 * <p>Pourtant l'exception est declaree ici, dans le domaine : "vehicule
 * indisponible" est du vocabulaire metier, et la couche application doit
 * pouvoir l'attraper sans savoir qu'une base de donnees existe. C'est
 * l'adaptateur de persistance qui la <b>leve</b>, en traduisant le SQLState
 * {@code 23P01} (exclusion_violation) de PostgreSQL.
 */
public class VehicleNotAvailableException extends DomainException {

    /**
     * @param vehicleId le vehicule demande
     * @param period    la periode demandee
     * @param cause     l'exception technique d'origine, conservee pour le diagnostic
     */
    public VehicleNotAvailableException(VehicleId vehicleId, RentalPeriod period, Throwable cause) {
        super("VEHICLE_NOT_AVAILABLE",
                "Le vehicule " + vehicleId.value() + " est deja loue sur une periode qui chevauche " + period);
        initCause(cause);
    }
}
