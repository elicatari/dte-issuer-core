package com.elicatari.dteissuer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DteTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final Rut RUT = Rut.parse("12.345.678-5");
    private static final Instant ISSUED_AT = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void issueComputesIva19AndTotalInWholePesos() {
        Dte dte = Dte.issue(ALPHA, new Folio(10), RUT, Money.pesos(1000), ISSUED_AT);

        assertThat(dte.documentType()).isEqualTo(DocumentType.BOLETA_39);
        assertThat(dte.status()).isEqualTo(DteStatus.ISSUED);
        assertThat(dte.neto()).isEqualTo(Money.pesos(1000));
        assertThat(dte.iva()).isEqualTo(Money.pesos(190));
        assertThat(dte.total()).isEqualTo(Money.pesos(1190));
        assertThat(dte.issuedEvent().tenantId()).isEqualTo(ALPHA);
        assertThat(dte.issuedEvent().folio()).isEqualTo(new Folio(10));
        assertThat(dte.issuedEvent().rut()).isEqualTo(RUT);
        assertThat(dte.issuedEvent().dteId()).isEqualTo(dte.id());
        assertThat(dte.issuedEvent().occurredAt()).isEqualTo(ISSUED_AT);
    }

    @Test
    void issueWithoutFolioIsRejected() {
        assertThatThrownBy(() -> Dte.issue(ALPHA, null, RUT, Money.pesos(1000), ISSUED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sin folio");
    }

    @Test
    void issueRequiresPositiveNeto() {
        assertThatThrownBy(() -> Dte.issue(ALPHA, new Folio(1), RUT, Money.zero(), ISSUED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neto");
    }

    @Test
    void constructorRejectsWrongIva() {
        UUID id = UUID.randomUUID();
        DteIssued event = new DteIssued(UUID.randomUUID(), ISSUED_AT, ALPHA, id, new Folio(1), RUT);
        assertThatThrownBy(() -> new Dte(
                        id,
                        ALPHA,
                        new Folio(1),
                        RUT,
                        DocumentType.BOLETA_39,
                        Money.pesos(1000),
                        Money.pesos(100),
                        Money.pesos(1100),
                        DteStatus.ISSUED,
                        ISSUED_AT,
                        event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IVA");
    }

    @Test
    void constructorRejectsMismatchedEvent() {
        UUID id = UUID.randomUUID();
        DteIssued event = new DteIssued(
                UUID.randomUUID(), ISSUED_AT, new TenantId("beta"), id, new Folio(1), RUT);
        assertThatThrownBy(() -> new Dte(
                        id,
                        ALPHA,
                        new Folio(1),
                        RUT,
                        DocumentType.BOLETA_39,
                        Money.pesos(1000),
                        Money.pesos(190),
                        Money.pesos(1190),
                        DteStatus.ISSUED,
                        ISSUED_AT,
                        event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evento");
    }

    @Test
    void folioReservationThenIssueDoesNotUseMaxPlusOne() {
        FolioRange range = FolioRange.open(ALPHA, new Folio(50), new Folio(52));
        FolioReservation reservation = range.reserveNext();
        Dte dte = Dte.issue(ALPHA, reservation.folio(), RUT, Money.pesos(100), ISSUED_AT);
        assertThat(dte.folio().value()).isEqualTo(50);
        assertThat(reservation.range().next().value()).isEqualTo(51);
    }

    @Test
    void ivaOfOnePesoRoundsToZero() {
        Dte dte = Dte.issue(ALPHA, new Folio(1), RUT, Money.pesos(1), ISSUED_AT);
        assertThat(dte.iva()).isEqualTo(Money.zero());
        assertThat(dte.total()).isEqualTo(Money.pesos(1));
    }

    @Test
    void ivaOfThreePesosRoundsHalfUpToOne() {
        Dte dte = Dte.issue(ALPHA, new Folio(1), RUT, Money.pesos(3), ISSUED_AT);
        assertThat(dte.iva()).isEqualTo(Money.pesos(1));
        assertThat(dte.total()).isEqualTo(Money.pesos(4));
    }
}