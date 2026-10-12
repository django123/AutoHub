package com.ocarius.autohub.rental.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Reservations echues d'un vehicule qui chevauchent une periode.
     *
     * <p>Requete native : l'operateur {@code &&} sur la colonne generee
     * {@code period} n'existe pas en JPQL, et c'est lui qui permet a
     * PostgreSQL d'utiliser l'index GiST de {@code rental_no_overlap}.
     */
    @Query(value = """
            SELECT r.* FROM rental r
             WHERE r.vehicle_id = :vehicleId
               AND r.status = 'RESERVED'
               AND r.reserved_until <= CAST(:now AS timestamptz)
               AND r.period && tsrange(CAST(:start AS timestamp), CAST(:end AS timestamp), '[)')
            """, nativeQuery = true)
    List<RentalJpaEntity> findOverdueReservationsOverlapping(@Param("vehicleId") UUID vehicleId,
                                                             @Param("start") LocalDateTime start,
                                                             @Param("end") LocalDateTime end,
                                                             @Param("now") Instant now);

    /**
     * Identifiants des reservations echues, les plus anciennes d'abord. Servie
     * par l'index partiel {@code rental_overdue_reservation_idx} (V3).
     */
    @Query(value = """
            SELECT r.id FROM rental r
             WHERE r.status = 'RESERVED' AND r.reserved_until <= CAST(:now AS timestamptz)
             ORDER BY r.reserved_until
             LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findOverdueReservationIds(@Param("now") Instant now, @Param("limit") int limit);
}
