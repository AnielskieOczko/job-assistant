#!/usr/bin/env bash
#
# Starts the production application in the foreground.
#
#   scripts/run-prod.sh
#
# Takes a dump before starting. The jar runs Flyway on boot, so start-up is the moment the schema
# can change; at this database's size the snapshot costs under a second and makes a bad migration
# an inconvenience instead of a loss.

. "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

load_env
require_env DB_PASSWORD
require_container "$PROD_CONTAINER"
[ -f "$PROD_JAR" ] || die "$PROD_JAR not found. Build it with scripts/release-prod.sh."

"$(dirname "${BASH_SOURCE[0]}")/db-backup.sh" pre-launch > /dev/null

step "Starting job-assistant (prod) on http://127.0.0.1:8090"
exec java -jar "$PROD_JAR" --spring.profiles.active=prod
