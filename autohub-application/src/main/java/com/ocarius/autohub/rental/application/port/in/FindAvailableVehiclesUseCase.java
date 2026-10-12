package com.ocarius.autohub.rental.application.port.in;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PORT ENTRANT (lecture) : chercher les vehicules libres sur une periode, avec
 * leur devis.
 *
 * <p>Invariant garanti : chaque vehicule renvoye est louable et n'a aucune
 * location bloquante non echue sur la periode. Le devis est indicatif : le
 * prix n'est fige qu'a la reservation.
 *
 * <p>Ni pagination, ni filtre, ni tri : coupe assumee (session WE6), notee au
 * {@code BACKLOG.md}.
 */
public interface FindAvailableVehiclesUseCase {

    /**
     * @param start debut souhaite (inclus)
     * @param end   fin souhaitee (exclue)
     */
    List<AvailableVehicleView> findAvailable(LocalDateTime start, LocalDateTime end);
}
