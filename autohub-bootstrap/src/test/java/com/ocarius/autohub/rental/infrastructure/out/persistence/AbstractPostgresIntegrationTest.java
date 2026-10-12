package com.ocarius.autohub.rental.infrastructure.out.persistence;

import com.ocarius.autohub.fleet.domain.model.VehicleId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Socle commun des tests d'integration de la persistance des locations.
 *
 * <h2>Un conteneur partage par toutes les classes de test</h2>
 * Le conteneur est demarre une seule fois, dans un bloc {@code static}, et
 * jamais arrete explicitement (Testcontainers le supprime a la fin de la JVM).
 * Avec {@code @Container} sur chaque classe, chacune paierait son propre
 * demarrage de PostgreSQL, et Spring ne pourrait pas reutiliser son contexte
 * en cache d'une classe a l'autre puisque l'URL de la base changerait.
 *
 * <h2>Pas de {@code @Transactional} sur les tests</h2>
 * Un test annote {@code @Transactional} s'execute dans une transaction annulee
 * a la fin. Pratique pour nettoyer, mais fatal ici : la contrainte d'exclusion
 * et le verrouillage optimiste ne se manifestent qu'entre transactions
 * <i>distinctes</i>. Chaque test ouvre donc ses transactions explicitement via
 * {@link #tx}, et isole ses donnees en creant son propre vehicule.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /** Ouvre une transaction par appel de {@code execute} : l'equivalent d'un cas d'usage. */
    @Autowired
    protected TransactionTemplate tx;

    /**
     * Insere un vehicule directement en SQL. L'agregat {@code Vehicle} n'existe
     * pas encore (week-end 3) ; seule la cle etrangere de {@code rental} a
     * besoin d'une ligne a referencer.
     *
     * @return l'identifiant du vehicule cree, unique a chaque appel
     */
    protected VehicleId insertVehicle() {
        VehicleId id = VehicleId.newId();
        // VIN et immatriculation derives de l'UUID pour rester uniques entre tests.
        String hex = id.value().toString().replace("-", "").toUpperCase();
        jdbc.update("""
                INSERT INTO vehicle (id, vin, plate_number, brand, model, model_year, mileage_km, status)
                VALUES (?, ?, ?, 'Peugeot', '208', 2024, 10000, 'IN_FLEET')
                """, id.value(), hex.substring(0, 17), hex.substring(0, 9));
        return id;
    }
}
