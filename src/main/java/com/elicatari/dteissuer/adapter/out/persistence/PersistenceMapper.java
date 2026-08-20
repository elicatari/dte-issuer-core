package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.port.out.IdempotencyRecord;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.DteIssued;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.FolioRange;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;

final class PersistenceMapper {

    private PersistenceMapper() {}

    static DteEntity toEntity(Dte dte) {
        return new DteEntity(
                dte.id(),
                dte.tenantId().value(),
                dte.folio().value(),
                dte.rut().value(),
                dte.documentType(),
                dte.neto().amount().longValueExact(),
                dte.iva().amount().longValueExact(),
                dte.total().amount().longValueExact(),
                dte.status(),
                dte.issuedAt());
    }

    static Dte toDomain(DteEntity entity) {
        TenantId tenantId = new TenantId(entity.tenantId());
        Folio folio = new Folio(entity.folio());
        Rut rut = Rut.parse(entity.rut());
        // V1 no guarda event_id; se reconstruye estable para el invariante del agregado.
        DteIssued event = new DteIssued(entity.id(), entity.issuedAt(), tenantId, entity.id(), folio, rut);
        return new Dte(
                entity.id(),
                tenantId,
                folio,
                rut,
                entity.documentType(),
                Money.pesos(entity.neto()),
                Money.pesos(entity.iva()),
                Money.pesos(entity.total()),
                entity.status(),
                entity.issuedAt(),
                event);
    }

    static FolioRange toDomain(FolioRangeEntity entity) {
        return new FolioRange(
                entity.id(),
                new TenantId(entity.tenantId()),
                new Folio(entity.folioFrom()),
                new Folio(entity.folioTo()),
                new Folio(entity.nextFolio()));
    }

    static IdempotencyRecord toRecord(IdempotencyKeyEntity entity) {
        return new IdempotencyRecord(
                new TenantId(entity.pk().tenantId()),
                entity.pk().key(),
                entity.requestHash(),
                entity.dteId());
    }
}