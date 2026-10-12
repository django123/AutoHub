package com.ocarius.autohub.fleet.infrastructure.in.rest;

import com.ocarius.autohub.fleet.application.port.in.AddVehicleToFleetUseCase;
import com.ocarius.autohub.fleet.application.port.in.CommissionVehicleUseCase;
import com.ocarius.autohub.fleet.application.port.in.GetVehicleQuery;
import com.ocarius.autohub.fleet.application.port.in.PrepareVehicleUseCase;
import com.ocarius.autohub.fleet.infrastructure.in.rest.dto.AddVehicleRequest;
import com.ocarius.autohub.fleet.infrastructure.in.rest.dto.VehicleResponse;
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
 * ADAPTATEUR ENTRANT : l'API HTTP du parc.
 *
 * <p>Perimetre minimal : ce qu'il faut pour amener un vehicule jusqu'a l'etat
 * louable (ajout, preparation, mise en service) et le consulter. Maintenance,
 * vente, retrait et changement d'offre existent dans l'agregat et sont testes,
 * mais n'ont pas encore d'endpoint (la vente arrivera avec {@code sales}).
 * Memes regles que {@code RentalController} : ports entrants seulement, aucun
 * try/catch, actions metier en POST sur des sous-ressources.
 */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final AddVehicleToFleetUseCase add;
    private final PrepareVehicleUseCase prepare;
    private final CommissionVehicleUseCase commission;
    private final GetVehicleQuery get;

    /** Injection des ports entrants. */
    public VehicleController(AddVehicleToFleetUseCase add, PrepareVehicleUseCase prepare,
                             CommissionVehicleUseCase commission, GetVehicleQuery get) {
        this.add = add;
        this.prepare = prepare;
        this.commission = commission;
        this.get = get;
    }

    /** Enregistrer un vehicule achete : 201 et en-tete {@code Location}. */
    @PostMapping
    public ResponseEntity<VehicleResponse> add(@Valid @RequestBody AddVehicleRequest request) {
        UUID id = add.add(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(VehicleResponse.from(get.get(id)));
    }

    /** Consulter un vehicule. */
    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable UUID id) {
        return VehicleResponse.from(get.get(id));
    }

    /** Commencer la preparation. */
    @PostMapping("/{id}/preparation")
    public VehicleResponse startPreparation(@PathVariable UUID id) {
        prepare.startPreparation(id);
        return VehicleResponse.from(get.get(id));
    }

    /** Mettre en service. */
    @PostMapping("/{id}/commissioning")
    public VehicleResponse commission(@PathVariable UUID id) {
        commission.commission(id);
        return VehicleResponse.from(get.get(id));
    }
}
