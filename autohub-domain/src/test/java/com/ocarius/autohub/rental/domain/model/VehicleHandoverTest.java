package com.ocarius.autohub.rental.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests du value object {@link VehicleHandover}. */
class VehicleHandoverTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2027, 3, 8, 8, 0);

    @Test
    @DisplayName("un kilometrage negatif est refuse")
    void kilometrage_negatif() {
        assertThatThrownBy(() -> new VehicleHandover(LUNDI_8H, -1, 50))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("un niveau de carburant hors de [0, 100] est refuse")
    void carburant_hors_bornes() {
        assertThatThrownBy(() -> new VehicleHandover(LUNDI_8H, 0, 101)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new VehicleHandover(LUNDI_8H, 0, -1)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("les bornes 0 et 100 % sont acceptees")
    void carburant_aux_bornes() {
        assertThat(new VehicleHandover(LUNDI_8H, 0, 0)).isNotNull();
        assertThat(new VehicleHandover(LUNDI_8H, 0, 100)).isNotNull();
    }

    @Test
    @DisplayName("deux constats identiques sont egaux : c'est un value object")
    void egalite_par_valeur() {
        assertThat(new VehicleHandover(LUNDI_8H, 1000, 80)).isEqualTo(new VehicleHandover(LUNDI_8H, 1000, 80));
    }

    @Test
    @DisplayName("la distance parcourue est la difference des compteurs")
    void distance_parcourue() {
        VehicleHandover depart = new VehicleHandover(LUNDI_8H, 10_000, 100);
        VehicleHandover retour = new VehicleHandover(LUNDI_8H.plusDays(2), 10_350, 40);
        assertThat(retour.kilometersSince(depart)).isEqualTo(350);
        assertThat(retour.hasLowerMileageThan(depart)).isFalse();
        assertThat(depart.happenedBefore(retour)).isTrue();
    }
}
