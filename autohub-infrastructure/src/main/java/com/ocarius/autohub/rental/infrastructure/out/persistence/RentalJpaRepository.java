package com.ocarius.autohub.rental.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Repository Spring Data sur l'entite JPA.
 *
 * <p>A ne pas confondre avec le port {@code RentalRepository} : celui-ci parle
 * d'entites et d'UUID, le port parle d'agregats et de {@code RentalId}. Il est
 * de visibilite paquet pour que personne, hors de l'adaptateur, ne soit tente
 * de l'injecter directement dans un service et de contourner le domaine.
 */
interface RentalJpaRepository extends JpaRepository<RentalJpaEntity, UUID> {

    /**
     * Prend un verrou consultatif PostgreSQL, libere automatiquement a la fin
     * de la transaction (commit ou rollback).
     *
     * <p>Un verrou consultatif ne protege aucune ligne : c'est un simple jeton
     * nomme par un entier, que seules les transactions qui le demandent
     * respectent. Ici, il sert a mettre en file les ecritures concurrentes
     * visant un meme vehicule (voir {@link RentalRepositoryAdapter}).
     *
     * <p>{@code SELECT true FROM ...} plutot que {@code SELECT pg_advisory_xact_lock(...)} :
     * la fonction renvoie {@code void}, type qu'Hibernate ne sait pas lire.
     *
     * @param key cle du verrou, derivee de l'identifiant du vehicule
     */
    @Query(value = "SELECT true FROM pg_advisory_xact_lock(:key)", nativeQuery = true)
    Boolean acquireTransactionLock(@Param("key") long key);
}
