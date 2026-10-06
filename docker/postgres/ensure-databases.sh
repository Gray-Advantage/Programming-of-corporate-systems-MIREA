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
    --command "SELECT 1 FROM pg_roles WHERE rolname = 'auth_service'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE USER auth_service WITH PASSWORD 'auth_service'"
fi
psql_admin --dbname postgres \
    --command "ALTER USER auth_service WITH PASSWORD 'auth_service'"

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_database WHERE datname = 'auth_db'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE DATABASE auth_db OWNER auth_service"
fi
psql_admin --dbname postgres \
    --command "ALTER DATABASE auth_db OWNER TO auth_service"

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

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_roles WHERE rolname = 'draft_recordings_service'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE USER draft_recordings_service WITH PASSWORD 'draft_recordings_service'"
fi
psql_admin --dbname postgres \
    --command "ALTER USER draft_recordings_service WITH PASSWORD 'draft_recordings_service'"

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_database WHERE datname = 'draft_recordings_db'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE DATABASE draft_recordings_db OWNER draft_recordings_service"
fi
psql_admin --dbname postgres \
    --command "ALTER DATABASE draft_recordings_db OWNER TO draft_recordings_service"

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_roles WHERE rolname = 'recordings_service'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE USER recordings_service WITH PASSWORD 'recordings_service'"
fi
psql_admin --dbname postgres \
    --command "ALTER USER recordings_service WITH PASSWORD 'recordings_service'"

if [ "$(psql_admin --dbname postgres --tuples-only --no-align \
    --command "SELECT 1 FROM pg_database WHERE datname = 'recordings_db'")" != "1" ]; then
    psql_admin --dbname postgres \
        --command "CREATE DATABASE recordings_db OWNER recordings_service"
fi
psql_admin --dbname postgres \
    --command "ALTER DATABASE recordings_db OWNER TO recordings_service"

psql_admin --dbname auth_db --command "SET ROLE auth_service" \
    --file /database/auth-schema.sql
psql_admin --dbname catalog_db --command "SET ROLE catalog_service" \
    --file /database/catalog-schema.sql
psql_admin --dbname draft_recordings_db --command "SET ROLE draft_recordings_service" \
    --file /database/draft-recordings-schema.sql
psql_admin --dbname recordings_db --command "SET ROLE recordings_service" \
    --file /database/recordings-schema.sql
psql_admin --dbname text_work_content_db --command "SET ROLE text_work_content_service" \
    --file /database/text-work-content-schema.sql

echo "Service databases and tables are ready."
