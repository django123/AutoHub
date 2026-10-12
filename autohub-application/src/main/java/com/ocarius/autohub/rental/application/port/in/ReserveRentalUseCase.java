package com.ocarius.autohub.rental.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : reserver un vehicule sur une periode.
 *
 * <p>Invariant garanti : le vehicule existe, est louable, et n'est occupe par
 * aucune autre location bloquante sur la periode. Le prix est calcule et fige
 * au moment de la reservation, qui expire si elle n'est pas confirmee dans les
 * 30 minutes.
 *
 * <h2>Pourquoi un identifiant en retour, et pas la location</h2>
 * Une commande renvoie le minimum : l'identifiant de ce qu'elle a cree.
 * Renvoyer l'agregat exposerait le domaine a l'appelant ; renvoyer une vue
 * melangerait commande et lecture. L'appelant qui veut afficher la location
 * la relit par {@link GetRentalQuery}.
 */
public interface ReserveRentalUseCase {

    /**
     * @return l'identifiant de la location creee
     * @throws com.ocarius.autohub.rental.domain.exception.UnknownVehicleException      vehicule inconnu
     * @throws com.ocarius.autohub.rental.domain.exception.VehicleNotRentableException  vehicule non louable
     * @throws com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException periode deja prise
     */
    UUID reserve(ReserveRentalCommand command);
}
