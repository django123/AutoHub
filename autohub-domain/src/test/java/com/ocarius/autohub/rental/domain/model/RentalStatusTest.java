package com.ocarius.autohub.rental.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.ocarius.autohub.rental.domain.model.RentalStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la machine a etats {@link RentalStatus}, independamment de
 * l'agregat : si la table de transitions est fausse, inutile de chercher plus
 * loin.
 */
class RentalStatusTest {

    @Nested
    @DisplayName("Transitions autorisees")
    class Autorisees {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "DRAFT, RESERVED",
                "RESERVED, CONFIRMED",
                "RESERVED, CANCELLED",
                "RESERVED, EXPIRED",
                "CONFIRMED, PICKED_UP",
                "CONFIRMED, CANCELLED",
                "PICKED_UP, RETURNED",
                "RETURNED, CLOSED"
        })
        void transition_autorisee(RentalStatus from, RentalStatus to) {
            assertThat(from.canTransitionTo(to)).isTrue();
        }
    }

    @Nested
    @DisplayName("Transitions interdites")
    class Interdites {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "PICKED_UP, CANCELLED",   // on n'annule pas une voiture deja sur la route
                "CONFIRMED, EXPIRED",     // une reservation confirmee n'expire plus
                "DRAFT, CONFIRMED",       // on ne saute pas la reservation
                "RESERVED, PICKED_UP",    // on ne part pas sans confirmer
                "PICKED_UP, CLOSED",      // on ne clot pas sans retour
                "RETURNED, PICKED_UP",    // pas de retour en arriere
                "DRAFT, DRAFT"            // pas de transition sur place
        })
        void transition_interdite(RentalStatus from, RentalStatus to) {
            assertThat(from.canTransitionTo(to)).isFalse();
        }

        @ParameterizedTest(name = "{0} est terminal")
        @EnumSource(value = RentalStatus.class, names = {"CLOSED", "CANCELLED", "EXPIRED"})
        void aucun_etat_terminal_n_a_de_sortie(RentalStatus terminal) {
            assertThat(terminal.isTerminal()).isTrue();
            for (RentalStatus target : RentalStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }

        @ParameterizedTest(name = "{0} a au moins une sortie")
        @EnumSource(value = RentalStatus.class, names = {"CLOSED", "CANCELLED", "EXPIRED"},
                mode = EnumSource.Mode.EXCLUDE)
        void les_autres_etats_ont_une_sortie(RentalStatus status) {
            assertThat(status.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("Etats qui bloquent le vehicule")
    class Bloquants {

        @Test
        @DisplayName("seuls RESERVED, CONFIRMED et PICKED_UP bloquent le vehicule")
        void etats_bloquants() {
            // Cette liste est dupliquee dans la clause WHERE de la contrainte
            // rental_no_overlap (V2). Un test d'integration verifie l'egalite.
            assertThat(RentalStatus.blockingStatuses()).containsExactlyInAnyOrder(RESERVED, CONFIRMED, PICKED_UP);
        }

        @Test
        @DisplayName("* un brouillon ne bloque pas le vehicule")
        void brouillon_ne_bloque_pas() {
            assertThat(DRAFT.blocksVehicle()).isFalse();
        }

        @Test
        @DisplayName("* une location rendue ne bloque plus le vehicule, meme avant cloture")
        void rendue_ne_bloque_plus() {
            // Sinon, un retour anticipe empecherait de relouer la voiture
            // jusqu'a la fin prevue de la location precedente.
            assertThat(RETURNED.blocksVehicle()).isFalse();
        }
    }
}
