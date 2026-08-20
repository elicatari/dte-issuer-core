package com.elicatari.dteissuer.application;

import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.application.port.out.DomainEventPublisher;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.application.port.out.FolioRangeRepository;
import com.elicatari.dteissuer.application.port.out.IdempotencyRecord;
import com.elicatari.dteissuer.application.port.out.IdempotencyStore;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.FolioRange;
import com.elicatari.dteissuer.domain.FolioReservation;
import com.elicatari.dteissuer.domain.NoFolioAvailableException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Un caso de uso: emitir Boleta 39. Persiste DTE, folio y clave en la misma
 * transacción local (la frontera transaccional la pone el adapter). Emite
 * {@code DteIssued}; no publica a Rabbit.
 *
 * <p>Idempotencia, scope {@code (tenant_id, idempotency_key)} + hash del body:
 * <ul>
 *   <li>misma clave, mismo body → el DTE original, un folio</li>
 *   <li>misma clave, body distinto → {@link IdempotencyConflictException}</li>
 *   <li>misma clave, otro tenant → otra clave; se emite</li>
 *   <li>carrera: uno adquiere la clave; el otro relee o
 *       {@link IdempotencyInProgressException}. Nunca un segundo folio</li>
 * </ul>
 */
public final class IssueDteUseCase {

    private final DteRepository dteRepository;
    private final FolioRangeRepository folioRangeRepository;
    private final IdempotencyStore idempotencyStore;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public IssueDteUseCase(
            DteRepository dteRepository,
            FolioRangeRepository folioRangeRepository,
            IdempotencyStore idempotencyStore,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        this.dteRepository = Objects.requireNonNull(dteRepository, "dteRepository");
        this.folioRangeRepository = Objects.requireNonNull(folioRangeRepository, "folioRangeRepository");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public IssueDteUseCase(
            DteRepository dteRepository,
            FolioRangeRepository folioRangeRepository,
            IdempotencyStore idempotencyStore,
            DomainEventPublisher eventPublisher) {
        this(dteRepository, folioRangeRepository, idempotencyStore, eventPublisher, Clock.systemUTC());
    }

    /**
     * Emite o relee según la clave. Debe ejecutarse dentro de una transacción
     * para que un fallo no deje la clave en curso ni un folio a medias.
     */
    public Dte execute(IssueDteCommand command) {
        Objects.requireNonNull(command, "el comando es obligatorio");
        String requestHash = CanonicalRequestHash.of(command);
        Optional<IdempotencyRecord> existing =
                idempotencyStore.tryClaim(command.tenantId(), command.idempotencyKey(), requestHash);
        if (existing.isEmpty()) {
            return issueNew(command);
        }
        return replayOrReject(command, requestHash, existing.get());
    }

    private Dte issueNew(IssueDteCommand command) {
        FolioRange range = folioRangeRepository
                .findByTenantId(command.tenantId())
                .orElseThrow(() -> new NoFolioAvailableException(command.tenantId()));
        FolioReservation reservation = range.reserveNext();
        folioRangeRepository.save(reservation.range());

        Dte dte = Dte.issue(
                command.tenantId(),
                reservation.folio(),
                command.rut(),
                command.neto(),
                clock.instant());
        dteRepository.save(dte);
        idempotencyStore.complete(command.tenantId(), command.idempotencyKey(), dte.id());
        eventPublisher.publish(dte.issuedEvent());
        return dte;
    }

    private Dte replayOrReject(IssueDteCommand command, String requestHash, IdempotencyRecord record) {
        if (!record.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(command.tenantId(), command.idempotencyKey());
        }
        if (record.inProgress()) {
            throw new IdempotencyInProgressException(command.tenantId(), command.idempotencyKey());
        }
        return dteRepository
                .findById(command.tenantId(), record.dteId())
                .orElseThrow(() -> new IllegalStateException(
                        "clave de idempotencia completada sin DTE " + record.dteId()));
    }
}