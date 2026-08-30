#!/usr/bin/env bash
#
# Dumps the production database to $BACKUP_DIR, mirrors the dump to $BACKUP_MIRROR, and prunes old
# ones. Safe to run at any time, including while the application is running - pg_dump takes a
# consistent snapshot and blocks nothing.
#
#   scripts/db-backup.sh [label]
#
# The optional label is appended to the filename (scripts/run-prod.sh passes "pre-launch"), so a
# snapshot taken for a reason is findable later among the nightly ones.

. "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

load_env
require_env DB_PASSWORD BACKUP_DIR
require_container "$PROD_CONTAINER"

label="${1:-nightly}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
name="jobassistant-${stamp}-${label}.dump"
mkdir -p "$BACKUP_DIR"
target="$BACKUP_DIR/$name"

step "Dumping $PROD_DATABASE -> $target"
# No -t: a TTY would corrupt the binary custom-format stream on its way out of the container.
docker exec -e PGPASSWORD="$DB_PASSWORD" "$PROD_CONTAINER" \
  pg_dump -U "$DB_ROLE" -d "$PROD_DATABASE" -Fc > "$target"

# The sidecar is what makes scripts/db-verify-restore.sh meaningful. Counts taken now, against the
# database the dump was just read from, give the restore something to be checked against; comparing
# a restore to the *live* database instead would report every row added since as a failure.
row_counts "$PROD_CONTAINER" "$PROD_DATABASE" > "$target.counts"

# Read the archive's index back before calling the dump done - a truncated write is otherwise
# indistinguishable from a good one until the day it is needed. Run inside the container because
# the host has no Postgres client tools installed; pg_restore reads a custom-format archive from
# stdin happily.
docker exec -i "$PROD_CONTAINER" pg_restore --list < "$target" > /dev/null \
  || die "$target is not a readable pg_dump archive"
note "$(du -h "$target" | cut -f1)  $(wc -l < "$target.counts" | tr -d ' ') tables recorded"

if [ -n "${BACKUP_MIRROR:-}" ]; then
  step "Mirroring to $BACKUP_MIRROR"
  mkdir -p "$BACKUP_MIRROR"
  cp "$target" "$target.counts" "$BACKUP_MIRROR/"
  note "ok"
else
  note "BACKUP_MIRROR is empty - this dump exists on one disk only."
fi

# Prune, but never below BACKUP_KEEP_MINIMUM. A machine left off for two months should come back to
# stale backups, not to none: age is a reason to prefer a newer copy, never a reason to hold zero.
retention="${BACKUP_RETENTION_DAYS:-30}"
keep="${BACKUP_KEEP_MINIMUM:-7}"
step "Pruning dumps older than ${retention}d (always keeping the newest ${keep})"

prune() {
  local dir="$1" old
  local -a dumps=()
  # Collected through process substitution rather than a pipeline: `set -o pipefail` plus a `while
  # read` that reaches EOF makes an empty result look like a failure, which is the normal case here.
  while IFS= read -r old; do dumps+=("$old"); done < <(ls -t "$dir"/jobassistant-*.dump 2>/dev/null)
  [ "${#dumps[@]}" -gt "$keep" ] || return 0
  for old in "${dumps[@]:$keep}"; do
    # -maxdepth before the test, which BSD find requires; -mtime +N is "older than N days".
    if [ -n "$(find "$old" -maxdepth 0 -mtime "+$retention" -print 2>/dev/null)" ]; then
      rm -f "$old" "$old.counts"
      note "removed $(basename "$old")"
    fi
  done
}

prune "$BACKUP_DIR"
[ -z "${BACKUP_MIRROR:-}" ] || prune "$BACKUP_MIRROR"

printf '\n%s\n' "$target"
