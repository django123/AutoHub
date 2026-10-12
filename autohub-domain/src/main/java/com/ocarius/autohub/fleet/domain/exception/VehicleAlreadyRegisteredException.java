package com.ocarius.autohub.fleet.domain.exception;

import com.ocarius.autohub.shared.domain.ConflictException;

/**
 * Un vehicule portant le meme VIN ou la meme immatriculation existe deja.
 *
 * <p>Comme {@code VehicleNotAvailableException}, l'invariant ("un VIN, une
 * voiture") traverse les agregats : il est garanti par les contraintes
 * {@code UNIQUE} de la table {@code vehicle}, et cette exception est levee par
 * l'adaptateur de persistance qui traduit la violation.
 */
public class VehicleAlreadyRegisteredException extends ConflictException {

    /**
     * @param detail description du doublon, pour le message
     * @param cause  l'exception technique d'origine
     */
    public VehicleAlreadyRegisteredException(String detail, Throwable cause) {
        super("VEHICLE_ALREADY_REGISTERED", "Ce vehicule est deja enregistre dans le parc : " + detail);
        initCause(cause);
    }
}
