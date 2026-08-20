/**
 * Persistencia JPA. El dominio no conoce este paquete.
 * {@code @Filter} de tenant + query con {@code tenant_id} + chequeo al cargar.
 * RLS ({@code app.tenant_id} local a la transacción) cubre lo que el filtro no ve.
 */
@org.hibernate.annotations.FilterDef(
        name = com.elicatari.dteissuer.adapter.out.persistence.TenantFilters.NAME,
        parameters =
                @org.hibernate.annotations.ParamDef(
                        name = com.elicatari.dteissuer.adapter.out.persistence.TenantFilters.PARAM,
                        type = String.class))
package com.elicatari.dteissuer.adapter.out.persistence;