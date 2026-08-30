# Shared setup for the scripts in this directory. Sourced, never executed.
#
# Everything here is deliberately explicit about which database it is talking to. The dev and prod
# containers differ in name, port, database name and password, so a script that resolves the wrong
# one fails to connect rather than quietly operating on the wrong data.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DEV_CONTAINER="job-assistant-dev-db"
DEV_DATABASE="jobassistant_dev"
PROD_CONTAINER="job-assistant-prod-db"
PROD_DATABASE="jobassistant"
DB_ROLE="jobassistant"

PROD_JAR_HOME="${PROD_JAR_HOME:-$HOME/Applications/job-assistant}"
PROD_JAR="$PROD_JAR_HOME/job-assistant.jar"

# The tables whose loss cannot be undone by re-running migrations or re-polling the market. Used by
# the backup sidecar and the restore verification, so that "the dump restored" is checked against
# what the dump was supposed to contain rather than against nothing.
IRREPLACEABLE_TABLES=(
  profile profile_details profile_link profile_skill
  work_experience experience_bullet experience_bullet_skill
  education credential project project_skill language_skill cv_consent_clause
  job_offer application analysis generated_document
)

die() { printf 'error: %s\n' "$*" >&2; exit 1; }

# These scripts run unattended from launchd, where a bare non-zero exit leaves nothing but an exit
# code in `launchctl list`. Naming the line that failed costs one trap.
trap 'status=$?; printf "error: %s failed at line %s (exit %s)\n" "${BASH_SOURCE[0]}" "$LINENO" "$status" >&2' ERR
note() { printf '  %s\n' "$*" >&2; }
step() { printf '\n== %s\n' "$*" >&2; }

# Loads .env.prod into the environment. `set -a` exports every assignment, which is what compose and
# the jar both need. Values containing $HOME are expanded because .env.example writes them that way.
load_env() {
  local file="${1:-$REPO_ROOT/.env.prod}"
  [ -f "$file" ] || die "$file not found. Copy .env.example to .env.prod and fill it in."
  set -a
  # shellcheck disable=SC1090
  . "$file"
  set +a
}

require_env() {
  local name
  for name in "$@"; do
    [ -n "${!name:-}" ] || die "$name is empty. Set it in .env.prod."
  done
}

require_container() {
  local name="$1"
  docker inspect -f '{{.State.Running}}' "$name" 2>/dev/null | grep -qx true \
    || die "container $name is not running. Start it first (see docs/operations.md)."
}

# psql inside a container, quiet and unaligned so the output is parseable.
psql_q() {
  local container="$1" database="$2" sql="$3"
  docker exec -e PGPASSWORD="${DB_PASSWORD:-jobassistant}" "$container" \
    psql -U "$DB_ROLE" -d "$database" -tAqc "$sql"
}

# Row counts for IRREPLACEABLE_TABLES as "table<TAB>count" lines, sorted.
#
# query_to_xml is what makes this one round trip: a plain "select count(*) from t" is parsed before
# any WHERE can guard it, so a table missing from the schema would abort the whole statement rather
# than be skipped. Here a missing table simply produces no row, and the diff against the sidecar
# reports it as a difference instead of as a crashed script.
row_counts() {
  local container="$1" database="$2" list
  list=$(printf "'%s'," "${IRREPLACEABLE_TABLES[@]}")
  psql_q "$container" "$database" "
    select t,
           (xpath('/row/c/text()',
                  query_to_xml(format('select count(*) as c from %I', t), false, true, '')))[1]::text
    from unnest(array[${list%,}]) as t
    where to_regclass(t) is not null
    order by 1" | tr '|' '\t'
}
