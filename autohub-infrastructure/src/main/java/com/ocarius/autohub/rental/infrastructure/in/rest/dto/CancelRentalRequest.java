package com.ocarius.autohub.rental.infrastructure.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/rentals/{id}/cancel}.
 *
 * @param reason motif de l'annulation, conserve dans l'evenement
 */
public record CancelRentalRequest(@NotBlank @Size(max = 500) String reason) {
}
