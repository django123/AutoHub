package com.ocarius.autohub.rental.infrastructure.in.rest;

import com.ocarius.autohub.rental.application.port.in.CancelRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.CloseRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.ConfirmRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.GetRentalQuery;
import com.ocarius.autohub.rental.application.port.in.PickUpVehicleUseCase;
import com.ocarius.autohub.rental.application.port.in.ReserveRentalUseCase;
import com.ocarius.autohub.rental.application.port.in.ReturnVehicleUseCase;
import com.ocarius.autohub.rental.infrastructure.in.rest.dto.CancelRentalRequest;
import com.ocarius.autohub.rental.infrastructure.in.rest.dto.HandoverRequest;
import com.ocarius.autohub.rental.infrastructure.in.rest.dto.RentalResponse;
import com.ocarius.autohub.rental.infrastructure.in.rest.dto.ReserveRentalRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * ADAPTATEUR ENTRANT : l'API HTTP des locations.
 *
 * <h2>Regles respectees (session WE6, bloc 6.4)</h2>
 * <ul>
 *   <li>Ne connait que des <b>ports entrants</b> (interfaces), jamais un
 *       service ni une classe du domaine : verifie par ArchUnit.</li>
 *   <li>Aucun {@code try/catch} : les exceptions du domaine remontent au
 *       {@code GlobalExceptionHandler}, qui choisit le statut (404, 409, 422).</li>
 *   <li>Aucune transaction : elle est ouverte par le cas d'usage. Une
 *       transaction au niveau du controleur durerait pendant la serialisation
 *       JSON, et un cas d'usage appele par un job ne serait plus transactionnel.</li>
 * </ul>
 *
 * <h2>Les actions sont des POST sur des sous-ressources</h2>
 * {@code POST /api/rentals/{id}/confirm} plutot qu'un {@code PATCH} du champ
 * {@code status} : le client exprime une intention metier, et ne peut pas
 * ecrire un statut arbitraire. C'est Tell-Don't-Ask applique a l'API.
 *
 * <p>Chaque action renvoie la location a jour, relue par {@link GetRentalQuery}
 * dans une seconde transaction : la commande renvoie un identifiant, la lecture
 * une vue (separation commande / requete).
 */
@RestController
@RequestMapping("/api/rentals")
public class RentalController {

    private final ReserveRentalUseCase reserve;
    private final ConfirmRentalUseCase confirm;
    private final CancelRentalUseCase cancel;
    private final PickUpVehicleUseCase pickUp;
    private final ReturnVehicleUseCase returnVehicle;
    private final CloseRentalUseCase close;
    private final GetRentalQuery get;

    /** Injection des ports entrants, un par action. */
    public RentalController(ReserveRentalUseCase reserve, ConfirmRentalUseCase confirm, CancelRentalUseCase cancel,
                            PickUpVehicleUseCase pickUp, ReturnVehicleUseCase returnVehicle,
                            CloseRentalUseCase close, GetRentalQuery get) {
        this.reserve = reserve;
        this.confirm = confirm;
        this.cancel = cancel;
        this.pickUp = pickUp;
        this.returnVehicle = returnVehicle;
        this.close = close;
        this.get = get;
    }

    /** Reserver : 201, en-tete {@code Location}, et la location creee. */
    @PostMapping
    public ResponseEntity<RentalResponse> reserve(@Valid @RequestBody ReserveRentalRequest request) {
        UUID id = reserve.reserve(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(RentalResponse.from(get.get(id)));
    }

    /** Consulter une location. */
    @GetMapping("/{id}")
    public RentalResponse get(@PathVariable UUID id) {
        return RentalResponse.from(get.get(id));
    }

    /** Confirmer une reservation. */
    @PostMapping("/{id}/confirm")
    public RentalResponse confirm(@PathVariable UUID id) {
        confirm.confirm(id);
        return RentalResponse.from(get.get(id));
    }

    /** Annuler une location. */
    @PostMapping("/{id}/cancel")
    public RentalResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRentalRequest request) {
        cancel.cancel(id, request.reason());
        return RentalResponse.from(get.get(id));
    }

    /** Enregistrer le depart du vehicule. */
    @PostMapping("/{id}/pickup")
    public RentalResponse pickUp(@PathVariable UUID id, @Valid @RequestBody HandoverRequest request) {
        pickUp.pickUp(id, request.toCommand());
        return RentalResponse.from(get.get(id));
    }

    /** Enregistrer le retour du vehicule. */
    @PostMapping("/{id}/return")
    public RentalResponse returnVehicle(@PathVariable UUID id, @Valid @RequestBody HandoverRequest request) {
        returnVehicle.returnVehicle(id, request.toCommand());
        return RentalResponse.from(get.get(id));
    }

    /** Clore une location rendue. */
    @PostMapping("/{id}/close")
    public RentalResponse close(@PathVariable UUID id) {
        close.close(id);
        return RentalResponse.from(get.get(id));
    }
}
