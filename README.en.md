# DTE Issuer — Issuance, not a fiscal ERP

[Español](README.md)

[![CI](https://github.com/elicatari/dte-issuer-core/actions/workflows/ci.yml/badge.svg)](https://github.com/elicatari/dte-issuer-core/actions/workflows/ci.yml)

> DTE Issuer: one service, one invariant. No folio, no receipt; the POS can retry; Alpha cannot see Beta. Keycloak, hexagonal, event after commit.

**Chilean DTE issuance** microservice for the CoreTenant slot in the portfolio.
**Not** a SaaS platform and not a microservices architecture: one process, one
database, one HTTP contract so a POS/ERP (Automotriz, BR Logística, Happy Pet)
can issue receipts without knowing the SII.

> Pattern samples only. Not production software and not SII homologation.

## The value is not “e-invoicing”

```
Keycloak login (JWT with tenant_id)
  → POST /api/v1/dte + Idempotency-Key
  → Domain: no live folio, no DTE
  → Persist in the same transaction
  → After commit: publish DteIssued to dte.issued
```

That is not an `INSERT`. It is folio as a scarce resource, no duplicate on POS retry, and
Alpha does not use Beta’s folios or see Beta’s documents.

## Why this repo (not a fifth product)

The public samples already cover something else. Hexagonal design, ArchUnit,
idempotency and concurrency are **not** new here:
[`grooming-scheduler-api`](https://github.com/elicatari/grooming-scheduler-api)
already shows them. This repo matches that floor (JaCoCo 80/70 **enforced**, jqwik,
ArchUnit on the port) and closes what **no** sample covers: **Keycloak as an external
IdP**, **multi-tenancy with RLS**, **event after commit to RabbitMQ**, and **real
output ports** (not `*Api` facades over `JpaRepository`).

| Sample | What it shows | What it does not |
|--------|----------------|------------------|
| [`automotive-erp-core-api`](https://github.com/elicatari/automotive-erp-core-api) | Homegrown JWT, modular monolith | External IdP, multi-tenant, events |
| [`warehouse-wms-core-api`](https://github.com/elicatari/warehouse-wms-core-api) | FEFO, DRAFT → CONFIRMED documents | Multi-tenant, messaging |
| [`grooming-scheduler-api`](https://github.com/elicatari/grooming-scheduler-api) | Pure domain, concurrency, idempotency, ArchUnit | Keycloak, multi-tenant, messaging |

On the site ([elicatari.com](https://elicatari.com)) it occupies the CoreTenant slot.

## Eight files

| # | File | What it shows |
|---|------|----------------|
| 1 | [`Dte.java`](src/main/java/com/elicatari/dteissuer/domain/Dte.java), [`FolioRange.java`](src/main/java/com/elicatari/dteissuer/domain/FolioRange.java) | Pure Java: no folio, no DTE; reservation is one aggregate operation |
| 2 | [`IssueDteUseCase.java`](src/main/java/com/elicatari/dteissuer/application/IssueDteUseCase.java) | Orchestration + ports; does not publish to Rabbit |
| 3 | [`TenantContextFilter.java`](src/main/java/com/elicatari/dteissuer/shared/TenantContextFilter.java) | `tenant_id` only from the JWT; a tenant header is rejected |
| 4 | [`TenantIsolationIT.java`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/TenantIsolationIT.java) | Alpha issues; Beta GET → 404 / empty, also at the repository |
| 5 | [`HexagonalArchTest.java`](src/test/java/com/elicatari/dteissuer/HexagonalArchTest.java) | Hexagonal boundary; the port is not a `JpaRepository` |
| 6 | [`OutboxWriter.java`](src/main/java/com/elicatari/dteissuer/adapter/out/messaging/OutboxWriter.java) | Outbox in the same TX as the DTE; the relay publishes to `dte.issued` after commit |
| 7 | [`DteIssuedConsumerIT.java`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/DteIssuedConsumerIT.java) | An IT consumes the message; Rabbit is not decoration |
| 8 | [`docker-compose.yml`](docker-compose.yml) + [`realm-export.json`](realm-export.json) | One command, reproducible IdP (`user_alpha` / `user_beta`) |

Invariant extras: [`FolioConcurrencyIT`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/FolioConcurrencyIT.java) (N parallel POSTs, distinct folios, no gaps) and [`RlsIsolationIT`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/RlsIsolationIT.java) (native SQL as `dte_app`).

## Three isolation layers (not one)

| Layer | Where | What it covers |
|-------|-------|----------------|
| Tenant origin | JWT → [`TenantContext`](src/main/java/com/elicatari/dteissuer/shared/TenantContext.java) | Nobody picks their tenant; company switch = logout + login |
| Data access | `@Filter` + query with `tenant_id` + check on load | Current code. `@Filter` does **not** cover `find()` |
| Database | RLS + transaction-local `app.tenant_id` | Native queries, forgotten `findAll`, future code |
| Floor | unique `(tenant_id, folio)` | Stops duplicates only; does not isolate reads |

The app connects as `dte_app`, without `BYPASSRLS`, distinct from the table owner.
`set_config(..., true)`: a session-scoped setting would leak the tenant across pooled connections.

## `Idempotency-Key`

Scope `(tenant_id, idempotency_key)` + hash of the canonicalized body. Required.

| Case | Behaviour |
|------|-----------|
| Repeated key, same body | Same representation of the original DTE. One folio |
| Repeated key, different body | `409` ProblemDetail (`idempotency-conflict`). **Not** the old response in silence |
| Same key, different tenants | Distinct keys; each tenant issues |
| Two concurrent POSTs, same key | One wins. The other replays the DTE if the first already committed, or `409` (`idempotency-in-progress`) and retries. Never a second folio |

The race is resolved by database uniqueness, not by a prior `if exists`.

## Decisions

| Topic | Decision |
|-------|----------|
| One service, not a fleet | One process, one database, one HTTP contract. No Gateway and no second domain |
| Rabbit | Publish after commit (outbox). An IT consumes `dte.issued` |
| Folio | Pessimistic lock per tenant, never `MAX(folio)+1` |
| RLS | Transaction-local `app.tenant_id`; `dte_app` role without `BYPASSRLS` |

## What is not guaranteed

- **At-least-once.** Outbox in the same TX as the DTE. If Rabbit is down after commit, the poller retries. A possible resend is deduplicated with `eventId`. Not a saga and not a second process.
- **SII is a stub.** One document type: Boleta 39. No signed XML.
- Folio and document live in **this** database. Not a saga, not a distributed transaction.

## How to debug

Every authenticated request puts `tenant_id` and `X-Request-Id` in the MDC (the
correlation id is echoed on the response). “What did Beta do?”: filter by
`tenant_id=beta` and `request_id`. RUT is masked; the token and `Idempotency-Key`
are never logged in the clear.

## Stack

- Java 21, Spring Boot 4, Maven (`./mvnw -B verify` in [CI](https://github.com/elicatari/dte-issuer-core/actions/workflows/ci.yml))
- Hexagonal + tactical DDD (no Lombok or JPA in `domain`)
- Spring Security OAuth2 Resource Server (Keycloak)
- PostgreSQL + Flyway, shared schema + `tenant_id` + RLS
- RabbitMQ (outbox + publish `DteIssued` after commit)
- ArchUnit, Testcontainers, jqwik; JaCoCo 80% lines / 70% branches enforced on domain + application

## How to run

```bash
cp .env.example .env
docker compose up --build
```

When the API, Postgres, Keycloak and Rabbit are healthy (`dte-issued-echo` is a
log/echo of `dte.issued`, not a second service):

```bash
# Alpha token (tenant_id=alpha)
curl -s -X POST "http://localhost:8081/realms/dte/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=dte-demo" \
  -d "username=user_alpha" \
  -d "password=change-me"

# Beta token (tenant_id=beta)
curl -s -X POST "http://localhost:8081/realms/dte/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=dte-demo" \
  -d "username=user_beta" \
  -d "password=change-me"
```

The JWT payload (second segment, base64) has `"tenant_id": "alpha"` or `"beta"`.
The client does **not** send tenant in a header: it comes from the claim.

```bash
# Ready: 200. Anything else, without a token: 401.
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health/readiness
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/dte
```

Password grant is demo-only. The client is public with PKCE for a future SPA.

## Walk — Alpha issues, Beta cannot see

With the API, Postgres, Keycloak and Rabbit healthy. If `.env` predates the two Postgres roles, recopy
it: `DTE_APP_USER` must be present.

```bash
./scripts/demo-f2.sh
```

The script: Alpha login → POST Boleta 39 (`Idempotency-Key`) → replay (same DTE, one
folio) → GET 200; Beta login → GET 404 and a list without that row; native query as
`dte_app` with `app.tenant_id=beta` and no `WHERE tenant_id` (RLS). Tenant is **not**
sent in a header.

After the POST, the demo echo ([`DteIssuedEcho.java`](scripts/dte-issued-echo/src/main/java/DteIssuedEcho.java),
a Java `main` with no Spring) prints `DteIssued` (RUT masked). It does not replace
[`DteIssuedConsumerIT`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/DteIssuedConsumerIT.java):

```bash
docker compose logs -f dte-issued-echo
```

By hand (same contract):

```bash
TOKEN_ALPHA=$(curl -s -X POST "http://localhost:8081/realms/dte/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=dte-demo" \
  -d "username=user_alpha" -d "password=change-me" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

curl -s -X POST http://localhost:8080/api/v1/dte \
  -H "Authorization: Bearer $TOKEN_ALPHA" \
  -H "Idempotency-Key: demo-1" \
  -H "Content-Type: application/json" \
  -d '{"rut":"12.345.678-5","neto":1000}'

# Logout = a new token. Beta does not see Alpha’s id (404, not 403).
TOKEN_BETA=$(curl -s -X POST "http://localhost:8081/realms/dte/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=dte-demo" \
  -d "username=user_beta" -d "password=change-me" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN_BETA" \
  http://localhost:8080/api/v1/dte/<alpha-id>
```

## Omitted on purpose

Inventory / FEFO, scheduling / slot locks, an audit worker as a second domain,
Gateway, Eureka, Kubernetes, real certificate-signed XML, AI chat.

## How to talk about it

> DTE issuance microservice (Chile). One deploy, one database, HTTP contract for POS/ERP.
> Keycloak, hexagonal, event after commit to RabbitMQ. The rest of the SaaS lives in other systems.

Not: “a microservices architecture”.

## License / intent

Portfolio artifact. Do not treat `.env.example` secrets as real credentials.