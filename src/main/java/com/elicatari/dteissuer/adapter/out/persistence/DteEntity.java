package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.domain.DocumentType;
import com.elicatari.dteissuer.domain.DteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "dtes")
@Filter(name = TenantFilters.NAME, condition = TenantFilters.CONDITION)
class DteEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private long folio;

    @Column(nullable = false, updatable = false)
    private String rut;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    private DocumentType documentType;

    @Column(nullable = false, updatable = false)
    private long neto;

    @Column(nullable = false, updatable = false)
    private long iva;

    @Column(nullable = false, updatable = false)
    private long total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DteStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected DteEntity() {}

    DteEntity(
            UUID id,
            String tenantId,
            long folio,
            String rut,
            DocumentType documentType,
            long neto,
            long iva,
            long total,
            DteStatus status,
            Instant issuedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.folio = folio;
        this.rut = rut;
        this.documentType = documentType;
        this.neto = neto;
        this.iva = iva;
        this.total = total;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    UUID id() {
        return id;
    }

    String tenantId() {
        return tenantId;
    }

    long folio() {
        return folio;
    }

    String rut() {
        return rut;
    }

    DocumentType documentType() {
        return documentType;
    }

    long neto() {
        return neto;
    }

    long iva() {
        return iva;
    }

    long total() {
        return total;
    }

    DteStatus status() {
        return status;
    }

    Instant issuedAt() {
        return issuedAt;
    }
}