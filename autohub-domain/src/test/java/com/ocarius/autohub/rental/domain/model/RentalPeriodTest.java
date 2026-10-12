package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link RentalPeriod} : les regles propres a une location, en plus
 * de celles de {@code DateRange} (deja couvertes par {@code DateRangeTest}).
 */
class RentalPeriodTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2027, 3, 8, 8, 0);

    /** Extrait le code d'une violation, pour des assertions lisibles. */
    private static String codeOf(Throwable e) {
        return ((BusinessRuleViolation) e).code();
    }

    @Nested
    @DisplayName("Invariants proteges a la construction")
    class Invariants {

        @Test
        @DisplayName("une periode d'une heure exactement est acceptee")
        void une_heure_pile_est_acceptee() {
            assertThat(RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusHours(1)).duration())
                    .isEqualTo(RentalPeriod.MINIMUM_DURATION);
        }

        @Test
        @DisplayName("une periode de moins d'une heure est refusee")
        void moins_d_une_heure_est_refusee() {
            assertThatThrownBy(() -> RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusMinutes(59)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_PERIOD_TOO_SHORT"));
        }

        @Test
        @DisplayName("90 jours exactement sont acceptes, 90 jours et une minute non")
        void duree_maximale() {
            assertThat(RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(90))).isNotNull();
            assertThatThrownBy(() -> RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(90).plusMinutes(1)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_PERIOD_TOO_LONG"));
        }

        @Test
        @DisplayName("des bornes avec des secondes sont refusees")
        void bornes_avec_secondes() {
            assertThatThrownBy(() -> RentalPeriod.of(LUNDI_8H.plusSeconds(1), LUNDI_8H.plusDays(1)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_PERIOD_NOT_MINUTE_ALIGNED"));
        }

        @Test
        @DisplayName("* des bornes avec des nanosecondes sont refusees (ADR 0002, point 2)")
        void bornes_avec_nanosecondes() {
            // Le cas qui a motive la regle : en base, la nanoseconde disparait
            // (precision timestamp = 1 microseconde) et l'intervalle peut devenir
            // vide, donc invisible pour la contrainte d'exclusion.
            assertThatThrownBy(() -> RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(1).plusNanos(1)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RENTAL_PERIOD_NOT_MINUTE_ALIGNED"));
        }

        @Test
        @DisplayName("une fin anterieure au debut reste refusee par DateRange")
        void fin_avant_debut() {
            assertThatThrownBy(() -> RentalPeriod.of(LUNDI_8H, LUNDI_8H.minusDays(1)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("DATE_RANGE_INVALID"));
        }
    }

    @Nested
    @DisplayName("Preavis")
    class Preavis {

        private final RentalPeriod period = RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(2));

        @Test
        @DisplayName("une heure de preavis exactement suffit")
        void preavis_exact() {
            assertThat(period.respectsMinimumNoticeFrom(LUNDI_8H.minusHours(1))).isTrue();
        }

        @Test
        @DisplayName("59 minutes de preavis ne suffisent pas")
        void preavis_insuffisant() {
            assertThat(period.respectsMinimumNoticeFrom(LUNDI_8H.minusMinutes(59))).isFalse();
        }

        @Test
        @DisplayName("une periode deja commencee ne respecte pas le preavis")
        void periode_deja_commencee() {
            assertThat(period.respectsMinimumNoticeFrom(LUNDI_8H.plusHours(3))).isFalse();
        }
    }

    @Nested
    @DisplayName("Chevauchement et appartenance")
    class Chevauchement {

        private final RentalPeriod lundiMercredi = RentalPeriod.of(LUNDI_8H, LUNDI_8H.plusDays(2));

        @Test
        @DisplayName("deux periodes adjacentes ne se chevauchent pas")
        void periodes_adjacentes() {
            RentalPeriod mercrediVendredi = RentalPeriod.of(LUNDI_8H.plusDays(2), LUNDI_8H.plusDays(4));
            assertThat(lundiMercredi.overlaps(mercrediVendredi)).isFalse();
            assertThat(mercrediVendredi.overlaps(lundiMercredi)).isFalse();
        }

        @Test
        @DisplayName("deux periodes qui partagent une minute se chevauchent")
        void chevauchement_d_une_minute() {
            RentalPeriod autre = RentalPeriod.of(LUNDI_8H.plusDays(2).minusMinutes(1), LUNDI_8H.plusDays(3));
            assertThat(lundiMercredi.overlaps(autre)).isTrue();
        }

        @Test
        @DisplayName("la fin prevue n'appartient pas a la periode")
        void fin_exclue() {
            assertThat(lundiMercredi.contains(LUNDI_8H)).isTrue();
            assertThat(lundiMercredi.contains(LUNDI_8H.plusDays(2))).isFalse();
        }

        @Test
        @DisplayName("un instant posterieur a la fin est detecte, la fin elle-meme non")
        void se_termine_avant() {
            assertThat(lundiMercredi.endsBefore(LUNDI_8H.plusDays(2))).isFalse();
            assertThat(lundiMercredi.endsBefore(LUNDI_8H.plusDays(2).plusMinutes(1))).isTrue();
        }
    }
}
