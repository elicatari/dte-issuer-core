#!/usr/bin/env bash
# Rol de runtime (dte_app): sin BYPASSRLS, no superusuario, no dueño de tablas.
# Credenciales solo desde el entorno; Flyway no crea este rol ni escribe el password.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL
  create role ${DTE_APP_USER} login password '${DTE_APP_PASSWORD}'
    nosuperuser nocreatedb nocreaterole nobypassrls;
  grant connect on database ${POSTGRES_DB} to ${DTE_APP_USER};
  grant usage on schema public to ${DTE_APP_USER};
EOSQL