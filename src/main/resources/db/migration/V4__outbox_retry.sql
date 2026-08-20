-- Reintentos con backoff y muertos. La policy RLS de V3 no se toca.
-- next_attempt_at nulo no existe: el claim filtra por <= now.

alter table outbox
    add column attempts int not null default 0,
    add column next_attempt_at timestamptz not null default now(),
    add column last_error text,
    add column dead_lettered_at timestamptz;

update outbox
    set next_attempt_at = occurred_at
    where published_at is null;

drop index outbox_unpublished_idx;
create index outbox_unpublished_idx
    on outbox (next_attempt_at, occurred_at)
    where published_at is null and dead_lettered_at is null;

comment on column outbox.attempts is 'Publicaciones fallidas. Al llegar al máximo, dead_lettered_at.';
comment on column outbox.next_attempt_at is 'El poller solo reclama si es <= now. Tras un fallo, now + backoff.';
comment on column outbox.last_error is 'Último fallo de publish o deserialización. Truncado en aplicación.';
comment on column outbox.dead_lettered_at is 'Nulo = vivo. Con valor, el poller no lo vuelve a tomar.';