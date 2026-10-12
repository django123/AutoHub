package com.ocarius.autohub.rental.infrastructure.in.scheduling;

import com.ocarius.autohub.rental.application.port.in.ExpireOverdueReservationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ADAPTATEUR ENTRANT : declenche periodiquement l'expiration des reservations
 * echues.
 *
 * <h2>Le role exact de ce traitement (bloc 6.2)</h2>
 * Il ne garantit <b>pas</b> qu'un creneau se libere a la seconde : une
 * reservation echue reste {@code RESERVED} en base jusqu'a son passage, et
 * bloquerait la contrainte d'exclusion. Ce trou est bouche par le cas d'usage
 * de reservation, qui expire lui-meme les reservations echues qui le genent.
 * Le traitement sert a tout le reste : faire passer le statut affiche a
 * {@code EXPIRED}, publier {@code RentalExpired}, et liberer les creneaux que
 * personne ne demande.
 *
 * <p>{@code fixedDelay} plutot que {@code fixedRate} : le passage suivant part
 * une fois le precedent termine, jamais en parallele de lui. Avec plusieurs
 * instances de l'application, chacune executera le traitement : c'est
 * correct (le cas d'usage est idempotent, et un conflit de version est
 * ignore), au prix d'un travail en double.
 *
 * <p>Desactivable par {@code autohub.rental.expiry.enabled=false} : les tests
 * d'integration qui pilotent le temps le coupent, pour qu'il n'agisse pas dans
 * leur dos.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "autohub.rental.expiry.enabled", havingValue = "true", matchIfMissing = true)
class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ExpireOverdueReservationsUseCase expireOverdueReservations;

    ReservationExpiryScheduler(ExpireOverdueReservationsUseCase expireOverdueReservations) {
        this.expireOverdueReservations = expireOverdueReservations;
    }

    /**
     * Un passage. Une exception est journalisee et n'arrete pas les passages
     * suivants (comportement par defaut du planificateur Spring).
     */
    @Scheduled(fixedDelayString = "${autohub.rental.expiry.interval:PT1M}",
            initialDelayString = "${autohub.rental.expiry.interval:PT1M}")
    void expireOverdueReservations() {
        int expired = expireOverdueReservations.expireOverdueReservations();
        if (expired > 0) {
            log.info("{} reservation(s) echue(s) expiree(s)", expired);
        }
    }
}
