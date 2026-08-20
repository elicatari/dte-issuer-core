package com.elicatari.dteissuer.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.elicatari.dteissuer.adapter.out.persistence.JpaOutboxStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class OutboxHealthIndicatorTest {

    @Test
    void deadLettersDegradeDetailsButStayUp() {
        JpaOutboxStore store = mock(JpaOutboxStore.class);
        when(store.deadLetteredCount()).thenReturn(3L);

        Health health = new OutboxHealthIndicator(store).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("deadLettered", 3L);
        assertThat(health.getDetails()).containsEntry("alert", "degraded");
    }
}