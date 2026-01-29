#!/bin/bash
# PostgreSQL init script - creates additional databases on container startup
# Place this in docker/postgres/ and mount in docker-compose
# This runs automatically when postgres container starts for the FIRST time

set -e

echo "Creating additional databases..."

# Create OpenFGA database if it doesn't exist
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE openfga'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'openfga')\gexec
    GRANT ALL PRIVILEGES ON DATABASE openfga TO $POSTGRES_USER;
EOSQL

# Create Personal Shared database if it doesn't exist (name from env)
# This database is used for logical tenant separation for personal users
if [ -n "$PERSONAL_POSTGRES_DB" ]; then
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        SELECT 'CREATE DATABASE ${PERSONAL_POSTGRES_DB}'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${PERSONAL_POSTGRES_DB}')\gexec
        GRANT ALL PRIVILEGES ON DATABASE ${PERSONAL_POSTGRES_DB} TO $POSTGRES_USER;
EOSQL
    echo "Personal shared database '${PERSONAL_POSTGRES_DB}' ready!"
else
    echo "PERSONAL_POSTGRES_DB not set, skipping personal shared database creation"
fi

echo "All databases ready!"
