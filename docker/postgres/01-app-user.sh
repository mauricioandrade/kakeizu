#!/bin/sh
# Runs once, on an empty volume. The app logs in as an ordinary role that owns its database
# (so Flyway can create tables) but is not a superuser and cannot create roles or databases.
set -eu
psql -v ON_ERROR_STOP=1 -v user="$DB_USER" -v password="$DB_PASSWORD" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE :"user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'password';
ALTER DATABASE kakeizu OWNER TO :"user";
SQL
