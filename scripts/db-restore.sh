#!/usr/bin/env bash
#
# Restores a dump into dev (the default) or, with an explicit flag and a typed confirmation, into
# production.
#
#   scripts/db-restore.sh <dump> [--into dev|prod]
#
# Restoring into prod overwrites the live profile and the whole application history, so it takes a
# fresh dump of its own first: the only way to lose data with this script is to be restoring
# because it was already lost.

. "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

dump="${1:-}"
[ -n "$dump" ] && [ -f "$dump" ] || die "usage: scripts/db-restore.sh <dump> [--into dev|prod]"
shift

into="dev"
if [ "${1:-}" = "--into" ]; then
  into="${2:-}"
  shift 2 || true
fi

case "$into" in
  dev)
    # No .env.prod is loaded here on purpose. Restoring into dev must not need the production
    # password anywhere near it, and the dev credentials are the compose defaults.
    container="$DEV_CONTAINER"; database="$DEV_DATABASE"; DB_PASSWORD="jobassistant"
    ;;
  prod)
    container="$PROD_CONTAINER"; database="$PROD_DATABASE"
    load_env
    require_env DB_PASSWORD
    require_container "$container"
    step "This will REPLACE the contents of the production database"
    note "dump:   $dump"
    note "target: $container / $database"
    printf 'Type the database name to confirm: ' >&2
    read -r typed
    [ "$typed" = "$database" ] || die "not confirmed"
    step "Taking a safety dump of the current production contents first"
    "$(dirname "${BASH_SOURCE[0]}")/db-backup.sh" pre-restore
    ;;
  *)
    die "--into takes dev or prod, not '$into'"
    ;;
esac

require_container "$container"

step "Restoring $(basename "$dump") into $container / $database"
# --clean --if-exists drops each object before recreating it, so restoring over a database that
# already has a schema (the usual case for dev) works without dropping the database itself.
docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$container" \
  pg_restore -U "$DB_ROLE" -d "$database" --clean --if-exists --no-owner < "$dump"

step "Result"
row_counts "$container" "$database"
