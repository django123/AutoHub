package com.ocarius.autohub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'integration de bout en bout : demarrage de l'application contre un
 * PostgreSQL reel.
 *
 * <h2>Pourquoi Testcontainers et pas H2</h2>
 * H2 est plus rapide a demarrer, et c'est son seul avantage. Il ne connait ni
 * {@code tstzrange}, ni {@code EXCLUDE USING gist}, ni {@code JSONB} --
 * c'est-a-dire exactement les fonctionnalites sur lesquelles repose ce projet.
 * Tester sur un moteur different de celui de production, c'est tester autre
 * chose que ce qu'on livre.
 *
 * <p>Testcontainers demarre un vrai PostgreSQL dans Docker, applique les
 * migrations Flyway, et detruit le conteneur a la fin. Environ 5 secondes de
 * surcout au premier lancement -- le prix d'une confiance reelle.
 *
 * <h2>Le role de {@code @ServiceConnection}</h2>
 * Cette annotation (Spring Boot 3.1+) cable automatiquement l'URL, le login et
 * le mot de passe du conteneur vers le {@code DataSource}. Elle remplace le
 * bloc {@code @DynamicPropertySource} qu'on ecrivait auparavant a la main.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
// Spring Boot DESACTIVE l'export de metriques dans les tests : sans cela, chaque
// @SpringBootTest du projet demarrerait des exporteurs dont il n'a que faire.
// La consequence est deroutante quand on ne la connait pas : /actuator/prometheus
// repond 200 quand on lance l'application, et 404 sous @SpringBootTest, a
// configuration et classpath rigoureusement identiques. Cette annotation reactive
// l'export, pour ce test qui veut precisement le verifier.
@AutoConfigureObservability
class ApplicationSmokeTest {

    /**
     * {@code static} : un seul conteneur pour toute la classe de test.
     * En le rendant non statique, on paierait un demarrage par methode.
     * Epingler la version (16-alpine) et ne jamais utiliser "latest" :
     * une build doit etre reproductible dans six mois.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Le contexte Spring demarre et se connecte a PostgreSQL")
    void le_contexte_demarre() {
        assertThat(dataSource).isNotNull();
        assertThat(POSTGRES.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Flyway a applique la migration V1 et installe les extensions")
    void flyway_a_applique_les_migrations() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // La table temoin creee par V1__baseline.sql
            ResultSet smoke = statement.executeQuery(
                    "SELECT count(*) FROM flyway_smoke_check");
            smoke.next();
            assertThat(smoke.getInt(1)).isEqualTo(1);

            // btree_gist : indispensable a la contrainte d'exclusion du WE 5.
            // Le verifier maintenant evite de decouvrir son absence dans trois
            // semaines, au moment ou la migration critique echouera.
            ResultSet extension = statement.executeQuery(
                    "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'");
            extension.next();
            assertThat(extension.getInt(1))
                    .as("l'extension btree_gist doit etre installee")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("L'endpoint de sante repond UP")
    void actuator_health_repond_up() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    @DisplayName("Une route inconnue repond 404, pas 500")
    void route_inconnue_repond_404() {
        // Regression : tant que GlobalExceptionHandler n'etendait pas
        // ResponseEntityExceptionHandler, son @ExceptionHandler(Exception.class)
        // interceptait la NoResourceFoundException levee par Spring MVC -- qui
        // porte pourtant deja son statut 404 -- et la renvoyait en 500, avec une
        // pile d'appels journalisee en niveau ERROR. Au week-end 6, la meme
        // mecanique aurait transforme les erreurs de validation en 500 opaques.
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/route/inexistante", String.class);

        assertThat(response.getStatusCode().value())
                .as("Spring doit garder la main sur ses propres exceptions")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("L'endpoint Prometheus expose reellement des metriques")
    void actuator_prometheus_expose_des_metriques() {
        // Regression : application.yml annoncait "prometheus" dans sa liste
        // d'exposition et ops/prometheus.yml le scrutait toutes les 15 secondes,
        // mais aucun registre Micrometer n'etait declare -- l'endpoint n'existait
        // pas. Une configuration qui promet ce qu'elle ne fournit pas ne se voit
        // qu'en executant l'application ; ce test la rend visible au build.
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode().value())
                .as("reponse recue : %s", response.getBody())
                .isEqualTo(200);
        assertThat(response.getBody())
                .as("le tag application vient de management.metrics.tags dans application.yml")
                .contains("application=\"autohub\"");
    }
}
