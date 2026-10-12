package com.ocarius.autohub.rental.infrastructure.out.persistence;

import com.ocarius.autohub.fleet.domain.model.VehicleId;
import com.ocarius.autohub.rental.domain.model.CustomerId;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.rental.domain.model.RentalStatus;
import com.ocarius.autohub.rental.domain.model.VehicleHandover;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.time.LocalDateTime;
import java.util.Currency;

/**
 * Traduction explicite entre l'agregat {@link Rental} et sa representation
 * {@link RentalJpaEntity}.
 *
 * <h2>Pourquoi un mapper ecrit a la main</h2>
 * Un outil de mapping automatique (MapStruct, ModelMapper) copierait les
 * champs par reflexion ou par convention de nommage, et donc contournerait
 * {@link Rental#restore} et ses verifications. Ici, chaque champ est converti
 * a la vue de tous, et la reconstruction passe <b>obligatoirement</b> par les
 * constructeurs des value objects (qui revalident leurs invariants) puis par
 * {@code Rental.restore} (qui verifie la coherence etat / donnees).
 *
 * <h2>Sens des dependances</h2>
 * Le mapper connait le domaine et l'entite ; le domaine ne connait ni l'un ni
 * l'autre. Aucune classe JPA ne peut fuir vers le domaine, parce que le module
 * {@code autohub-domain} ne voit meme pas {@code jakarta.persistence}.
 *
 * <p>Classe utilitaire sans etat : pas besoin d'en faire un bean Spring.
 */
final class RentalMapper {

    private RentalMapper() {
    }

    /**
     * Reconstitue un agregat depuis une ligne relue.
     *
     * @throws IllegalStateException       si la ligne est incoherente avec son statut
     * @throws IllegalArgumentException    si le statut stocke ne correspond a aucune constante
     */
    static Rental toDomain(RentalJpaEntity entity) {
        return Rental.restore(
                RentalId.of(entity.getId()),
                VehicleId.of(entity.getVehicleId()),
                CustomerId.of(entity.getCustomerId()),
                RentalPeriod.of(entity.getStartsAt(), entity.getEndsAt()),
                Money.of(entity.getQuotedAmount(), Currency.getInstance(entity.getQuotedCurrency())),
                RentalStatus.valueOf(entity.getStatus()),
                entity.getReservedUntil(),
                toHandover(entity.getPickupAt(), entity.getPickupMileageKm(), entity.getPickupFuelPercent()),
                toHandover(entity.getDropoffAt(), entity.getDropoffMileageKm(), entity.getDropoffFuelPercent()));
    }

    /**
     * Recopie l'etat de l'agregat dans une entite, nouvelle ou deja geree par
     * Hibernate.
     *
     * <p>On met a jour une entite existante plutot que d'en creer une neuve a
     * chaque sauvegarde : c'est ce qui preserve son champ {@code version}, et
     * donc le verrouillage optimiste.
     */
    static void copyToEntity(Rental rental, RentalJpaEntity entity) {
        entity.setVehicleId(rental.vehicleId().value());
        entity.setCustomerId(rental.customerId().value());
        entity.setStartsAt(rental.period().start());
        entity.setEndsAt(rental.period().end());
        entity.setStatus(rental.status().name());
        entity.setQuotedAmount(rental.quotedPrice().amount());
        entity.setQuotedCurrency(rental.quotedPrice().currency().getCurrencyCode());
        entity.setReservedUntil(rental.reservedUntil().orElse(null));

        VehicleHandover pickup = rental.pickup().orElse(null);
        entity.setPickupAt(pickup == null ? null : pickup.at());
        entity.setPickupMileageKm(pickup == null ? null : pickup.mileageKm());
        entity.setPickupFuelPercent(pickup == null ? null : (short) pickup.fuelLevelPercent());

        VehicleHandover dropoff = rental.dropoff().orElse(null);
        entity.setDropoffAt(dropoff == null ? null : dropoff.at());
        entity.setDropoffMileageKm(dropoff == null ? null : dropoff.mileageKm());
        entity.setDropoffFuelPercent(dropoff == null ? null : (short) dropoff.fuelLevelPercent());
    }

    /**
     * Reconstruit un etat des lieux a partir de ses trois colonnes. La
     * contrainte CHECK de la table garantit qu'elles sont toutes nulles ou
     * toutes renseignees.
     */
    private static VehicleHandover toHandover(LocalDateTime at, Long mileageKm, Short fuelPercent) {
        if (at == null) {
            return null;
        }
        return new VehicleHandover(at, mileageKm, fuelPercent);
    }
}
