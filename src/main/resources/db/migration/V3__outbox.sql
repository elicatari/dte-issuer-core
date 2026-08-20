-- Outbox transaccional: la fila nace con el DTE. No es dual-write (ADR 0002).
-- RLS igual que el resto; el poller in-process usa app.outbox_relay local a su TX.
-- No es un segundo proceso ni BYPASSRLS.

create table outbox (
    id              uuid        primary key,
    tenant_id       text        not null,
    event_name      text        not null,
    event_version   text        not null,
    payload         text        not null,
    occurred_at     timestamptz not null,
    published_at    timestamptz null
);

create index outbox_unpublished_idx
    on outbox (occurred_at)
    where published_at is null;

comment on table outbox is 'DteIssued en la misma TX que el DTE. El relay publica a Rabbit y marca published_at.';
comment on column outbox.id is 'eventId del hecho de dominio; sirve para deduplicar en el consumidor.';
comment on column outbox.published_at is 'Nulo = pendiente. El relay lo rellena tras un convertAndSend exitoso.';

revoke all on table outbox from public;
grant select, insert, update on table outbox to ${dte_app_role};

alter table outbox enable row level security;
alter table outbox force row level security;
create policy tenant_isolation on outbox
    using      (
        tenant_id = current_setting('app.tenant_id', true)
        or current_setting('app.outbox_relay', true) = 'true'
    )
    with check (
        tenant_id = current_setting('app.tenant_id', true)
        or current_setting('app.outbox_relay', true) = 'true'
    );