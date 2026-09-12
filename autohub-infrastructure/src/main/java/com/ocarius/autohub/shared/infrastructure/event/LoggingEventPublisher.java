package com.ocarius.autohub.shared.infrastructure.event;

import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADAPTATEUR SORTANT provisoire : implementation du port {@link EventPublisher}
 * qui se contente de tracer les evenements.
 *
 * <h2>Pourquoi commencer par une implementation "inutile"</h2>
 * Elle rend le systeme complet et testable de bout en bout des maintenant,
 * sans introduire ni broker ni table outbox. C'est le meme raisonnement que le
 * {@code FakePaymentGateway} : on repousse les decisions couteuses tout en
 * gardant un systeme qui tourne.
 *
 * <p>En phase 5, cette classe sera remplacee par un {@code OutboxEventPublisher}.
 * Le remplacement se fera ici et nulle part ailleurs : aucune classe de la
 * couche application ne connait ce type, elles ne connaissent que l'interface.
 * C'est l'inversion de dependance qui paye sa dette.
 */
@Component
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(DomainEvent event) {
        log.info("Evenement de domaine [{}] id={} survenu={}",
                event.eventType(), event.eventId(), event.occurredOn());
    }
}
