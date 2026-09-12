package com.ocarius.autohub.shared.domain.vo;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link DateRange}.
 *
 * <p>C'est la classe de test la plus importante de la phase 0 : la logique de
 * chevauchement qu'elle verifie sera, au week-end 5, dupliquee dans une
 * contrainte PostgreSQL. Si les deux definitions divergent d'un cas limite, on
 * obtient le pire type de bug -- celui ou le code et la base ne sont pas
 * d'accord sur ce qu'est un conflit.
 */
class DateRangeTest {

    // Reperes fixes et lisibles. Ne jamais utiliser LocalDateTime.now() dans un
    // test : le resultat dependrait de l'heure d'execution, et le test
    // echouerait un jour a 23h59 sans que personne ne comprenne pourquoi.
    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2026, 3, 9, 8, 0);
    private static final LocalDateTime MERCREDI_8H = LocalDateTime.of(2026, 3, 11, 8, 0);
    private static final LocalDateTime VENDREDI_8H = LocalDateTime.of(2026, 3, 13, 8, 0);
    private static final LocalDateTime DIMANCHE_8H = LocalDateTime.of(2026, 3, 15, 8, 0);

    @Nested
    @DisplayName("Invariants proteges a la construction")
    class Invariants {

        @Test
        @DisplayName("une fin anterieure au debut est refusee")
        void fin_avant_debut() {
            assertThatThrownBy(() -> new DateRange(MERCREDI_8H, LUNDI_8H))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting(e -> ((BusinessRuleViolation) e).code())
                    .isEqualTo("DATE_RANGE_INVALID");
        }

        @Test
        @DisplayName("un intervalle de duree nulle est refuse")
        void duree_nulle() {
            // Cas subtil : start == end passerait une verification naive
            // ecrite avec isBefore() au lieu de isAfter(). Un intervalle vide
            // se propagerait alors jusqu'au calcul de prix, ou il produirait
            // une facture a zero euro sans que rien ne signale l'anomalie.
            assertThatThrownBy(() -> new DateRange(LUNDI_8H, LUNDI_8H))
                    .isInstanceOf(BusinessRuleViolation.class);
        }
    }

    @Nested
    @DisplayName("Chevauchement -- la logique critique du projet")
    class Chevauchement {

        @Test
        @DisplayName("deux intervalles identiques se chevauchent")
        void identiques() {
            DateRange a = new DateRange(LUNDI_8H, VENDREDI_8H);
            assertThat(a.overlaps(a)).isTrue();
        }

        @Test
        @DisplayName("deux intervalles adjacents ne se chevauchent PAS")
        void adjacents() {
            // ---------------------------------------------------------------
            // LE TEST LE PLUS IMPORTANT DE CETTE CLASSE.
            //
            // Location A : lundi 08:00 -> mercredi 08:00
            // Location B : mercredi 08:00 -> vendredi 08:00
            //
            // Un client rend la voiture mercredi a 08:00, un autre la prend au
            // meme moment. C'est un scenario parfaitement legitime, et le
            // systeme doit l'accepter.
            //
            // Avec des intervalles FERMES [start, end], ce test echouerait et
            // on perdrait du chiffre d'affaires sur un faux conflit.
            // La semantique semi-ouverte [start, end) le resout par
            // construction -- sans aucun minusSeconds(1) dans le code.
            // ---------------------------------------------------------------
            DateRange locationA = new DateRange(LUNDI_8H, MERCREDI_8H);
            DateRange locationB = new DateRange(MERCREDI_8H, VENDREDI_8H);

            assertThat(locationA.overlaps(locationB))
                    .as("des locations qui s'enchainent ne sont pas en conflit")
                    .isFalse();
            assertThat(locationB.overlaps(locationA)).isFalse();
        }

        @Test
        @DisplayName("un intervalle inclus dans un autre se chevauche")
        void inclusion() {
            DateRange grand = new DateRange(LUNDI_8H, DIMANCHE_8H);
            DateRange petit = new DateRange(MERCREDI_8H, VENDREDI_8H);

            assertThat(grand.overlaps(petit)).isTrue();
            assertThat(petit.overlaps(grand)).isTrue();
        }

        @Test
        @DisplayName("le chevauchement est symetrique")
        void symetrie() {
            // Propriete mathematique : a.overlaps(b) == b.overlaps(a).
            // Une implementation asymetrique produit des bugs dependant de
            // l'ordre de parcours des reservations -- donc irreproductibles.
            DateRange a = new DateRange(LUNDI_8H, VENDREDI_8H);
            DateRange b = new DateRange(MERCREDI_8H, DIMANCHE_8H);

            assertThat(a.overlaps(b)).isEqualTo(b.overlaps(a));
        }

        @Test
        @DisplayName("deux intervalles disjoints ne se chevauchent pas")
        void disjoints() {
            DateRange a = new DateRange(LUNDI_8H, MERCREDI_8H);
            DateRange b = new DateRange(VENDREDI_8H, DIMANCHE_8H);

            assertThat(a.overlaps(b)).isFalse();
        }
    }

    @Nested
    @DisplayName("Appartenance d'un instant")
    class Appartenance {

        @Test
        @DisplayName("la borne de debut est incluse, la borne de fin est exclue")
        void bornes() {
            DateRange range = new DateRange(LUNDI_8H, VENDREDI_8H);

            assertThat(range.contains(LUNDI_8H)).as("debut inclus").isTrue();
            assertThat(range.contains(MERCREDI_8H)).as("milieu inclus").isTrue();
            assertThat(range.contains(VENDREDI_8H)).as("fin EXCLUE").isFalse();
        }
    }

    @Nested
    @DisplayName("Jours facturables -- regle metier")
    class JoursFacturables {

        @ParameterizedTest(name = "{0}h de location => {1} jour(s) factures")
        @CsvSource({
                "1,   1",    // une heure entamee = une journee due
                "23,  1",
                "24,  1",    // exactement 24h = 1 jour, pas 2
                "25,  2",    // une heure de depassement = une journee de plus
                "48,  2",
                "49,  3"
        })
        void arrondi_au_jour_superieur(long heures, long joursAttendus) {
            DateRange range = new DateRange(LUNDI_8H, LUNDI_8H.plusHours(heures));

            assertThat(range.billableDays()).isEqualTo(joursAttendus);
        }

        @Test
        @DisplayName("le cas limite des 24h exactes ne facture qu'un jour")
        void vingt_quatre_heures_exactes() {
            // Cas typique de bug off-by-one : une implementation naive avec
            // ChronoUnit.DAYS.between() + 1 facturerait 2 jours ici, et le
            // client s'en apercevrait avant nous.
            DateRange range = new DateRange(LUNDI_8H, LUNDI_8H.plusDays(1));

            assertThat(range.billableDays()).isEqualTo(1);
        }
    }
}
