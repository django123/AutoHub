package com.ocarius.autohub.shared.infrastructure.event;

import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * ADAPTATEUR SORTANT : publie les evenements de domaine dans le bus
 * d'evenements de Spring. Remplace {@code LoggingEventPublisher}, qui se
 * contentait de les tracer.
 *
 * <h2>Remis pendant la transaction, delivres apres le commit</h2>
 * {@code publishEvent} est appele pendant la transaction du cas d'usage. Les
 * consommateurs qui ont des effets ({@code VehicleReturnedListener}) sont
 * declares {@code @TransactionalEventListener(phase = AFTER_COMMIT)} : Spring
 * retient l'evenement et ne le leur delivre qu'une fois le commit reussi. En
 * cas de rollback, l'evenement est abandonne -- aucun consommateur n'agit sur
 * un fait qui n'a pas eu lieu.
 *
 * <h2>Limite assumee jusqu'a la phase 5</h2>
 * Entre le commit et la livraison, l'evenement n'existe qu'en memoire : un
 * arret de l'application a cet instant precis le perd. La table outbox (phase 5)
 * supprimera cette fenetre en ecrivant l'evenement dans la meme transaction que
 * l'agregat. Ce remplacement se fera ici, sans toucher a la couche application.
 */
@Component
public class SpringDomainEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    /** @param publisher bus d'evenements du contexte Spring */
    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        log.info("Evenement de domaine [{}] id={} survenu={}", event.eventType(), event.eventId(), event.occurredOn());
        publisher.publishEvent(event);
    }
}
