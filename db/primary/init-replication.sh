#!/bin/bash
# Runs once, when the primary's data directory is first created.
# Creates the replication user, a replication slot for the replica, and
# allows the replica to connect for replication.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE ${REPLICATION_USER} WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';
SELECT pg_create_physical_replication_slot('replica_1');
SQL

echo "host replication ${REPLICATION_USER} all scram-sha-256" >> "$PGDATA/pg_hba.conf"
