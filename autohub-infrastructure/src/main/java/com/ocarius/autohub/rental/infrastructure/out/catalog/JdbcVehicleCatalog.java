package com.ocarius.autohub.rental.infrastructure.out.catalog;

import com.ocarius.autohub.rental.application.port.out.VehicleCatalog;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.shared.domain.vo.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ADAPTATEUR SORTANT : le catalogue de location, lu directement dans la table
 * {@code vehicle} en SQL.
 *
 * <h2>Pourquoi du SQL et pas le repository de fleet</h2>
 * Le contexte {@code rental} ne depend pas de {@code fleet} (ADR 0004) : cette
 * classe n'importe aucune classe {@code fleet}. Elle lit les colonnes dont
 * {@code rental} a besoin et les traduit dans son vocabulaire
 * ({@link CatalogVehicle}). C'est une couche anti-corruption : si le schema de
 * {@code vehicle} change, seul ce fichier est touche.
 *
 * <h2>Deux regles dupliquees, a garder alignees</h2>
 * <ul>
 *   <li>"louable" = {@code IN_FLEET}, non endommage, propose a la location :
 *       meme regle que {@code Vehicle.isRentable()} ;</li>
 *   <li>"bloquant" = {@code RESERVED}, {@code CONFIRMED}, {@code PICKED_UP} :
 *       meme liste que la contrainte {@code rental_no_overlap} et que
 *       {@code RentalStatus.blockingStatuses()}.</li>
 * </ul>
 * La duplication est le prix du decouplage. {@code RentalHttpJourneyTest}
 * verifie le comportement de bout en bout contre PostgreSQL.
 */
@Component
class JdbcVehicleCatalog implements VehicleCatalog {

    private static final String COLUMNS = """
            v.id, v.brand, v.model, v.model_year, v.plate_number, v.daily_rate_amount, v.daily_rate_currency,
            (v.status = 'IN_FLEET' AND v.condition <> 'DAMAGED' AND v.for_rent) AS rentable
            """;

    private static final RowMapper<CatalogVehicle> ROW_MAPPER = (rs, rowNum) -> new CatalogVehicle(
            VehicleId.of(rs.getObject("id", UUID.class)),
            rs.getString("brand") + " " + rs.getString("model") + " (" + rs.getInt("model_year") + ") "
                    + rs.getString("plate_number"),
            Money.of(rs.getBigDecimal("daily_rate_amount"), Currency.getInstance(rs.getString("daily_rate_currency"))),
            rs.getBoolean("rentable"));

    private final JdbcTemplate jdbc;

    JdbcVehicleCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@inheritDoc} Lecture par cle primaire. */
    @Override
    public Optional<CatalogVehicle> findById(VehicleId vehicleId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM vehicle v WHERE v.id = ?", ROW_MAPPER, vehicleId.value())
                .stream().findFirst();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Le {@code NOT EXISTS} reprend exactement la definition de la
     * contrainte d'exclusion (meme vehicule, plages qui se chevauchent, statut
     * bloquant), en ignorant les reservations echues. Il est servi par l'index
     * GiST de {@code rental_no_overlap}.
     */
    @Override
    public List<CatalogVehicle> findAvailable(RentalPeriod period, Instant now) {
        return jdbc.query("SELECT " + COLUMNS + """
                  FROM vehicle v
                 WHERE v.status = 'IN_FLEET' AND v.condition <> 'DAMAGED' AND v.for_rent
                   AND NOT EXISTS (
                       SELECT 1 FROM rental r
                        WHERE r.vehicle_id = v.id
                          AND r.status IN ('RESERVED', 'CONFIRMED', 'PICKED_UP')
                          AND r.period && tsrange(CAST(? AS timestamp), CAST(? AS timestamp), '[)')
                          AND NOT (r.status = 'RESERVED' AND r.reserved_until <= CAST(? AS timestamptz)))
                 ORDER BY v.brand, v.model, v.plate_number
                """, ROW_MAPPER, period.start(), period.end(), OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
