package com.ocarius.autohub.fleet.domain.model;

/**
 * Ce que l'agence propose de faire d'un vehicule : le louer, le vendre, ou les
 * deux. Un vehicule sans aucune offre reste dans le parc sans etre propose.
 */
public enum Offering {
    /** Propose a la location (contexte rental). */
    FOR_RENT,
    /** Propose a la vente (contexte sales, phase 4). */
    FOR_SALE
}
