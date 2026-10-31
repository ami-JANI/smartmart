#!/bin/bash
# Starts the read-only replica. On first start it copies the primary with
# pg_basebackup; -R writes the settings that make this server a standby
# that streams changes from the primary.
set -euo pipefail

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "Cloning primary ${PRIMARY_HOST} ..."
  export PGPASSWORD="$REPLICATION_PASSWORD"
  pg_basebackup -h "$PRIMARY_HOST" -U "$REPLICATION_USER" -D "$PGDATA" \
    -R -X stream -S replica_1 -P
  chmod 700 "$PGDATA"
fi

exec postgres
