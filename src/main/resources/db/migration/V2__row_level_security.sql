-- RLS: última capa. force aplica también al dueño; el runtime es dte_app, sin BYPASSRLS.
-- app.tenant_id se fija con set_config(..., true): local a la transacción, no a la sesión del pool.

alter table folio_ranges enable row level security;
alter table folio_ranges force row level security;
create policy tenant_isolation on folio_ranges
    using      (tenant_id = current_setting('app.tenant_id', true))
    with check (tenant_id = current_setting('app.tenant_id', true));

alter table dtes enable row level security;
alter table dtes force row level security;
create policy tenant_isolation on dtes
    using      (tenant_id = current_setting('app.tenant_id', true))
    with check (tenant_id = current_setting('app.tenant_id', true));

alter table idempotency_keys enable row level security;
alter table idempotency_keys force row level security;
create policy tenant_isolation on idempotency_keys
    using      (tenant_id = current_setting('app.tenant_id', true))
    with check (tenant_id = current_setting('app.tenant_id', true));