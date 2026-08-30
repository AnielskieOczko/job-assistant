#!/usr/bin/env bash
#
# Restores a dump into a throwaway database and checks that what came back matches what was
# recorded when the dump was taken. A backup that has never been restored is a file, not a backup;
# this is the script that makes the difference.
#
#   scripts/db-verify-restore.sh [dump]     # defaults to the newest dump in $BACKUP_DIR
#
# Exits non-zero on any mismatch, so launchd/CI can treat it as a check.

. "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

load_env
require_env DB_PASSWORD BACKUP_DIR
require_container "$PROD_CONTAINER"

dump="${1:-$(ls -t "$BACKUP_DIR"/jobassistant-*.dump 2>/dev/null | head -1)}"
[ -n "$dump" ] && [ -f "$dump" ] || die "no dump to verify (looked in $BACKUP_DIR)"
[ -f "$dump.counts" ] || die "$dump has no .counts sidecar - it was not written by db-backup.sh"

scratch="jobassistant_restorecheck"
# The scratch database lives in the prod container because that is where the dump's role and
# extensions exist. It is dropped on the way in as well as on the way out, so an interrupted run
# leaves nothing that would make the next one fail.
cleanup() {
  psql_q "$PROD_CONTAINER" postgres "drop database if exists $scratch with (force)" > /dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

step "Restoring $(basename "$dump") into $scratch"
psql_q "$PROD_CONTAINER" postgres "create database $scratch owner $DB_ROLE" > /dev/null
docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$PROD_CONTAINER" \
  pg_restore -U "$DB_ROLE" -d "$scratch" --no-owner --exit-on-error < "$dump"

step "Comparing row counts against $(basename "$dump").counts"
actual="$(row_counts "$PROD_CONTAINER" "$scratch")"
if diff <(cat "$dump.counts") <(printf '%s\n' "$actual"); then
  # A dump of an empty database also restores cleanly and matches its own sidecar, which would be a
  # green check over nothing at all. The profile is the reason this database exists.
  profile_rows="$(psql_q "$PROD_CONTAINER" "$scratch" "select count(*) from profile_details")"
  [ "${profile_rows:-0}" -gt 0 ] || die "restore matched its sidecar, but profile_details is empty"
  migrations="$(psql_q "$PROD_CONTAINER" "$scratch" "select count(*) from flyway_schema_history")"
  printf '\nPASS  %s  (%s migrations, %s profile rows)\n' "$(basename "$dump")" "$migrations" "$profile_rows"
else
  printf '\nFAIL  %s  restored contents differ from what was recorded at dump time\n' "$(basename "$dump")" >&2
  exit 1
fi
