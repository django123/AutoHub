package com.ocarius.autohub.fleet.infrastructure.out.persistence;

import com.ocarius.autohub.fleet.application.port.out.VehicleRepository;
import com.ocarius.autohub.fleet.domain.exception.ConcurrentVehicleModificationException;
import com.ocarius.autohub.fleet.domain.exception.VehicleAlreadyRegisteredException;
import com.ocarius.autohub.fleet.domain.model.Vehicle;
import com.ocarius.autohub.shared.domain.VehicleId;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * ADAPTATEUR SORTANT : implemente {@link VehicleRepository} avec JPA.
 *
 * <p>Memes principes que {@code RentalRepositoryAdapter} : {@code saveAndFlush}
 * pour que les violations surgissent ici, {@code Propagation.MANDATORY} pour
 * que le verrouillage optimiste protege reellement, et traduction de toutes
 * les exceptions techniques attendues en exceptions du domaine :
 * <ul>
 *   <li>violation de {@code vehicle_vin_key} ou {@code vehicle_plate_number_key}
 *       (SQLState 23505) : {@link VehicleAlreadyRegisteredException} ;</li>
 *   <li>conflit de version : {@link ConcurrentVehicleModificationException}.</li>
 * </ul>
 * Les autres violations d'integrite sont relancees telles quelles : ce sont des
 * bugs, pas des situations metier.
 */
@Component
class VehicleRepositoryAdapter implements VehicleRepository {

    private final VehicleJpaRepository jpaRepository;

    VehicleRepositoryAdapter(VehicleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** Insere ou met a jour le vehicule, et traduit doublons et conflits de version. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Vehicle vehicle) {
        VehicleJpaEntity entity = jpaRepository.findById(vehicle.id().value())
                .orElseGet(() -> new VehicleJpaEntity(vehicle.id().value()));
        VehicleMapper.copyToEntity(vehicle, entity);
        try {
            jpaRepository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentVehicleModificationException(vehicle.id(), e);
        } catch (DataIntegrityViolationException e) {
            String constraint = constraintNameOf(e);
            if ("vehicle_vin_key".equals(constraint)) {
                throw new VehicleAlreadyRegisteredException("VIN " + vehicle.vin(), e);
            }
            if ("vehicle_plate_number_key".equals(constraint)) {
                throw new VehicleAlreadyRegisteredException("immatriculation " + vehicle.plate(), e);
            }
            throw e;
        }
    }

    /** Relit un vehicule et le reconstitue via le mapper. */
    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> findById(VehicleId id) {
        return jpaRepository.findById(id.value()).map(VehicleMapper::toDomain);
    }

    /**
     * Nom de la contrainte violee, tel qu'Hibernate l'extrait du message
     * PostgreSQL. Le nom distingue les deux contraintes UNIQUE, que le SQLState
     * seul (23505 pour les deux) ne permet pas de departager.
     */
    private static String constraintNameOf(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
