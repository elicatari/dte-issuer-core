#!/usr/bin/env bash
# Recorrido F2: Alpha emite, el reintento no duplica, Beta no ve esa fila.
# Requiere Compose healthy y python3.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

API="${API_BASE:-http://localhost:${API_PORT:-8080}}"
KC="${KEYCLOAK_BASE:-http://localhost:${KEYCLOAK_HTTP_PORT:-8081}}"
REALM="${KEYCLOAK_REALM:-dte}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-dte-demo}"
PASSWORD="${DEMO_USER_PASSWORD:-change-me}"
POSTGRES_DB="${POSTGRES_DB:-dte_issuer}"
BODY='{"rut":"12.345.678-5","neto":1000}'
LAST_BODY=""
LAST_CODE=""

if ! command -v python3 >/dev/null; then
  echo "hace falta python3 para leer JSON" >&2
  exit 1
fi

json() {
  printf '%s' "$LAST_BODY" | python3 -c "$1"
}

token_for() {
  local user="$1"
  local raw
  raw="$(curl -sS -X POST "${KC}/realms/${REALM}/protocol/openid-connect/token" \
    -d "grant_type=password" \
    -d "client_id=${CLIENT_ID}" \
    -d "username=${user}" \
    -d "password=${PASSWORD}")"
  printf '%s' "$raw" | python3 -c "
import json, sys
payload = json.load(sys.stdin)
token = payload.get('access_token')
if not token:
    raise SystemExit('no hay access_token para ${user}: ' + json.dumps(payload))
print(token)
"
}

request() {
  local tmp
  tmp="$(mktemp)"
  LAST_CODE="$(curl -sS -o "$tmp" -w '%{http_code}' "$@")"
  LAST_BODY="$(cat "$tmp")"
  rm -f "$tmp"
}

echo "== health =="
ready="$(curl -sS -o /dev/null -w '%{http_code}' "${API}/actuator/health/readiness" || true)"
if [[ "$ready" != "200" ]]; then
  echo "API no lista (readiness=${ready}). Copia .env.example a .env y corre: docker compose up --build" >&2
  exit 1
fi

echo "== Alpha: login + POST =="
TOKEN_ALPHA="$(token_for user_alpha)"
KEY="demo-f2-$(date +%s)"
request -X POST "${API}/api/v1/dte" \
  -H "Authorization: Bearer ${TOKEN_ALPHA}" \
  -H "Idempotency-Key: ${KEY}" \
  -H "Content-Type: application/json" \
  -d "${BODY}"
if [[ "$LAST_CODE" != "201" ]]; then
  echo "POST Alpha esperaba 201, fue ${LAST_CODE}: ${LAST_BODY}" >&2
  exit 1
fi
DTE_ID="$(json "import json,sys; print(json.load(sys.stdin)['id'])")"
FOLIO="$(json "import json,sys; print(json.load(sys.stdin)['folio'])")"
STATUS="$(json "import json,sys; print(json.load(sys.stdin)['status'])")"
if [[ "$STATUS" != "issued" ]]; then
  echo "el DTE no quedó issued: ${LAST_BODY}" >&2
  exit 1
fi
echo "emitido id=${DTE_ID} folio=${FOLIO}"

echo "== Alpha: replay misma key, mismo folio =="
request -X POST "${API}/api/v1/dte" \
  -H "Authorization: Bearer ${TOKEN_ALPHA}" \
  -H "Idempotency-Key: ${KEY}" \
  -H "Content-Type: application/json" \
  -d "${BODY}"
REPLAY_ID="$(json "import json,sys; print(json.load(sys.stdin)['id'])")"
REPLAY_FOLIO="$(json "import json,sys; print(json.load(sys.stdin)['folio'])")"
if [[ "$LAST_CODE" != "201" || "$REPLAY_ID" != "$DTE_ID" || "$REPLAY_FOLIO" != "$FOLIO" ]]; then
  echo "el replay no devolvió el mismo DTE (${LAST_CODE}): ${LAST_BODY}" >&2
  exit 1
fi

echo "== Alpha: GET por id =="
request "${API}/api/v1/dte/${DTE_ID}" -H "Authorization: Bearer ${TOKEN_ALPHA}"
if [[ "$LAST_CODE" != "200" ]]; then
  echo "GET Alpha esperaba 200, fue ${LAST_CODE}: ${LAST_BODY}" >&2
  exit 1
fi

echo "== Beta: login (otro token; el tenant no se manda en header) =="
TOKEN_BETA="$(token_for user_beta)"
request "${API}/api/v1/dte/${DTE_ID}" -H "Authorization: Bearer ${TOKEN_BETA}"
if [[ "$LAST_CODE" != "404" ]]; then
  echo "GET Beta del DTE de Alpha debe ser 404, no ${LAST_CODE}: ${LAST_BODY}" >&2
  exit 1
fi

request "${API}/api/v1/dte" -H "Authorization: Bearer ${TOKEN_BETA}"
if [[ "$LAST_CODE" != "200" ]]; then
  echo "listado Beta esperaba 200, fue ${LAST_CODE}: ${LAST_BODY}" >&2
  exit 1
fi
printf '%s' "$LAST_BODY" | python3 -c "
import json, sys
dte_id = sys.argv[1]
rows = json.load(sys.stdin)
if any(row.get('id') == dte_id for row in rows):
    raise SystemExit('el listado de Beta incluye el DTE de Alpha')
" "$DTE_ID"

echo "== RLS: query nativa como dte_app (tenant beta, sin WHERE tenant_id) =="
if docker compose exec -T postgres psql -U dte_app -d "${POSTGRES_DB}" -c 'select 1' >/dev/null 2>&1; then
  rls="$(docker compose exec -T postgres psql -U dte_app -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 -Atq <<SQL | tr -d '\r'
begin;
select case when exists (select 1 from dtes where id = '${DTE_ID}') then 'leak' else 'ok' end
from (select set_config('app.tenant_id', 'beta', true)) cfg;
commit;
SQL
)"
  if [[ "$rls" != "ok" ]]; then
    echo "la query nativa de Beta vio la fila de Alpha: ${rls}" >&2
    exit 1
  fi
  empty="$(docker compose exec -T postgres psql -U dte_app -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 -Atq -c "select count(*) from dtes;" | tr -d '\r')"
  if [[ "$empty" != "0" ]]; then
    echo "sin app.tenant_id RLS debe devolver cero filas, fue ${empty}" >&2
    exit 1
  fi
else
  echo "aviso: Postgres de Compose no disponible; se omitió la query nativa"
fi

echo "== DteIssued echo de demo (F3-T06, no sustituye DteIssuedConsumerIT) =="
if [[ -n "$(docker compose ps -q dte-issued-echo 2>/dev/null || true)" ]]; then
  found=0
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if docker compose logs dte-issued-echo 2>/dev/null | grep -q "${DTE_ID}"; then
      found=1
      break
    fi
    sleep 1
  done
  if [[ "$found" -eq 1 ]]; then
    echo "el echo de demo vio dteId=${DTE_ID}"
  else
    echo "aviso: el echo no mostró aún el evento; mira: docker compose logs dte-issued-echo" >&2
  fi
else
  echo "aviso: dte-issued-echo no está arriba"
fi

echo "F2 listo: Alpha emitió folio ${FOLIO}; Beta no ve esa fila."