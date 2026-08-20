package com.elicatari.dteissuer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elicatari.dteissuer.application.port.out.IdempotencyRecord;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    private static final TenantId ALPHA = new TenantId("alpha");

    @Test
    void startedIsInProgressUntilCompleted() {
        IdempotencyRecord started = IdempotencyRecord.started(ALPHA, "k", "hash");
        assertThat(started.inProgress()).isTrue();
        assertThat(started.isCompleted()).isFalse();
        assertThat(started.dteId()).isNull();

        UUID dteId = UUID.randomUUID();
        IdempotencyRecord done = started.completed(dteId);
        assertThat(done.isCompleted()).isTrue();
        assertThat(done.inProgress()).isFalse();
        assertThat(done.dteId()).isEqualTo(dteId);
        assertThat(done.requestHash()).isEqualTo("hash");
        assertThat(done.key()).isEqualTo("k");
    }

    @Test
    void rejectsMissingIdentity() {
        assertThatThrownBy(() -> IdempotencyRecord.started(null, "k", "hash"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> IdempotencyRecord.started(ALPHA, " ", "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyRecord.started(ALPHA, "k", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> IdempotencyRecord.started(ALPHA, "k", "hash").completed(null))
                .isInstanceOf(NullPointerException.class);
    }
}