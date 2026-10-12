package com.ocarius.autohub.shared.infrastructure.error;

import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.rental.domain.exception.ConcurrentRentalModificationException;
import com.ocarius.autohub.rental.domain.exception.IllegalRentalTransitionException;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.rental.domain.model.RentalStatus;
import com.ocarius.autohub.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traduction des exceptions du domaine en statuts HTTP par
 * {@link GlobalExceptionHandler}.
 *
 * <h2>Pourquoi MockMvc "standalone"</h2>
 * Aucun controleur metier n'existe encore (week-end 6). Plutot que d'attendre,
 * on branche le handler sur un faux controleur qui leve chaque type
 * d'exception. {@code standaloneSetup} n'a besoin ni du contexte Spring ni de
 * la base : le test tourne en quelques dizaines de millisecondes, et il exerce
 * le vrai mecanisme de resolution de Spring MVC (choix du handler le plus
 * specifique), ce qu'un appel direct a {@code handleConflict} ne ferait pas.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("un vehicule deja loue repond 409")
    void vehicule_indisponible_409() throws Exception {
        mvc.perform(get("/vehicle-not-available"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.type").value("https://autohub.ocarius.com/errors/VEHICLE_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("une transition interdite repond 409")
    void transition_interdite_409() throws Exception {
        mvc.perform(get("/illegal-transition"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENTAL_TRANSITION_FORBIDDEN"));
    }

    @Test
    @DisplayName("une modification concurrente repond 409")
    void modification_concurrente_409() throws Exception {
        mvc.perform(get("/concurrent-modification"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENTAL_CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("* une regle metier violee hors conflit repond toujours 422")
    void regle_metier_422() throws Exception {
        // Garde-fou : le handler des conflits ne doit pas avaler les autres
        // exceptions du domaine.
        mvc.perform(get("/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RENTAL_PERIOD_TOO_SHORT"));
    }

    @Test
    @DisplayName("* une exception imprevue repond 500 sans divulguer son message")
    void imprevu_500() throws Exception {
        mvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Une erreur interne est survenue."));
    }

    /** Faux controleur : une route par categorie d'exception. */
    @RestController
    static class ThrowingController {

        private static final RentalPeriod PERIOD =
                RentalPeriod.of(LocalDateTime.of(2027, 3, 8, 8, 0), LocalDateTime.of(2027, 3, 10, 8, 0));

        @GetMapping("/vehicle-not-available")
        void vehicleNotAvailable() {
            throw new VehicleNotAvailableException(VehicleId.newId(), PERIOD, null);
        }

        @GetMapping("/illegal-transition")
        void illegalTransition() {
            throw new IllegalRentalTransitionException(RentalId.newId(), RentalStatus.PICKED_UP, RentalStatus.CANCELLED);
        }

        @GetMapping("/concurrent-modification")
        void concurrentModification() {
            throw new ConcurrentRentalModificationException(RentalId.newId(), null);
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleViolation("RENTAL_PERIOD_TOO_SHORT", "Une location dure au moins 1 h");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("table rental introuvable sur db-prod-01");
        }
    }
}
