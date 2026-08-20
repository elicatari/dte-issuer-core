package com.elicatari.dteissuer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.elicatari.dteissuer.application.fake.InMemoryDteRepository;
import com.elicatari.dteissuer.application.fake.InMemoryFolioRangeRepository;
import com.elicatari.dteissuer.application.fake.InMemoryIdempotencyStore;
import com.elicatari.dteissuer.application.fake.RecordingDomainEventPublisher;
import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.FolioRange;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class IssueDteUseCaseConcurrencyTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final Rut RUT = Rut.parse("12.345.678-5");
    private static final int THREADS = 8;

    @Test
    @Timeout(10)
    void concurrentSameKeyConsumesASingleFolio() throws Exception {
        InMemoryDteRepository dtes = new InMemoryDteRepository();
        InMemoryFolioRangeRepository ranges = new InMemoryFolioRangeRepository();
        InMemoryIdempotencyStore keys = new InMemoryIdempotencyStore();
        RecordingDomainEventPublisher events = new RecordingDomainEventPublisher();
        IssueDteUseCase useCase = new IssueDteUseCase(
                dtes, ranges, keys, events, Clock.fixed(Instant.parse("2026-08-18T18:00:00Z"), ZoneOffset.UTC));
        ranges.seed(FolioRange.open(ALPHA, new Folio(1), new Folio(20)));

        IssueDteCommand command =
                new IssueDteCommand(ALPHA, new IdempotencyKey("same-key"), RUT, Money.pesos(1000));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Callable<Dte>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return executeRetryingInProgress(useCase, command);
            });
        }

        List<Future<Dte>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        List<Dte> issued = new ArrayList<>();
        for (Future<Dte> future : futures) {
            issued.add(future.get(5, TimeUnit.SECONDS));
        }

        List<UUID> ids = issued.stream().map(Dte::id).distinct().toList();
        assertThat(ids).hasSize(1);
        assertThat(issued.stream().map(Dte::folio).distinct()).containsExactly(new Folio(1));
        assertThat(dtes.size()).isEqualTo(1);
        assertThat(ranges.findByTenantId(ALPHA).orElseThrow().next()).isEqualTo(new Folio(2));
        assertThat(events.events()).hasSize(1);
    }

    private static Dte executeRetryingInProgress(IssueDteUseCase useCase, IssueDteCommand command) {
        for (int attempt = 0; attempt < 64; attempt++) {
            try {
                return useCase.execute(command);
            } catch (IdempotencyInProgressException ignored) {
                Thread.onSpinWait();
            }
        }
        throw new AssertionError("la clave siguió en curso; no hubo segundo folio pero el ganador no releyó");
    }
}