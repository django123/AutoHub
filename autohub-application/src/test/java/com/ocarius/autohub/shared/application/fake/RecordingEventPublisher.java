package com.ocarius.autohub.shared.application.fake;

import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.domain.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/** Fake d'{@link EventPublisher} : retient les evenements publies, dans l'ordre. */
public final class RecordingEventPublisher implements EventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
    }

    /** Evenements publies, dans l'ordre. */
    public List<DomainEvent> published() {
        return List.copyOf(published);
    }

    /** Evenements publies d'un type donne. */
    public <E extends DomainEvent> List<E> published(Class<E> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
