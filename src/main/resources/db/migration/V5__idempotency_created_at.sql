-- Reserva en curso abandonada: created_at permite reclamar la clave tras el TTL.
-- La policy RLS de V2 no se toca.

alter table idempotency_keys
    add column created_at timestamptz not null default now();

comment on column idempotency_keys.created_at is
    'Inicio de la reserva. Si dte_id es nulo y created_at supera el TTL, el INSERT ON CONFLICT puede reclamarla.';