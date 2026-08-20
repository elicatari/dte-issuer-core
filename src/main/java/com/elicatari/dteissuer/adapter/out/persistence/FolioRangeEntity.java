package com.elicatari.dteissuer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "folio_ranges")
@Filter(name = TenantFilters.NAME, condition = TenantFilters.CONDITION)
class FolioRangeEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "folio_from", nullable = false, updatable = false)
    private long folioFrom;

    @Column(name = "folio_to", nullable = false, updatable = false)
    private long folioTo;

    @Column(name = "next_folio", nullable = false)
    private long nextFolio;

    protected FolioRangeEntity() {}

    UUID id() {
        return id;
    }

    String tenantId() {
        return tenantId;
    }

    long folioFrom() {
        return folioFrom;
    }

    long folioTo() {
        return folioTo;
    }

    long nextFolio() {
        return nextFolio;
    }

    void setNextFolio(long nextFolio) {
        this.nextFolio = nextFolio;
    }
}