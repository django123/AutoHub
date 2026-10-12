package com.ocarius.autohub.rental.application.port.in;

import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.VehicleHandover;
import com.ocarius.autohub.shared.application.view.MoneyView;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vue en lecture d'une location, en types standard.
 *
 * @param id                identifiant
 * @param vehicleId         vehicule loue
 * @param customerId        client
 * @param start             debut (inclus)
 * @param end               fin prevue (exclue)
 * @param status            statut, nom de la constante du domaine
 * @param dailyRate         tarif journalier fige
 * @param quotedPrice       prix fige de la periode
 * @param lateReturnPenalty penalite de retard (zero sinon)
 * @param totalDue          montant total du
 * @param reservedUntil     echeance de la reservation, {@code null} hors {@code RESERVED}
 * @param pickup            etat des lieux de depart, ou {@code null}
 * @param dropoff           etat des lieux de retour, ou {@code null}
 */
public record RentalView(UUID id, UUID vehicleId, UUID customerId, LocalDateTime start, LocalDateTime end,
                         String status, MoneyView dailyRate, MoneyView quotedPrice, MoneyView lateReturnPenalty,
                         MoneyView totalDue, Instant reservedUntil, HandoverView pickup, HandoverView dropoff) {

    /**
     * Construit la vue depuis l'agregat. C'est le seul endroit ou la couche
     * application lit l'etat de {@code Rental} pour le restituer.
     */
    public static RentalView from(Rental rental) {
        return new RentalView(
                rental.id().value(),
                rental.vehicleId().value(),
                rental.customerId().value(),
                rental.period().start(),
                rental.period().end(),
                rental.status().name(),
                MoneyView.of(rental.dailyRate()),
                MoneyView.of(rental.quotedPrice()),
                MoneyView.of(rental.lateReturnPenalty()),
                MoneyView.of(rental.totalDue()),
                rental.reservedUntil().orElse(null),
                rental.pickup().map(HandoverView::of).orElse(null),
                rental.dropoff().map(HandoverView::of).orElse(null));
    }

    /**
     * Etat des lieux en lecture.
     *
     * @param at               date-heure de la remise
     * @param mileageKm        kilometrage
     * @param fuelLevelPercent carburant
     */
    public record HandoverView(LocalDateTime at, long mileageKm, int fuelLevelPercent) {

        static HandoverView of(VehicleHandover handover) {
            return new HandoverView(handover.at(), handover.mileageKm(), handover.fuelLevelPercent());
        }
    }
}
