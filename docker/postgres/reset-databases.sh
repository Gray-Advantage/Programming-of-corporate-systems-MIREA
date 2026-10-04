#!/bin/sh
set -eu

DATABASE_HOST="${DATABASE_HOST:-/var/run/postgresql}"
POSTGRES_USER="${POSTGRES_USER:-teremok_admin}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-teremok_admin}"
export PGPASSWORD="$POSTGRES_PASSWORD"

sh /database/ensure-databases.sh >/dev/null

psql --host "$DATABASE_HOST" --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname catalog_db <<-SQL
    DROP SCHEMA public CASCADE;
    CREATE SCHEMA public AUTHORIZATION catalog_service;
SQL
psql --host "$DATABASE_HOST" --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname catalog_db \
    --command "SET ROLE catalog_service" \
    --file /database/catalog-schema.sql

psql --host "$DATABASE_HOST" --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname text_work_content_db <<-SQL
    DROP SCHEMA public CASCADE;
    CREATE SCHEMA public AUTHORIZATION text_work_content_service;
SQL
psql --host "$DATABASE_HOST" --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname text_work_content_db \
    --command "SET ROLE text_work_content_service" \
    --file /database/text-work-content-schema.sql

echo "Service databases were reset and empty tables were recreated."
