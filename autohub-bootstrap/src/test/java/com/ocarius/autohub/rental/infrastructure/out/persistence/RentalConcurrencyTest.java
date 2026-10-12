package com.ocarius.autohub.rental.infrastructure.out.persistence;

import com.ocarius.autohub.shared.domain.VehicleId;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.testing.AbstractPostgresIntegrationTest;
import com.ocarius.autohub.rental.domain.exception.VehicleNotAvailableException;
import com.ocarius.autohub.rental.domain.model.CustomerId;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.rental.domain.model.RentalQuote;
import com.ocarius.autohub.shared.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LE test du projet : 50 clients reservent le meme vehicule sur la meme
 * periode, au meme instant. Exactement un doit reussir.
 *
 * <h2>Pourquoi ce test prouve quelque chose</h2>
 * Chaque thread construit sa propre location, parfaitement valide aux yeux du
 * domaine : aucune instance de {@code Rental} ne sait que les 49 autres
 * existent. Si un seul thread reussit, c'est donc que la base a arbitre --
 * c'est la demonstration que la non-superposition n'est pas un invariant
 * d'agregat, mais un invariant garanti par la contrainte d'exclusion.
 *
 * <h2>Pourquoi un {@link CountDownLatch} de depart</h2>
 * Cinquante {@code submit()} successifs ne demarrent pas en meme temps : le
 * premier thread peut avoir commite avant que le dixieme ne soit cree, et
 * l'on testerait alors 50 insertions <i>sequentielles</i>. Ici, chaque thread
 * se met en attente sur {@code startGun} ; le thread principal attend que les
 * 50 soient prets ({@code ready}), puis les libere tous d'un coup.
 *
 * <p>Nuance honnete : le pool Hikari a 10 connexions par defaut, donc au plus
 * 10 transactions sont reellement simultanees en base. C'est suffisant pour
 * provoquer des INSERT concurrents (le cas ou une transaction attend le verrou
 * de l'autre puis echoue), et c'est la realite d'une application en production.
 *
 * <h2>Ce que ce test a appris : les deadlocks</h2>
 * La premiere version passait... une fois sur deux. L'autre fois, des
 * {@code CannotAcquireLockException} (SQLState {@code 40P01}) apparaissaient :
 * PostgreSQL insere la ligne avant de verifier la contrainte d'exclusion, si
 * bien que deux INSERT concurrents peuvent s'attendre mutuellement. Chaque
 * deadlock coutant une seconde, la rafale finissait par epuiser le pool de
 * connexions. L'adaptateur serialise desormais les ecritures par vehicule avec
 * un verrou consultatif (voir sa javadoc) : le test dure ~3 s au lieu de 40.
 * Un test de concurrence qui n'est vert qu'une fois sur deux est un test qui a
 * trouve un bug, pas un test instable.
 *
 * <h2>Pourquoi Testcontainers, et pas H2</h2>
 * H2 ne connait ni {@code tsrange}, ni {@code EXCLUDE USING gist}. Le test y
 * serait soit impossible, soit vert pour de mauvaises raisons.
 *
 * <h2>Verifie : ce test echoue sans la contrainte</h2>
 * En retirant {@code rental_no_overlap} de la migration V2, on obtient 50
 * succes et 0 refus. Un test qui ne peut pas echouer ne teste rien.
 */
class RentalConcurrencyTest extends AbstractPostgresIntegrationTest {

    private static final int THREADS = 50;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private RentalRepository repository;

    @Test
    @DisplayName("50 reservations simultanees du meme vehicule sur la meme periode : exactement 1 reussit")
    void une_seule_reservation_gagne() throws Exception {
        VehicleId vehicle = insertVehicle();
        RentalPeriod period = RentalPeriod.of(LocalDateTime.of(2027, 6, 1, 8, 0), LocalDateTime.of(2027, 6, 5, 8, 0));

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger notAvailable = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    // Chaque client prepare sa location AVANT le top depart :
                    // seule l'ecriture en base doit etre concurrente.
                    Rental rental = Rental.draft(RentalId.newId(), vehicle, CustomerId.newId(),
                            period, RentalQuote.of(Money.euros("100.00"), Money.euros("200.00")));
                    rental.reserve(CLOCK);
                    ready.countDown();
                    try {
                        startGun.await();
                        tx.executeWithoutResult(status -> repository.save(rental));
                        successes.incrementAndGet();
                    } catch (VehicleNotAvailableException e) {
                        notAvailable.incrementAndGet();
                    } catch (Throwable t) {
                        unexpected.add(t);
                    }
                    return null;
                }));
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("les 50 threads sont prets").isTrue();
            startGun.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected).as("aucune autre exception que VehicleNotAvailableException").isEmpty();
        assertThat(successes.get()).as("reservations acceptees").isEqualTo(1);
        assertThat(notAvailable.get()).as("reservations refusees").isEqualTo(THREADS - 1);

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM rental WHERE vehicle_id = ?", Integer.class, vehicle.value());
        assertThat(rows).as("une seule ligne en base pour ce vehicule").isEqualTo(1);
    }
}
