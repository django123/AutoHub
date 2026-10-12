package com.ocarius.autohub.rental.application.port.in;

import java.util.UUID;

/**
 * PORT ENTRANT : enregistrer le retour du vehicule.
 *
 * <p>Invariant garanti : le compteur ne recule pas ; un retour tardif entraine
 * une penalite, un retour anticipe aucun remboursement. Le kilometrage du
 * vehicule est mis a jour dans le contexte {@code fleet} par evenement, apres
 * le commit (ADR 0004).
 */
public interface ReturnVehicleUseCase {

    /**
     * @param rentalId la location
     * @param handover etat des lieux de retour
     */
    void returnVehicle(UUID rentalId, HandoverCommand handover);
}
