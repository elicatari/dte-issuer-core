package com.elicatari.dteissuer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elicatari.dteissuer.application.fake.InMemoryDteRepository;
import com.elicatari.dteissuer.application.fake.InMemoryFolioRangeRepository;
import com.elicatari.dteissuer.application.fake.InMemoryIdempotencyStore;
import com.elicatari.dteissuer.application.fake.RecordingDomainEventPublisher;
import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.application.port.out.IdempotencyRecord;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.FolioRange;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.NoFolioAvailableException;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssueDteUseCaseTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final TenantId BETA = new TenantId("beta");
    private static final Rut RUT = Rut.parse("12.345.678-5");
    private static final Instant NOW = Instant.parse("2026-08-18T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryDteRepository dtes;
    private InMemoryFolioRangeRepository ranges;
    private InMemoryIdempotencyStore keys;
    private RecordingDomainEventPublisher events;
    private IssueDteUseCase useCase;

    @BeforeEach
    void setUp() {
        dtes = new InMemoryDteRepository();
        ranges = new InMemoryFolioRangeRepository();
        keys = new InMemoryIdempotencyStore();
        events = new RecordingDomainEventPublisher();
        useCase = new IssueDteUseCase(dtes, ranges, keys, events, CLOCK);
        ranges.seed(FolioRange.open(ALPHA, new Folio(10), new Folio(12)));
        ranges.seed(FolioRange.open(BETA, new Folio(100), new Folio(102)));
    }

    @Test
    void executeRequiresCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void issuesBoleta39ReservesFolioAndPublishesDteIssued() {
        Dte dte = useCase.execute(command(ALPHA, "key-1", RUT, 1000));

        assertThat(dte.folio()).isEqualTo(new Folio(10));
        assertThat(dte.neto()).isEqualTo(Money.pesos(1000));
        assertThat(dte.iva()).isEqualTo(Money.pesos(190));
        assertThat(dte.total()).isEqualTo(Money.pesos(1190));
        assertThat(dte.issuedAt()).isEqualTo(NOW);
        assertThat(dtes.size()).isEqualTo(1);
        assertThat(ranges.findByTenantId(ALPHA).orElseThrow().next()).isEqualTo(new Folio(11));
        assertThat(events.events()).containsExactly(dte.issuedEvent());
        assertThat(dte.issuedEvent().tenantId()).isEqualTo(ALPHA);
        assertThat(dte.issuedEvent().folio()).isEqualTo(new Folio(10));
    }

    @Test
    void sameKeyAndSameBodyReturnsOriginalDteWithoutSecondFolio() {
        Dte first = useCase.execute(command(ALPHA, "key-1", RUT, 1000));
        Dte second = useCase.execute(command(ALPHA, "key-1", Rut.parse("12345678-5"), 1000));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.folio()).isEqualTo(first.folio());
        assertThat(dtes.size()).isEqualTo(1);
        assertThat(ranges.findByTenantId(ALPHA).orElseThrow().next()).isEqualTo(new Folio(11));
        assertThat(events.events()).hasSize(1);
    }

    @Test
    void sameKeyDifferentBodyIsConflictAndDoesNotReturnOldDte() {
        Dte original = useCase.execute(command(ALPHA, "key-1", RUT, 1000));

        assertThatThrownBy(() -> useCase.execute(command(ALPHA, "key-1", RUT, 2000)))
                .isInstanceOf(IdempotencyConflictException.class)
                .satisfies(ex -> {
                    IdempotencyConflictException conflict = (IdempotencyConflictException) ex;
                    assertThat(conflict.tenantId()).isEqualTo(ALPHA);
                    assertThat(conflict.key()).isEqualTo(new IdempotencyKey("key-1"));
                });

        assertThat(dtes.size()).isEqualTo(1);
        assertThat(dtes.findById(ALPHA, original.id()).orElseThrow().neto()).isEqualTo(Money.pesos(1000));
        assertThat(ranges.findByTenantId(ALPHA).orElseThrow().next()).isEqualTo(new Folio(11));
        assertThat(events.events()).hasSize(1);
    }

    @Test
    void differentKeyConsumesNextFolio() {
        Dte first = useCase.execute(command(ALPHA, "key-1", RUT, 1000));
        Dte second = useCase.execute(command(ALPHA, "key-2", RUT, 1000));

        assertThat(first.folio()).isEqualTo(new Folio(10));
        assertThat(second.folio()).isEqualTo(new Folio(11));
        assertThat(dtes.size()).isEqualTo(2);
        assertThat(events.events()).hasSize(2);
    }

    @Test
    void sameKeyOnDifferentTenantsAreIndependent() {
        Dte alpha = useCase.execute(command(ALPHA, "shared-key", RUT, 1000));
        Dte beta = useCase.execute(command(BETA, "shared-key", RUT, 1000));

        assertThat(alpha.folio()).isEqualTo(new Folio(10));
        assertThat(beta.folio()).isEqualTo(new Folio(100));
        assertThat(alpha.id()).isNotEqualTo(beta.id());
        assertThat(dtes.size()).isEqualTo(2);
    }

    @Test
    void exhaustedRangeFailsWithDomainExceptionAndDoesNotCreateDte() {
        ranges.seed(FolioRange.open(ALPHA, new Folio(1), new Folio(1)));
        useCase.execute(command(ALPHA, "first", RUT, 100));

        assertThatThrownBy(() -> useCase.execute(command(ALPHA, "second", RUT, 100)))
                .isInstanceOf(NoFolioAvailableException.class)
                .hasMessageContaining("alpha");

        assertThat(dtes.size()).isEqualTo(1);
        assertThat(ranges.findByTenantId(ALPHA).orElseThrow().exhausted()).isTrue();
        assertThat(events.events()).hasSize(1);
    }

    @Test
    void missingRangeFailsWithDomainException() {
        IssueDteUseCase isolated =
                new IssueDteUseCase(dtes, new InMemoryFolioRangeRepository(), keys, events, CLOCK);

        assertThatThrownBy(() -> isolated.execute(command(ALPHA, "key-1", RUT, 1000)))
                .isInstanceOf(NoFolioAvailableException.class);
        assertThat(dtes.size()).isZero();
        assertThat(events.events()).isEmpty();
    }

    @Test
    void inProgressSameBodyDoesNotIssue() {
        IssueDteCommand cmd = command(ALPHA, "key-1", RUT, 1000);
        keys.seed(IdempotencyRecord.started(ALPHA, "key-1", CanonicalRequestHash.of(cmd)));

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(IdempotencyInProgressException.class)
                .satisfies(ex -> {
                    IdempotencyInProgressException inProgress = (IdempotencyInProgressException) ex;
                    assertThat(inProgress.tenantId()).isEqualTo(ALPHA);
                    assertThat(inProgress.key()).isEqualTo(new IdempotencyKey("key-1"));
                });

        assertThat(dtes.size()).isZero();
        assertThat(ranges.findByTenantId(ALPHA).orElseThrow().next()).isEqualTo(new Folio(10));
        assertThat(events.events()).isEmpty();
    }

    @Test
    void inProgressDifferentBodyIsConflict() {
        keys.seed(IdempotencyRecord.started(ALPHA, "key-1", "hash-de-otro-body"));

        assertThatThrownBy(() -> useCase.execute(command(ALPHA, "key-1", RUT, 1000)))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(dtes.size()).isZero();
    }

    @Test
    void completedKeyWithoutDteIsIllegalState() {
        UUID missing = UUID.randomUUID();
        IssueDteCommand cmd = command(ALPHA, "key-1", RUT, 1000);
        keys.seed(IdempotencyRecord.started(ALPHA, "key-1", CanonicalRequestHash.of(cmd)).completed(missing));

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void defaultClockConstructorIssuesWithSystemTime() {
        IssueDteUseCase withSystemClock = new IssueDteUseCase(dtes, ranges, keys, events);
        Dte dte = withSystemClock.execute(command(ALPHA, "key-clock", RUT, 500));
        assertThat(dte.issuedAt()).isNotNull();
        assertThat(dte.folio()).isEqualTo(new Folio(10));
    }

    @Test
    void replayDoesNotSeeDteOfTheOtherTenant() {
        Dte alpha = useCase.execute(command(ALPHA, "key-1", RUT, 1000));
        keys.seed(IdempotencyRecord.started(BETA, "key-1", CanonicalRequestHash.of(command(BETA, "key-1", RUT, 1000)))
                .completed(alpha.id()));

        assertThatThrownBy(() -> useCase.execute(command(BETA, "key-1", RUT, 1000)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(dtes.findById(BETA, alpha.id())).isEmpty();
    }

    private static IssueDteCommand command(TenantId tenantId, String key, Rut rut, long neto) {
        return new IssueDteCommand(tenantId, new IdempotencyKey(key), rut, Money.pesos(neto));
    }
}