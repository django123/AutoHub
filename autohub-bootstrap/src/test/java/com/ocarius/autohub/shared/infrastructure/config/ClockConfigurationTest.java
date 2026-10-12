package com.ocarius.autohub.shared.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link ClockConfiguration}.
 *
 * <p>{@link ApplicationContextRunner} demarre un contexte Spring minimal,
 * reduit a cette seule configuration : ni base, ni serveur web, quelques
 * millisecondes. C'est l'outil adapte pour verifier un bean de configuration
 * sans payer un {@code @SpringBootTest} complet.
 */
class ClockConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfiguration.class);

    @Test
    @DisplayName("sans configuration, l'horloge est dans le fuseau Europe/Paris")
    void fuseau_par_defaut() {
        runner.run(context -> assertThat(context.getBean(Clock.class).getZone())
                .isEqualTo(ZoneId.of("Europe/Paris")));
    }

    @Test
    @DisplayName("* le fuseau ne depend pas du fuseau de la JVM")
    void independant_du_fuseau_de_la_jvm() {
        // La JVM de test est en Europe/Paris (pom.xml) : le test precedent
        // passerait aussi avec Clock.systemDefaultZone(). Celui-ci verifie que
        // le fuseau vient bien de la configuration, en demandant un fuseau
        // different de celui de la JVM.
        runner.withPropertyValues("autohub.agency.time-zone=Indian/Reunion")
                .run(context -> assertThat(context.getBean(Clock.class).getZone())
                        .isEqualTo(ZoneId.of("Indian/Reunion"))
                        .isNotEqualTo(ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("* un fuseau inconnu empeche l'application de demarrer")
    void fuseau_inconnu() {
        // Mieux vaut un demarrage refuse qu'une horloge silencieusement en UTC.
        runner.withPropertyValues("autohub.agency.time-zone=Europe/Atlantide")
                .run(context -> assertThat(context).hasFailed());
    }
}
