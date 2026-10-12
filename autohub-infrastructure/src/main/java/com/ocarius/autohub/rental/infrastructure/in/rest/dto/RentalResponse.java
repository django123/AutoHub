package com.ocarius.autohub.rental.infrastructure.in.rest.dto;

import com.ocarius.autohub.rental.application.port.in.RentalView;
import com.ocarius.autohub.shared.application.view.MoneyView;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reponse JSON d'une location.
 *
 * <p>Distincte de {@link RentalView} : la vue est le contrat de la couche
 * application, cette classe est le contrat de l'API HTTP. Aujourd'hui elles se
 * ressemblent ; le jour ou l'API devra rester stable pendant que l'application
 * evolue (ou l'inverse), c'est ici qu'on adaptera, sans toucher au cas d'usage.
 *
 * @param id                identifiant
 * @param vehicleId         vehicule
 * @param customerId        client
 * @param start             debut
 * @param end               fin prevue
 * @param status            statut
 * @param dailyRate         tarif journalier fige
 * @param quotedPrice       prix fige
 * @param lateReturnPenalty penalite de retard
 * @param totalDue          total du
 * @param reservedUntil     echeance de la reservation, ou absente
 * @param pickup            etat des lieux de depart, ou absent
 * @param dropoff           etat des lieux de retour, ou absent
 */
public record RentalResponse(UUID id, UUID vehicleId, UUID customerId, LocalDateTime start, LocalDateTime end,
                             String status, MoneyView dailyRate, MoneyView quotedPrice, MoneyView lateReturnPenalty,
                             MoneyView totalDue, Instant reservedUntil, Handover pickup, Handover dropoff) {

    /** Construit la reponse depuis la vue applicative. */
    public static RentalResponse from(RentalView view) {
        return new RentalResponse(view.id(), view.vehicleId(), view.customerId(), view.start(), view.end(),
                view.status(), view.dailyRate(), view.quotedPrice(), view.lateReturnPenalty(), view.totalDue(),
                view.reservedUntil(), Handover.from(view.pickup()), Handover.from(view.dropoff()));
    }

    /**
     * Etat des lieux en JSON.
     *
     * @param at               date-heure
     * @param mileageKm        kilometrage
     * @param fuelLevelPercent carburant
     */
    public record Handover(LocalDateTime at, long mileageKm, int fuelLevelPercent) {

        static Handover from(RentalView.HandoverView view) {
            return view == null ? null : new Handover(view.at(), view.mileageKm(), view.fuelLevelPercent());
        }
    }
}
