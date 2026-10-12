package com.ocarius.autohub.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Fournit l'unique {@link Clock} de l'application, dans le fuseau de l'agence.
 *
 * <h2>Pourquoi un Clock injecte</h2>
 * Le domaine ne lit jamais l'horloge systeme : chaque methode de {@code Rental}
 * qui a besoin de "maintenant" recoit un {@code Clock}. En test, on lui passe une
 * horloge figee ; en production, c'est ce bean que les cas d'usage injecteront.
 *
 * <h2>Pourquoi le fuseau de l'agence, et surtout pas UTC</h2>
 * Le fuseau d'un {@code Clock} ne change rien a {@code clock.instant()}, mais il
 * decide du resultat de {@code LocalDateTime.now(clock)}. Or le domaine raisonne
 * en heures <b>nominales</b> a l'agence (ADR 0002) : une location "de 08:00"
 * commence a 08:00 heure de Paris. Avec {@code Clock.systemUTC()}, "maintenant"
 * serait en retard d'une heure en hiver et de deux en ete, et le preavis
 * d'une heure ({@code RentalPeriod.respectsMinimumNoticeFrom}) laisserait
 * passer une reservation qui commence dans cinq minutes.
 *
 * <p>Ne pas non plus utiliser {@code Clock.systemDefaultZone()} : le fuseau
 * dependrait alors de la machine qui execute l'application, souvent UTC sur un
 * serveur ou un conteneur. Le fuseau de l'agence est une donnee metier, pas un
 * reglage du systeme d'exploitation.
 *
 * <h2>Configurable, mais avec un defaut explicite</h2>
 * {@code autohub.agency.time-zone} permet de changer de fuseau sans recompiler.
 * Changer de fuseau reste une decision lourde : les heures deja stockees en
 * base sont nominales et ne seraient pas converties (voir l'ADR 0002 et
 * {@code BACKLOG.md}, mono-agence assume).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    /**
     * Horloge systeme dans le fuseau de l'agence.
     *
     * @param agencyTimeZone identifiant IANA du fuseau, par exemple {@code Europe/Paris} ;
     *                       un identifiant inconnu fait echouer le demarrage, ce qui est voulu
     */
    @Bean
    public Clock clock(@Value("${autohub.agency.time-zone:Europe/Paris}") ZoneId agencyTimeZone) {
        return Clock.system(agencyTimeZone);
    }
}
