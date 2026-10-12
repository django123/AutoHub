package com.ocarius.autohub.shared.infrastructure.transaction;

import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * ADAPTATEUR SORTANT : implemente {@link UnitOfWork} avec un
 * {@link TransactionTemplate} Spring.
 *
 * <p>Propagation par defaut ({@code REQUIRED}) : si une transaction est deja
 * ouverte, le travail la rejoint. C'est ce qui permet au consommateur
 * d'evenements de {@code fleet} d'ouvrir lui-meme une transaction neuve
 * ({@code REQUIRES_NEW}) dans laquelle le cas d'usage s'execute.
 *
 * <p>Une exception non verifiee levee par le travail provoque le rollback et
 * est relancee telle quelle : les exceptions du domaine traversent sans
 * enveloppe jusqu'au {@code GlobalExceptionHandler}.
 */
@Component
public class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactionTemplate;

    /** @param transactionTemplate gabarit configure par Spring Boot sur le gestionnaire JPA */
    public SpringUnitOfWork(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
