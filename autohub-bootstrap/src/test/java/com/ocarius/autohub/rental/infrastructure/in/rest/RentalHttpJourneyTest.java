package com.ocarius.autohub.rental.infrastructure.in.rest;

import com.jayway.jsonpath.JsonPath;
import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complets joues en HTTP, de l'API jusqu'a PostgreSQL : c'est la
 * preuve que les couches s'assemblent (Definition of Done WE6 et WE7).
 *
 * <p>Chaque test cree ses propres vehicules par l'API du parc : aucune
 * dependance a l'ordre d'execution, ni aux donnees des autres tests.
 */
class RentalHttpJourneyTest extends AbstractPostgresIntegrationTest {

    /** Une semaine apres l'horloge de test (1er mars 2027) : le preavis est respecte. */
    private static final String START = "2027-03-08T08:00:00";
    private static final String END = "2027-03-10T08:00:00";

    @Autowired
    private MockMvc mvc;

    // =================================================================
    // Helpers : une ligne par geste metier
    // =================================================================

    /** Enregistre un vehicule par l'API et l'amene jusqu'a l'etat louable. */
    private String rentableVehicle(String dailyRate) throws Exception {
        String body = """
                {"vin": "%s", "plateNumber": "%s", "brand": "Renault", "model": "Clio", "year": 2024,
                 "mileageKm": 12000, "condition": "NEW", "offerings": ["FOR_RENT"],
                 "dailyRentalRate": %s, "currency": "EUR"}
                """.formatted(randomVin(VehicleId.newId()), randomPlate(), dailyRate);
        String id = JsonPath.read(mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/vehicles/{id}/preparation", id)).andExpect(status().isOk());
        mvc.perform(post("/api/vehicles/{id}/commissioning", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentable").value(true));
        return id;
    }

    private ResultActions reserve(String vehicleId, String start, String end) throws Exception {
        return mvc.perform(post("/api/rentals").contentType(MediaType.APPLICATION_JSON).content("""
                {"vehicleId": "%s", "customerId": "%s", "start": "%s", "end": "%s"}
                """.formatted(vehicleId, UUID.randomUUID(), start, end)));
    }

    private String reserveOk(String vehicleId) throws Exception {
        return JsonPath.read(reserve(vehicleId, START, END).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions handover(String rentalId, String action, String at, long km) throws Exception {
        return mvc.perform(post("/api/rentals/{id}/" + action, rentalId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"at": "%s", "mileageKm": %d, "fuelLevelPercent": 80}
                        """.formatted(at, km)));
    }

    // =================================================================
    // Parcours
    // =================================================================

    @Test
    @DisplayName("devis -> reservation -> confirmation -> depart -> retour en retard -> cloture, en HTTP")
    void cycle_complet() throws Exception {
        String vehicle = rentableVehicle("45.00");

        // Devis : le vehicule apparait dans les disponibilites, avec son prix detaille.
        mvc.perform(get("/api/vehicles/available").param("start", START).param("end", END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.vehicleId == '%s')].total.amount".formatted(vehicle)).value(hasItem(90.0)));

        String rental = reserveOk(vehicle);
        mvc.perform(get("/api/rentals/{id}", rental))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.quotedPrice.amount").value(90.0))
                .andExpect(jsonPath("$.reservedUntil").exists());

        mvc.perform(post("/api/rentals/{id}/confirm", rental))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        handover(rental, "pickup", START, 12_000).andExpect(jsonPath("$.status").value("PICKED_UP"));

        // Une heure de retard : un jour de penalite a 150 % (67,50 EUR).
        handover(rental, "return", "2027-03-10T09:00:00", 12_480)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.lateReturnPenalty.amount").value(67.5))
                .andExpect(jsonPath("$.totalDue.amount").value(157.5));

        mvc.perform(post("/api/rentals/{id}/close", rental)).andExpect(jsonPath("$.status").value("CLOSED"));

        // ADR 0004 : le kilometrage du vehicule a ete mis a jour par l'evenement
        // VehicleReturned, consomme par fleet apres le commit du retour.
        mvc.perform(get("/api/vehicles/{id}", vehicle)).andExpect(jsonPath("$.mileageKm").value(12_480));
    }

    @Test
    @DisplayName("* aucune reponse ne laisse fuir la structure interne de l'agregat")
    void pas_d_agregat_dans_le_json() throws Exception {
        String body = reserve(rentableVehicle("45.00"), START, END).andReturn().getResponse().getContentAsString();

        // Les champs techniques de l'agregat ou de l'entite n'apparaissent pas :
        // ni evenements de domaine, ni version, ni identifiants enveloppes.
        assertThat(body).doesNotContain("domainEvents", "version", "\"value\"", "quote\"");
    }

    @Nested
    @DisplayName("Conflits et expiration")
    class ConflitsEtExpiration {

        @Test
        @DisplayName("une seconde reservation sur le meme creneau repond 409 VEHICLE_NOT_AVAILABLE")
        void creneau_deja_pris() throws Exception {
            String vehicle = rentableVehicle("45.00");
            reserveOk(vehicle);

            reserve(vehicle, "2027-03-09T08:00:00", "2027-03-11T08:00:00")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VEHICLE_NOT_AVAILABLE"));
        }

        @Test
        @DisplayName("* une reservation echue libere son creneau des la reservation suivante, sans attendre le traitement")
        void reservation_echue_liberee() throws Exception {
            String vehicle = rentableVehicle("45.00");
            String first = reserveOk(vehicle);

            clock.advance(Duration.ofMinutes(31));
            String second = reserveOk(vehicle);

            mvc.perform(get("/api/rentals/{id}", first)).andExpect(jsonPath("$.status").value("EXPIRED"));
            mvc.perform(get("/api/rentals/{id}", second)).andExpect(jsonPath("$.status").value("RESERVED"));
        }

        @Test
        @DisplayName("* une reservation echue n'empeche pas le vehicule d'apparaitre dans les disponibilites")
        void reservation_echue_et_disponibilites() throws Exception {
            String vehicle = rentableVehicle("45.00");
            reserveOk(vehicle);
            mvc.perform(get("/api/vehicles/available").param("start", START).param("end", END))
                    .andExpect(jsonPath("$[*].vehicleId").value(not(hasItem(vehicle))));

            clock.advance(Duration.ofMinutes(31));

            mvc.perform(get("/api/vehicles/available").param("start", START).param("end", END))
                    .andExpect(jsonPath("$[*].vehicleId").value(hasItem(vehicle)));
        }

        @Test
        @DisplayName("confirmer apres l'echeance repond 422 RENTAL_RESERVATION_EXPIRED")
        void confirmation_trop_tardive() throws Exception {
            String rental = reserveOk(rentableVehicle("45.00"));
            clock.advance(Duration.ofMinutes(30));

            mvc.perform(post("/api/rentals/{id}/confirm", rental))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("RENTAL_RESERVATION_EXPIRED"));
        }

        @Test
        @DisplayName("annuler apres le depart repond 409 RENTAL_TRANSITION_FORBIDDEN")
        void annulation_apres_depart() throws Exception {
            String rental = reserveOk(rentableVehicle("45.00"));
            mvc.perform(post("/api/rentals/{id}/confirm", rental));
            handover(rental, "pickup", START, 12_000);

            mvc.perform(post("/api/rentals/{id}/cancel", rental).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"trop tard\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RENTAL_TRANSITION_FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("Erreurs de saisie")
    class ErreursDeSaisie {

        @Test
        @DisplayName("une location inconnue repond 404 RENTAL_NOT_FOUND")
        void location_inconnue() throws Exception {
            mvc.perform(get("/api/rentals/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RENTAL_NOT_FOUND"));
        }

        @Test
        @DisplayName("un vehicule inconnu repond 404, un vehicule non mis en service 409")
        void vehicule_inconnu_ou_non_louable() throws Exception {
            reserve(UUID.randomUUID().toString(), START, END)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));

            String body = """
                    {"vin": "%s", "plateNumber": "%s", "brand": "Renault", "model": "Clio", "year": 2024,
                     "mileageKm": 0, "condition": "NEW", "offerings": ["FOR_RENT"], "dailyRentalRate": 40, "currency": "EUR"}
                    """.formatted(randomVin(VehicleId.newId()), randomPlate());
            String acquired = JsonPath.read(mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andReturn().getResponse().getContentAsString(), "$.id");
            reserve(acquired, START, END)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VEHICLE_NOT_RENTABLE"));
        }

        @Test
        @DisplayName("un champ obligatoire absent repond 400 (validation de forme)")
        void champ_absent() throws Exception {
            mvc.perform(post("/api/rentals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("une periode trop courte repond 422 RENTAL_PERIOD_TOO_SHORT (regle du domaine)")
        void periode_trop_courte() throws Exception {
            reserve(rentableVehicle("45.00"), START, "2027-03-08T08:30:00")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("RENTAL_PERIOD_TOO_SHORT"));
        }

        @Test
        @DisplayName("* un VIN deja enregistre repond 409 VEHICLE_ALREADY_REGISTERED")
        void vin_en_double() throws Exception {
            String vin = randomVin(VehicleId.newId());
            String template = """
                    {"vin": "%s", "plateNumber": "%s", "brand": "Renault", "model": "Clio", "year": 2024,
                     "mileageKm": 0, "condition": "NEW", "offerings": [], "dailyRentalRate": 40, "currency": "EUR"}
                    """;
            mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                    .content(template.formatted(vin, randomPlate()))).andExpect(status().isCreated());

            mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                            .content(template.formatted(vin, randomPlate())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_REGISTERED"));
        }
    }
}
