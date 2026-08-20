-- Schema compartido. El cursor de folio vive en folio_ranges.next_folio, no en MAX(dtes.folio).
-- El rol de runtime (${dte_app_role}) no es dueño: solo SELECT/INSERT/UPDATE.
-- RLS (force + app.tenant_id local a la transacción) está en V2.

create table folio_ranges (
    id          uuid        primary key,
    tenant_id   text        not null,
    folio_from  bigint      not null,
    folio_to    bigint      not null,
    next_folio  bigint      not null,
    constraint folio_ranges_tenant_unique unique (tenant_id),
    constraint folio_ranges_span_chk check (folio_from >= 1 and folio_from <= folio_to),
    constraint folio_ranges_cursor_chk check (next_folio >= folio_from and next_folio <= folio_to + 1)
);

create table dtes (
    id              uuid        primary key,
    tenant_id       text        not null,
    folio           bigint      not null,
    rut             text        not null,
    document_type   text        not null,
    neto            bigint      not null,
    iva             bigint      not null,
    total           bigint      not null,
    status          text        not null,
    issued_at       timestamptz not null,
    constraint dtes_tenant_folio_unique unique (tenant_id, folio),
    constraint dtes_folio_chk check (folio >= 1),
    constraint dtes_neto_chk check (neto > 0),
    constraint dtes_total_chk check (total = neto + iva),
    constraint dtes_type_chk check (document_type = 'BOLETA_39'),
    constraint dtes_status_chk check (status = 'ISSUED')
);

create table idempotency_keys (
    tenant_id         text        not null,
    idempotency_key   varchar(255) not null,
    request_hash      varchar(64)  not null,
    dte_id            uuid        null references dtes (id),
    constraint idempotency_keys_pkey primary key (tenant_id, idempotency_key)
);

comment on table folio_ranges is 'CAF simulado: un rango por tenant. next_folio es el cursor que se bloquea al emitir.';
comment on column folio_ranges.next_folio is 'Siguiente folio a asignar. No se deriva de MAX(dtes.folio).';
comment on table dtes is 'Boleta 39 emitida. Unicidad (tenant_id, folio): última defensa si el bloqueo falla.';
comment on table idempotency_keys is 'Scope (tenant_id, idempotency_key). request_hash distingue reintento de colisión de body.';
comment on column idempotency_keys.request_hash is 'SHA-256 hex del pedido canonicalizado.';
comment on column idempotency_keys.dte_id is 'Nulo mientras la emisión está en curso.';

-- CAF de demo: Alpha y Beta, rangos distintos (no se solapan).
insert into folio_ranges (id, tenant_id, folio_from, folio_to, next_folio) values
    ('a1fa0000-0000-4000-8000-000000000001', 'alpha', 1,    100,  1),
    ('b2fb0000-0000-4000-8000-000000000001', 'beta',  1000, 1099, 1000);

revoke all on table folio_ranges, dtes, idempotency_keys from public;
grant select, insert, update on table folio_ranges, dtes, idempotency_keys to ${dte_app_role};

alter default privileges in schema public
    grant select, insert, update on tables to ${dte_app_role};