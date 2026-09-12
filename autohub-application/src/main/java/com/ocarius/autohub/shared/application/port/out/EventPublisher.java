package com.ocarius.autohub.shared.application.port.out;

import com.ocarius.autohub.shared.domain.DomainEvent;

import java.util.List;

/**
 * PORT SORTANT : publication des evenements de domaine.
 *
 * <h2>Comment lire un "port"</h2>
 * Un port est une interface <b>definie par l'interieur</b> (application/domaine)
 * et <b>implementee par l'exterieur</b> (infrastructure). C'est l'inversion de
 * dependance du "D" de SOLID, appliquee a l'echelle de l'architecture.
 *
 * <p>Note la direction : ce n'est pas l'application qui s'adapte a Kafka,
 * c'est Kafka qui devra s'adapter a cette interface. Si demain tu passes de
 * Kafka a RabbitMQ, tu ecris un nouvel adaptateur et tu ne touches a rien
 * d'autre. Le test de qualite d'un port est la : son vocabulaire ne doit
 * trahir aucune technologie. Ici il n'y a ni "topic", ni "partition",
 * ni "exchange" -- uniquement {@code DomainEvent}.
 *
 * <h2>Implementations prevues</h2>
 * <ul>
 *   <li><b>Phase 0 a 4</b> : publication en memoire via les evenements Spring</li>
 *   <li><b>Phase 5</b> : ecriture dans une table outbox, dans la meme
 *       transaction que l'agregat -- garantissant qu'un rollback n'envoie rien</li>
 *   <li><b>Janvier</b> : Debezium lit l'outbox et alimente Kafka</li>
 * </ul>
 * Les trois changements se font <b>sans modifier une seule ligne</b> de la
 * couche application. C'est precisement ce que l'architecture achete.
 */
public interface EventPublisher {

    void publish(DomainEvent event);

    default void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
