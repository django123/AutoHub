package com.ocarius.autohub.rental.application.port.out;

import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PORT SORTANT : ce que le contexte {@code rental} a besoin de savoir des
 * vehicules du parc.
 *
 * <h2>Une couche anti-corruption</h2>
 * Le contexte {@code rental} ne depend pas de {@code fleet} (ADR 0004) : il
 * n'importe ni {@code Vehicle} ni {@code VehicleRepository}. Il declare ici,
 * dans son propre vocabulaire, la vue reduite dont il a besoin : un vehicule
 * est-il louable, a quel tarif, et comment le presenter. L'adaptateur
 * ({@code JdbcVehicleCatalog}) lit la table {@code vehicle} en SQL.
 *
 * <p>Le couplage reste reel, mais il est concentre dans un seul adaptateur et
 * porte sur des donnees, pas sur du code. En microservices, cet adaptateur
 * deviendrait un appel HTTP ou une copie locale alimentee par les evenements
 * de {@code fleet} ; ce port ne changerait pas.
 */
public interface VehicleCatalog {

    /** Le vehicule, louable ou non ; vide s'il n'existe pas. */
    Optional<CatalogVehicle> findById(VehicleId vehicleId);

    /**
     * Vehicules louables et libres sur toute la periode, au moment {@code now}.
     *
     * <p>Une reservation echue mais pas encore expiree ne rend pas le vehicule
     * indisponible : elle sera expiree a la reservation (voir
     * {@link RentalRepository#findOverdueReservationsOverlapping}).
     */
    List<CatalogVehicle> findAvailable(RentalPeriod period, Instant now);

    /**
     * Vue d'un vehicule pour le contexte {@code rental}.
     *
     * @param id          identifiant partage
     * @param description libelle lisible : modele et immatriculation
     * @param dailyRate   tarif journalier de location
     * @param rentable    le vehicule peut-il partir en location aujourd'hui ?
     */
    record CatalogVehicle(VehicleId id, String description, Money dailyRate, boolean rentable) {

        /** Refuse les champs absents. */
        public CatalogVehicle {
            Objects.requireNonNull(id);
            Objects.requireNonNull(description);
            Objects.requireNonNull(dailyRate);
        }
    }
}
