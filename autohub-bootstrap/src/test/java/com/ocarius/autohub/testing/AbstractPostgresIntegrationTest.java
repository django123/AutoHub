package com.ocarius.autohub.testing;

import com.ocarius.autohub.shared.domain.VehicleId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Socle commun des tests d'integration contre PostgreSQL.
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
 * a la fin. Pratique pour nettoyer, mais fatal ici : la contrainte d'exclusion,
 * le verrouillage optimiste et les evenements delivres apres commit ne se
 * manifestent qu'entre transactions <i>distinctes</i>, reellement validees.
 * Chaque test ouvre donc ses transactions explicitement via {@link #tx}, et
 * isole ses donnees en creant son propre vehicule.
 *
 * <h2>Un seul contexte Spring pour tous</h2>
 * Environnement web simule ({@code MockMvc}) plutot qu'aucun : les tests HTTP
 * et les tests de persistance partagent ainsi la meme configuration, donc le
 * meme contexte en cache, demarre une seule fois.
 *
 * <h2>Traitement d'expiration coupe</h2>
 * {@code autohub.rental.expiry.enabled=false} : le traitement planifie ne doit
 * pas faire expirer des reservations dans le dos d'un test.
 */
@SpringBootTest(properties = "autohub.rental.expiry.enabled=false")
@AutoConfigureMockMvc
@Import(AbstractPostgresIntegrationTest.TestClockConfiguration.class)
public abstract class AbstractPostgresIntegrationTest {

    /**
     * Remplace l'horloge de l'application par une horloge pilotable, demarree le
     * 1er mars 2027 a 10:00 (Paris). Les cas d'usage l'injectent par type : le
     * bean {@code @Primary} l'emporte sur celui de {@code ClockConfiguration}.
     *
     * <p>Partagee par tous les tests du contexte : un test qui la fait avancer
     * le fait pour les suivants. Les tests qui en dependent raisonnent donc en
     * durees relatives ("31 minutes apres ma reservation"), jamais en dates
     * absolues de l'horloge.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2027-03-01T09:00:00Z"), ZoneId.of("Europe/Paris"));
        }
    }

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /** Lettres autorisees dans une immatriculation SIV (sans I, O, U). */
    private static final String PLATE_LETTERS = "ABCDEFGHJKLMNPQRSTVWXYZ";

    @Autowired
    protected JdbcTemplate jdbc;

    /** L'horloge de l'application, a faire avancer pour simuler le temps qui passe. */
    @Autowired
    protected MutableClock clock;

    /** Ouvre une transaction par appel de {@code execute} : l'equivalent d'un cas d'usage. */
    @Autowired
    protected TransactionTemplate tx;

    /**
     * Insere directement en SQL un vehicule louable (en service, propose a la
     * location, 50 EUR par jour). Plus rapide que de passer par les cas d'usage
     * de {@code fleet}, quand le test ne porte pas sur eux.
     *
     * @return l'identifiant du vehicule cree, unique a chaque appel
     */
    protected VehicleId insertVehicle() {
        VehicleId id = VehicleId.newId();
        jdbc.update("""
                INSERT INTO vehicle (id, vin, plate_number, brand, model, model_year, mileage_km, status,
                                     condition, for_rent, for_sale, daily_rate_amount, daily_rate_currency)
                VALUES (?, ?, ?, 'Peugeot', '208', 2024, 10000, 'IN_FLEET', 'USED', true, false, 50, 'EUR')
                """, id.value(), randomVin(id), randomPlate());
        return id;
    }

    /**
     * VIN derive de l'UUID : 17 caracteres hexadecimaux, donc chiffres et
     * lettres A-F, toutes autorisees. Unique comme l'UUID.
     */
    protected static String randomVin(VehicleId id) {
        return id.value().toString().replace("-", "").substring(0, 17).toUpperCase();
    }

    /**
     * Immatriculation valide tiree au hasard. 23^4 x 999 combinaisons : une
     * collision entre deux tests est negligeable.
     */
    protected static String randomPlate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder plate = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i == 2) {
                plate.append('-').append(String.format("%03d", random.nextInt(1, 1000))).append('-');
            }
            plate.append(PLATE_LETTERS.charAt(random.nextInt(PLATE_LETTERS.length())));
        }
        return plate.toString();
    }
}
