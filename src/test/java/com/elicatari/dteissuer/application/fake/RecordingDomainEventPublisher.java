package com.elicatari.dteissuer.application.fake;

import com.elicatari.dteissuer.application.port.out.DomainEventPublisher;
import com.elicatari.dteissuer.domain.DteIssued;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingDomainEventPublisher implements DomainEventPublisher {

    private final List<DteIssued> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(DteIssued event) {
        events.add(event);
    }

    public List<DteIssued> events() {
        return List.copyOf(events);
    }
}