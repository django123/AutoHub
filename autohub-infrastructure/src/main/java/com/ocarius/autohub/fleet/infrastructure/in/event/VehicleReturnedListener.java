package com.ocarius.autohub.fleet.infrastructure.in.event;

import com.ocarius.autohub.fleet.application.port.in.RecordReturnMileageUseCase;
import com.ocarius.autohub.rental.domain.event.VehicleReturned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ADAPTATEUR ENTRANT du contexte {@code fleet} : reagit au retour d'un vehicule
 * en mettant a jour son kilometrage (option (d) de l'ADR 0004).
 *
 * <h2>Apres le commit, dans sa propre transaction</h2>
 * {@code AFTER_COMMIT} : le kilometrage n'est mis a jour que si le retour est
 * effectivement enregistre. {@code REQUIRES_NEW} est indispensable : a ce
 * stade, la transaction de {@code rental} est validee mais ses ressources sont
 * encore liees au thread ; sans transaction neuve, l'ecriture du vehicule
 * rejoindrait une transaction deja terminee et ne serait jamais validee.
 *
 * <h2>Ce qui se passe si la mise a jour echoue</h2>
 * Le retour du vehicule <b>n'est pas annule</b> : il est deja valide, et c'est
 * le fait metier qui compte. Le kilometrage du vehicule reste en retard sur la
 * realite, jusqu'au prochain retour (dont le releve, plus eleve, le
 * corrigera) ou a une correction manuelle. C'est la perte acceptee par l'ADR
 * 0004. L'echec est journalise en ERROR avec de quoi rejouer la mise a jour a
 * la main : il ne doit pas etre silencieux.
 *
 * <h2>Dependance vers rental</h2>
 * Cette classe importe {@code rental.domain.event.VehicleReturned} : c'est la
 * seule dependance de {@code fleet} vers {@code rental} que la regle ArchUnit
 * autorise. Un evenement est un contrat publie ; en microservices, ce serait un
 * message dont {@code fleet} connaitrait le schema.
 */
@Component
class VehicleReturnedListener {

    private static final Logger log = LoggerFactory.getLogger(VehicleReturnedListener.class);

    private final RecordReturnMileageUseCase recordReturnMileage;

    VehicleReturnedListener(RecordReturnMileageUseCase recordReturnMileage) {
        this.recordReturnMileage = recordReturnMileage;
    }

    /**
     * Reporte le kilometrage releve au retour sur le vehicule. Idempotent : un
     * evenement rejoue ou arrive en retard ne change rien (voir
     * {@code Vehicle.recordReturnMileage}).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(VehicleReturned event) {
        try {
            boolean changed = recordReturnMileage.recordReturnMileage(event.vehicleId().value(), event.mileageKm());
            log.info("Kilometrage du vehicule {} {} ({} km, evenement {})", event.vehicleId().value(),
                    changed ? "mis a jour" : "deja a jour", event.mileageKm(), event.eventId());
        } catch (RuntimeException e) {
            // Spring journaliserait l'exception sans contexte metier : on le
            // fait ici, avec tout ce qu'il faut pour rejouer la mise a jour.
            log.error("ECHEC de la mise a jour du kilometrage : vehicule={} kilometrage={} evenement={}",
                    event.vehicleId().value(), event.mileageKm(), event.eventId(), e);
            throw e;
        }
    }
}
