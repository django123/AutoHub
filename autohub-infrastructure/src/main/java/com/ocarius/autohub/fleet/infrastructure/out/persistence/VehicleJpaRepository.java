package com.ocarius.autohub.fleet.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repository Spring Data sur l'entite JPA, de visibilite paquet (voir RentalJpaRepository). */
interface VehicleJpaRepository extends JpaRepository<VehicleJpaEntity, UUID> {
}
