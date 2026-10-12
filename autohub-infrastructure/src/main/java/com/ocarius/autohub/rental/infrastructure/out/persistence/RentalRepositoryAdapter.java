package com.ocarius.autohub.rental.infrastructure.out.persistence;

import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * ADAPTATEUR SORTANT : implemente le port {@link RentalRepository} avec JPA et
 * PostgreSQL.
 *
 * <h2>La traduction d'exception, raison d'etre de cette classe</h2>
 * Quand la contrainte {@code rental_no_overlap} refuse un INSERT, l'erreur
 * remonte en trois couches successives :
 * <ol>
 *   <li>{@code PSQLException} du pilote JDBC, SQLState {@code 23P01} ;</li>
 *   <li>enveloppee par Hibernate dans une {@code ConstraintViolationException} ;</li>
 *   <li>enveloppee par Spring dans une {@link DataIntegrityViolationException}.</li>
 * </ol>
 * Aucune de ces trois exceptions ne doit atteindre la couche application, qui
 * ne connait ni JDBC, ni Hibernate, ni Spring. L'adaptateur les remplace par
 * {@link VehicleNotAvailableException}, vocabulaire du domaine.
 *
 * <p>On teste le <b>SQLState</b> et non le message : {@code 23P01} est stable
 * et normalise, le texte du message depend de la version et de la langue du
 * serveur. {@code 23505} (cle unique) ou {@code 23503} (cle etrangere) sont
 * d'autres violations d'integrite, qu'il serait faux de presenter comme
 * "vehicule indisponible" : elles sont relancees telles quelles.
 *
 * <h2>Le deadlock, et le verrou consultatif qui l'evite (decouverts par le test a 50 threads)</h2>
 * PostgreSQL insere d'abord la ligne et son entree d'index, <i>puis</i> verifie
 * la contrainte d'exclusion. Deux transactions concurrentes peuvent donc
 * chacune trouver la ligne non validee de l'autre, et s'attendre
 * mutuellement. PostgreSQL detecte le cycle au bout de {@code deadlock_timeout}
 * (1 s par defaut) et annule l'une des deux avec le SQLState {@code 40P01}.
 * Sous une rafale, ces secondes s'enchainent : au premier essai, 49 perdants
 * sur 10 connexions ont epuise le delai d'attente du pool (30 s).
 *
 * <p>Parade : avant d'ecrire une location qui bloque le vehicule, on prend un
 * <b>verrou consultatif transactionnel</b> sur l'identifiant du vehicule
 * ({@code pg_advisory_xact_lock}). Les ecritures concurrentes sur un meme
 * vehicule passent alors une par une ; chacune verifie la contrainte contre
 * des lignes deja validees et echoue immediatement en {@code 23P01}. Les
 * vehicules differents ne s'attendent pas entre eux.
 *
 * <p>Le verrou <b>ne remplace pas</b> la contrainte : il se contente d'ordonner
 * les transactions, c'est toujours la contrainte qui refuse. Retirer la
 * contrainte de V2 fait accepter les 50 reservations du test de concurrence ;
 * retirer le verrou ne ferait que ralentir les conflits. Le {@code 40P01} reste
 * traduit par securite (une ecriture qui ne passerait pas par cet adaptateur,
 * un script SQL, ne prend pas le verrou).
 *
 * <h2>Pourquoi {@code saveAndFlush} et pas {@code save}</h2>
 * Sans flush, Hibernate differe l'INSERT jusqu'au commit, qui a lieu
 * <i>apres</i> la sortie de cette methode, dans l'intercepteur transactionnel
 * de l'appelant. La violation surgirait alors hors de portee de la
 * traduction ci-dessous. Le flush force l'INSERT ici et maintenant.
 *
 * <h2>Le verrouillage optimiste, et pourquoi {@code MANDATORY}</h2>
 * Le champ {@code version} n'existe que dans l'entite JPA. Pour qu'il protege
 * contre les mises a jour perdues, le chargement de l'agregat et sa sauvegarde
 * doivent avoir lieu dans <b>la meme transaction</b> : l'entite chargee par
 * {@link #findById} reste alors geree par Hibernate, {@link #save} la retrouve
 * avec sa version d'origine, et l'UPDATE echoue si quelqu'un l'a modifiee
 * entre-temps. {@code Propagation.MANDATORY} sur {@code save} rend cette
 * condition obligatoire : appele hors transaction, il echoue au lieu de
 * perdre silencieusement une modification concurrente.
 *
 * <p>Limite connue : si un jour un cas d'usage charge dans une requete HTTP et
 * sauvegarde dans une autre, il faudra faire voyager la version (dans le
 * domaine ou dans un DTO). Ce n'est le cas d'aucun cas d'usage prevu.
 */
@Component
class RentalRepositoryAdapter implements RentalRepository {

    /** SQLState PostgreSQL d'une violation de contrainte d'exclusion. */
    static final String EXCLUSION_VIOLATION = "23P01";

    /** SQLState PostgreSQL d'un interblocage detecte (voir la javadoc de la classe). */
    static final String DEADLOCK_DETECTED = "40P01";

    private final RentalJpaRepository jpaRepository;

    RentalRepositoryAdapter(RentalJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Insere ou met a jour la location, et traduit un conflit de periode en
     * exception du domaine.
     *
     * <p>Apres une violation, la transaction de l'appelant est marquee
     * "rollback only" par Spring : elle ne peut plus rien valider. C'est
     * voulu -- la reservation a echoue, le cas d'usage s'arrete.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Rental rental) {
        if (rental.blocksVehicle()) {
            jpaRepository.acquireTransactionLock(lockKeyOf(rental));
        }
        RentalJpaEntity entity = jpaRepository.findById(rental.id().value())
                .orElseGet(() -> new RentalJpaEntity(rental.id().value()));
        RentalMapper.copyToEntity(rental, entity);
        try {
            jpaRepository.saveAndFlush(entity);
        } catch (DataAccessException e) {
            if (isPeriodConflict(rental, sqlStateOf(e))) {
                throw new VehicleNotAvailableException(rental.vehicleId(), rental.period(), e);
            }
            throw e;
        }
    }

    /**
     * Cle du verrou consultatif : les 128 bits de l'UUID du vehicule replies sur
     * 64. Deux vehicules peuvent theoriquement partager une cle ; la seule
     * consequence serait de serialiser inutilement leurs reservations.
     */
    private static long lockKeyOf(Rental rental) {
        UUID vehicle = rental.vehicleId().value();
        return vehicle.getMostSignificantBits() ^ vehicle.getLeastSignificantBits();
    }

    /**
     * L'erreur revele-t-elle un conflit de periode avec une autre location ?
     * Toujours pour {@code 23P01} ; pour {@code 40P01}, seulement si la location
     * bloque le vehicule, donc si la contrainte d'exclusion a pu etre verifiee.
     */
    private static boolean isPeriodConflict(Rental rental, String sqlState) {
        return EXCLUSION_VIOLATION.equals(sqlState)
                || (DEADLOCK_DETECTED.equals(sqlState) && rental.blocksVehicle());
    }

    /** Relit une location et la reconstitue via le mapper (donc via {@code Rental.restore}). */
    @Override
    @Transactional(readOnly = true)
    public Optional<Rental> findById(RentalId id) {
        return jpaRepository.findById(id.value()).map(RentalMapper::toDomain);
    }

    /**
     * Cherche le SQLState dans la chaine des causes : selon la couche qui a
     * detecte l'erreur, la {@link SQLException} est enveloppee une ou plusieurs
     * fois.
     */
    private static String sqlStateOf(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
