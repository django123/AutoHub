package com.ocarius.autohub.rental.infrastructure.in.rest.dto;

import com.ocarius.autohub.rental.application.port.in.ReserveRentalCommand;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Corps de {@code POST /api/rentals}.
 *
 * <p>La validation Jakarta ne verifie que la <b>forme</b> (champs presents) :
 * c'est le travail d'un adaptateur d'entree. Les regles metier (duree minimale,
 * preavis, minute pleine) restent dans le domaine, seul juge, et remontent en
 * 422 via {@code GlobalExceptionHandler}. Les dupliquer ici, ce serait deux
 * definitions a garder alignees.
 *
 * @param vehicleId  vehicule demande
 * @param customerId client
 * @param start      debut, ISO-8601 sans fuseau (heure de l'agence)
 * @param end        fin, exclue
 */
public record ReserveRentalRequest(@NotNull UUID vehicleId, @NotNull UUID customerId,
                                   @NotNull LocalDateTime start, @NotNull LocalDateTime end) {

    /** Traduit la requete HTTP en commande du port entrant. */
    public ReserveRentalCommand toCommand() {
        return new ReserveRentalCommand(vehicleId, customerId, start, end);
    }
}
