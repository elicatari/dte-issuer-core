package com.elicatari.dteissuer.adapter.out.persistence;

final class TenantFilters {

    static final String NAME = "tenantFilter";
    static final String PARAM = "tenantId";
    static final String CONDITION = "tenant_id = :" + PARAM;

    private TenantFilters() {}
}