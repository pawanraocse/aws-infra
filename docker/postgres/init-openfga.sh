#!/bin/bash
# PostgreSQL init script - creates additional databases on container startup
# Place this in docker/postgres/ and mount in docker-compose
# This runs automatically when postgres container starts for the FIRST time

set -e

echo "Checking for OpenFGA database..."

# Create OpenFGA database if it doesn't exist
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE openfga'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'openfga')\gexec
    GRANT ALL PRIVILEGES ON DATABASE openfga TO $POSTGRES_USER;
EOSQL

echo "OpenFGA database ready!"
