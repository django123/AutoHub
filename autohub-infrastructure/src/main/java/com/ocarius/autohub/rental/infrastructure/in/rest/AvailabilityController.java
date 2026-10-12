package com.ocarius.autohub.rental.infrastructure.in.rest;

import com.ocarius.autohub.rental.application.port.in.FindAvailableVehiclesUseCase;
import com.ocarius.autohub.rental.infrastructure.in.rest.dto.AvailableVehicleResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ADAPTATEUR ENTRANT : recherche de vehicules disponibles a la location.
 *
 * <p>Dans le contexte {@code rental} et non {@code fleet}, malgre l'URL en
 * {@code /api/vehicles} : "disponible sur une periode" est une notion de
 * location, qui depend des reservations. Le parc ne sait pas ce qu'est une
 * reservation.
 *
 * <p>Ni pagination, ni filtre, ni tri : coupe assumee, notee au BACKLOG.
 */
@RestController
public class AvailabilityController {

    private final FindAvailableVehiclesUseCase findAvailable;

    /** @param findAvailable port entrant de recherche */
    public AvailabilityController(FindAvailableVehiclesUseCase findAvailable) {
        this.findAvailable = findAvailable;
    }

    /**
     * {@code GET /api/vehicles/available?start=2027-03-08T08:00&end=2027-03-10T08:00}
     *
     * @param start debut souhaite, ISO-8601 sans fuseau
     * @param end   fin souhaitee
     */
    @GetMapping("/api/vehicles/available")
    public List<AvailableVehicleResponse> findAvailable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return findAvailable.findAvailable(start, end).stream().map(AvailableVehicleResponse::from).toList();
    }
}
