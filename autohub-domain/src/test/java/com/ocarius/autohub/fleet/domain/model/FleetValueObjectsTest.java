package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests des value objects du contexte fleet : VIN, immatriculation, kilometrage, modele. */
class FleetValueObjectsTest {

    private static String codeOf(Throwable e) {
        return ((BusinessRuleViolation) e).code();
    }

    @Nested
    @DisplayName("Vin")
    class VinTest {

        @Test
        @DisplayName("un VIN saisi en minuscules est normalise, et egal a sa version majuscule")
        void normalisation() {
            assertThat(Vin.of(" vf3abcdef12345678 ")).isEqualTo(Vin.of("VF3ABCDEF12345678"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"VF3ABCDEF1234567", "VF3ABCDEF123456789", "VF3ABC-EF12345678"})
        @DisplayName("un VIN qui n'a pas 17 caracteres alphanumeriques est refuse")
        void longueur_ou_caractere_invalide(String value) {
            assertThatThrownBy(() -> Vin.of(value)).satisfies(e -> assertThat(codeOf(e)).isEqualTo("VIN_INVALID"));
        }

        @ParameterizedTest(name = "lettre {0}")
        @ValueSource(strings = {"I", "O", "Q"})
        @DisplayName("* les lettres I, O et Q sont interdites par la norme")
        void lettres_interdites(String letter) {
            assertThatThrownBy(() -> Vin.of("VF3ABCDEF1234567" + letter)).isInstanceOf(BusinessRuleViolation.class);
        }
    }

    @Nested
    @DisplayName("PlateNumber")
    class PlateNumberTest {

        @Test
        @DisplayName("une immatriculation saisie avec des espaces et en minuscules est normalisee")
        void normalisation() {
            assertThat(PlateNumber.of("ab 123 cd")).isEqualTo(PlateNumber.of("AB-123-CD"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"AB123CD", "A-123-CD", "AB-12-CD", "1B-123-CD"})
        @DisplayName("un format autre que AA-123-AA est refuse")
        void format_invalide(String value) {
            assertThatThrownBy(() -> PlateNumber.of(value))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo("PLATE_NUMBER_INVALID"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"AI-123-CD", "AB-123-OD", "UB-123-CD"})
        @DisplayName("* les lettres I, O et U sont interdites")
        void lettres_interdites(String value) {
            assertThatThrownBy(() -> PlateNumber.of(value)).isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("* le bloc numerique 000 n'est pas attribue")
        void bloc_000() {
            assertThatThrownBy(() -> PlateNumber.of("AB-000-CD")).isInstanceOf(BusinessRuleViolation.class);
        }
    }

    @Nested
    @DisplayName("Mileage et VehicleModel")
    class MileageEtModele {

        @Test
        @DisplayName("un kilometrage negatif est refuse, zero est accepte")
        void kilometrage() {
            assertThat(Mileage.of(0).kilometers()).isZero();
            assertThatThrownBy(() -> Mileage.of(-1)).satisfies(e -> assertThat(codeOf(e)).isEqualTo("MILEAGE_NEGATIVE"));
        }

        @Test
        @DisplayName("les comparaisons de kilometrage sont strictes")
        void comparaisons() {
            assertThat(Mileage.of(10).isLowerThan(Mileage.of(10))).isFalse();
            assertThat(Mileage.of(10).isGreaterThan(Mileage.of(9))).isTrue();
        }

        @Test
        @DisplayName("une marque vide ou une annee hors bornes est refusee")
        void modele_invalide() {
            assertThatThrownBy(() -> new VehicleModel("  ", "208", 2024)).isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> new VehicleModel("Peugeot", "208", 1949)).isInstanceOf(BusinessRuleViolation.class);
            assertThat(new VehicleModel(" Peugeot ", "208", 2024).label()).isEqualTo("Peugeot 208 (2024)");
        }
    }
}
