package com.ocarius.autohub.fleet.domain.model;

import com.ocarius.autohub.shared.domain.BusinessRuleViolation;

import java.util.Objects;

/**
 * Modele commercial d'un vehicule : marque, modele, annee.
 *
 * <p>Value object : deux Peugeot 208 de 2024 ont le meme modele, meme si ce
 * sont deux voitures distinctes. C'est le {@link Vehicle} qui porte
 * l'identite.
 *
 * <p>L'annee est bornee a [1950, 2100] plutot que comparee a l'annee courante :
 * ce dernier controle demanderait une horloge dans un value object, pour un
 * gain nul (un modele de l'annee prochaine est vendu des septembre).
 *
 * @param brand marque, non vide
 * @param name  nom du modele, non vide
 * @param year  annee-modele
 */
public record VehicleModel(String brand, String name, int year) {

    /** Normalise les espaces et verifie les bornes. */
    public VehicleModel {
        Objects.requireNonNull(brand, "La marque est obligatoire");
        Objects.requireNonNull(name, "Le modele est obligatoire");
        brand = brand.strip();
        name = name.strip();
        BusinessRuleViolation.check(!brand.isEmpty(), "VEHICLE_MODEL_BRAND_EMPTY", "La marque est obligatoire");
        BusinessRuleViolation.check(!name.isEmpty(), "VEHICLE_MODEL_NAME_EMPTY", "Le modele est obligatoire");
        BusinessRuleViolation.check(
                year >= 1950 && year <= 2100,
                "VEHICLE_MODEL_YEAR_INVALID",
                "L'annee-modele doit etre comprise entre 1950 et 2100 : " + year);
    }

    /** Libelle lisible : "Peugeot 208 (2024)". */
    public String label() {
        return brand + " " + name + " (" + year + ")";
    }
}
