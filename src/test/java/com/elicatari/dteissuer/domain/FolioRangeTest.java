package com.elicatari.dteissuer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FolioRangeTest {

    private static final TenantId ALPHA = new TenantId("alpha");

    @Test
    void reserveNextAssignsConsecutiveFoliosThenExhausts() {
        FolioRange range = FolioRange.open(ALPHA, new Folio(10), new Folio(12));
        List<Long> assigned = new ArrayList<>();

        FolioReservation first = range.reserveNext();
        assigned.add(first.folio().value());
        FolioReservation second = first.range().reserveNext();
        assigned.add(second.folio().value());
        FolioReservation third = second.range().reserveNext();
        assigned.add(third.folio().value());

        assertThat(assigned).containsExactly(10L, 11L, 12L);
        assertThat(third.range().exhausted()).isTrue();
        assertThatThrownBy(() -> third.range().reserveNext())
                .isInstanceOf(NoFolioAvailableException.class)
                .hasMessageContaining("alpha");
    }

    @Test
    void singleFolioRangeEmitsOnce() {
        FolioRange range = FolioRange.open(ALPHA, new Folio(1), new Folio(1));
        FolioReservation reservation = range.reserveNext();
        assertThat(reservation.folio()).isEqualTo(new Folio(1));
        assertThat(reservation.range().exhausted()).isTrue();
    }

    @Test
    void invertedRangeIsRejected() {
        assertThatThrownBy(() -> new FolioRange(UUID.randomUUID(), ALPHA, new Folio(5), new Folio(4), new Folio(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invertido");
    }

    @Test
    void cursorOutsideRangeIsRejected() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new FolioRange(id, ALPHA, new Folio(1), new Folio(3), new Folio(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FolioRange(id, ALPHA, new Folio(2), new Folio(4), new Folio(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openStartsAtFrom() {
        FolioRange range = FolioRange.open(ALPHA, new Folio(100), new Folio(110));
        assertThat(range.next()).isEqualTo(new Folio(100));
        assertThat(range.exhausted()).isFalse();
    }
}