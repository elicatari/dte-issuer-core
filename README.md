# DTE Issuer — Emisión, no un ERP fiscal

[English](README.en.md)

[![CI](https://github.com/elicatari/dte-issuer-core/actions/workflows/ci.yml/badge.svg)](https://github.com/elicatari/dte-issuer-core/actions/workflows/ci.yml)

> DTE Issuer: un servicio, un invariante. Sin folio no hay boleta, el POS puede reintentar, Alpha no ve a Beta. Keycloak, hexagonal, evento tras commit.

Microservicio de **emisión de DTE (Chile)** para el slot CoreTenant del portfolio.
**No** es una plataforma SaaS ni una arquitectura de microservicios: un proceso, una
base, un contrato HTTP para que un POS/ERP (Automotriz, BR Logística, Happy Pet)
emita boletas sin conocer el SII.

> Solo muestras de patrones. No es software de producción ni homologación SII.

## El valor no es “facturación electrónica”

```
Login Keycloak (JWT con tenant_id)
  → POST /api/v1/dte + Idempotency-Key
  → Dominio: sin folio vigente no hay DTE
  → Persistencia en la misma transacción
  → Tras commit: publicar DteIssued a dte.issued
```

Eso no es un `INSERT`. Es folio como recurso escaso, no duplicar si el POS reintenta, y
Alpha no usa los folios ni ve los documentos de Beta.

## Ocho archivos

| # | Archivo | Qué demuestra |
|---|---------|----------------|
| 1 | [`Dte.java`](src/main/java/com/elicatari/dteissuer/domain/Dte.java), [`FolioRange.java`](src/main/java/com/elicatari/dteissuer/domain/FolioRange.java) | Java puro: sin folio no se construye el DTE; la reserva es una operación del agregado |
| 2 | [`IssueDteUseCase.java`](src/main/java/com/elicatari/dteissuer/application/IssueDteUseCase.java) | Orquestación + puertos; no publica a Rabbit |
| 3 | [`TenantContextFilter.java`](src/main/java/com/elicatari/dteissuer/shared/TenantContextFilter.java) | `tenant_id` solo del JWT; un header de tenant se rechaza |
| 4 | [`TenantIsolationIT.java`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/TenantIsolationIT.java) | Alpha emite; Beta GET → 404 / vacío, también en el repositorio |
| 5 | [`HexagonalArchTest.java`](src/test/java/com/elicatari/dteissuer/HexagonalArchTest.java) | Frontera hexagonal; el puerto no es un `JpaRepository` |
| 6 | [`OutboxWriter.java`](src/main/java/com/elicatari/dteissuer/adapter/out/messaging/OutboxWriter.java) | Outbox en la misma TX que el DTE; el relay publica a `dte.issued` tras commit |
| 7 | [`DteIssuedConsumerIT.java`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/DteIssuedConsumerIT.java) | Un IT consume el mensaje; Rabbit no es adorno |
| 8 | [`docker-compose.yml`](docker-compose.yml) + [`realm-export.json`](realm-export.json) | Un comando, IdP reproducible (`user_alpha` / `user_beta`) |

Complementos de los invariantes: [`FolioConcurrencyIT`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/FolioConcurrencyIT.java) (N POST en paralelo, folios distintos sin huecos) y [`RlsIsolationIT`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/RlsIsolationIT.java) (SQL nativo como `dte_app`).

## Tres capas de aislamiento (no una)

| Capa | Dónde | Qué cubre |
|------|-------|-----------|
| Origen del tenant | JWT → [`TenantContext`](src/main/java/com/elicatari/dteissuer/shared/TenantContext.java) | Nadie elige su tenant; cambio de empresa = logout + login |
| Acceso a datos | `@Filter` + query con `tenant_id` + chequeo al cargar | El código actual. El `@Filter` **no** cubre `find()` |
| Base de datos | RLS + `app.tenant_id` local a la transacción | Queries nativas, `findAll` olvidados, código futuro |
| Suelo | unique `(tenant_id, folio)` | Solo evita duplicados; no aísla lecturas |

La app se conecta como `dte_app`, sin `BYPASSRLS` y distinto del dueño de las tablas.
`set_config(..., true)`: si fuera de sesión, el pool arrastraría el tenant a la petición siguiente.

## `Idempotency-Key`

Scope `(tenant_id, idempotency_key)` + hash del body canonicalizado. Obligatorio.

| Caso | Comportamiento |
|------|----------------|
| Key repetido, mismo body | Misma representación del DTE original. Un folio |
| Key repetido, body distinto | `409` ProblemDetail (`idempotency-conflict`). **No** la respuesta vieja en silencio |
| Mismo key, tenants distintos | Son claves distintas; cada tenant emite |
| Dos POST simultáneos, mismo key | Uno gana. El otro relee el DTE si el primero ya commitó, o `409` (`idempotency-in-progress`) y reintenta. Nunca un segundo folio |

La carrera se resuelve con la unicidad de la BD, no con un `if exists` previo.

Una reserva en curso abandonada (`created_at` más viejo que `dte.idempotency.in-progress-ttl-ms`, 60 s por defecto) se puede reclamar otra vez: mismo body emite; body distinto sigue siendo 409.

## Decisiones

| Tema | Decisión |
|------|----------|
| Un servicio, no una flota | Un proceso, una base, un contrato HTTP. No Gateway ni segundo dominio |
| Rabbit | Publish tras commit (outbox). Un IT consume `dte.issued` |
| Folio | Bloqueo pesimista por tenant, nunca `MAX(folio)+1` |
| RLS | `app.tenant_id` local a la transacción; rol `dte_app` sin `BYPASSRLS` |

## Qué no está garantizado

- **At-least-once.** Outbox en la misma TX que el DTE. Si Rabbit cae tras el commit, el poller reintenta con backoff. Un payload que falla siempre se entierra (`dead_lettered_at`) y no bloquea al resto. Un reenvío posible se deduplica con `eventId`. No es saga ni segundo proceso.
- **SII = stub.** Un solo tipo documental: Boleta 39. Sin XML firmado.
- Folio y documento viven en **esta** base. No es saga ni transacción distribuida.

## Cómo se depura

Cada petición autenticada lleva `tenant_id` y `X-Request-Id` en el MDC (y el id de
correlación vuelve en la respuesta). Los logs de consola salen en JSON ECS; el MDC
viaja como campos (`tenant_id`, `request_id`), no como texto embebido. Pregunta
“¿qué hizo Beta?”:

```bash
docker compose logs api | jq 'select(.tenant_id=="beta")'
```

Texto plano al desarrollar: `--spring.profiles.active=local`. El RUT va enmascarado;
el token y el `Idempotency-Key` no se loguean en claro.

`/actuator/prometheus` exige JWT (igual que el contrato HTTP). Métricas de negocio:
`dte_issued_total`, `dte_folio_reservation_seconds`, `dte_outbox_pending`,
`dte_outbox_lag_seconds`, `dte_outbox_dead_lettered_total`. Un muerto en el
outbox **no** pone `/actuator/health` en DOWN.

## Stack

- Java 21, Spring Boot 4, Maven (`./mvnw -B verify` en [CI](https://github.com/elicatari/dte-issuer-core/actions/workflows/ci.yml))
- Hexagonal + DDD táctico (sin Lombok ni JPA en `domain`)
- Spring Security OAuth2 Resource Server (Keycloak)
- OpenAPI generado (`/v3/api-docs`, `/swagger-ui.html`; JWT obligatorio, igual que el resto)
- Actuator: health público; Prometheus autenticado
- PostgreSQL + Flyway, schema compartido + `tenant_id` + RLS
- RabbitMQ (outbox + publish `DteIssued` tras commit)
- ArchUnit, Testcontainers, jqwik; JaCoCo 80% líneas / 70% ramas forzado sobre dominio + aplicación; `adapter/**` 70% / 40% (más bajo: más ramas de wiring). `shared/**` queda fuera del umbral (filtros JWT/MDC); no es que el gate solo mida lo fácil sin decirlo.
- Mutación PIT sobre `domain` en CI (`./mvnw -B -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage`, job paralelo a `verify` en PR y `push`, umbral 70%). No se ata a `verify`: JaCoCo y PIT no comparten lifecycle.

## Cómo levantar

```bash
cp .env.example .env
docker compose up --build
```

La imagen de `api` no corre como root (`id -u` es 10001) y declara `HEALTHCHECK` en el Dockerfile (Compose sigue comprobando readiness igual).

Cuando API, Postgres, Keycloak y Rabbit están healthy (`dte-issued-echo` es
log/echo de `dte.issued`, no un segundo servicio):

```bash
# Token Alpha (tenant_id=alpha)
curl -s -X POST "http://localhost:8081/realms/dte/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=dte-demo" \
  -d "username=user_alpha" \
  -d "password=change-me"

# Token Beta (tenant_id=beta)
curl -s -X POST "http://localhost:8081/realms/dte/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=dte-demo" \
  -d "username=user_beta" \
  -d "password=change-me"
```

El payload del JWT (segunda parte, base64) trae `"tenant_id": "alpha"` o `"beta"`.
El cliente **no** manda el tenant en un header: sale del claim.

```bash
# Listo: 200. El resto, sin token, 401.
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health/readiness
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/dte
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/v3/api-docs
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/prometheus
```

Password grant es solo para la demo. El cliente es público con PKCE para una SPA futura.

## Recorrido — Alpha emite, Beta no ve

Con API, Postgres, Keycloak y Rabbit healthy. Si el `.env` es anterior a los dos roles de
Postgres, recópialo: falta `DTE_APP_USER`.

```bash
./scripts/demo-f2.sh
```

El script: login Alpha → POST Boleta 39 (`Idempotency-Key`) → replay (mismo DTE, un
folio) → GET 200; login Beta → GET 404 y listado sin esa fila; query nativa como
`dte_app` con `app.tenant_id=beta` y sin `WHERE tenant_id` (RLS). El tenant **no**
viaja en un header.

Tras el POST, el echo de demo ([`DteIssuedEcho.java`](scripts/dte-issued-echo/src/main/java/DteIssuedEcho.java),
un `main` Java sin Spring) imprime `DteIssued` (RUT enmascarado). No sustituye
a [`DteIssuedConsumerIT`](src/test/java/com/elicatari/dteissuer/adapter/out/persistence/DteIssuedConsumerIT.java):

```bash
docker compose logs -f dte-issued-echo
```

A mano (mismo contrato):

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

# Logout = otro token. Beta no ve el id de Alpha (404, no 403).
TOKEN_BETA=$(curl -s -X POST "http://localhost:8081/realms/dte/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=dte-demo" \
  -d "username=user_beta" -d "password=change-me" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN_BETA" \
  http://localhost:8080/api/v1/dte/<id-de-alpha>
```

## Omitido a propósito

Inventario / FEFO, agenda / locks de horario, worker de auditoría como segundo dominio,
Gateway, Eureka, Kubernetes, XML firmado con certificado real, chat de IA.

## Licencia / intención

Artefacto de portafolio. No trates los secretos de `.env.example` como credenciales reales.