#!/bin/sh
set -eu

DATABASE_HOST="${DATABASE_HOST:-/var/run/postgresql}"
POSTGRES_USER="${POSTGRES_USER:-teremok_admin}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-teremok_admin}"
export PGPASSWORD="$POSTGRES_PASSWORD"

psql_admin() {
    psql --host "$DATABASE_HOST" --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" "$@"
}

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_roles WHERE rolname = 'catalog_service'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE USER catalog_service WITH PASSWORD 'catalog_service'"
fi
psql_admin --dbname postgres \
    --command "ALTER USER catalog_service WITH PASSWORD 'catalog_service'"

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_database WHERE datname = 'catalog_db'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE DATABASE catalog_db OWNER catalog_service"
fi
psql_admin --dbname postgres \
    --command "ALTER DATABASE catalog_db OWNER TO catalog_service"

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_roles WHERE rolname = 'text_work_content_service'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE USER text_work_content_service WITH PASSWORD 'text_work_content_service'"
fi
psql_admin --dbname postgres \
    --command "ALTER USER text_work_content_service WITH PASSWORD 'text_work_content_service'"

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_database WHERE datname = 'text_work_content_db'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE DATABASE text_work_content_db OWNER text_work_content_service"
fi
psql_admin --dbname postgres \
    --command "ALTER DATABASE text_work_content_db OWNER TO text_work_content_service"

psql_admin --dbname catalog_db --command "SET ROLE catalog_service" \
    --file /database/catalog-schema.sql
psql_admin --dbname text_work_content_db --command "SET ROLE text_work_content_service" \
    --file /database/text-work-content-schema.sql

echo "Service databases and tables are ready."
