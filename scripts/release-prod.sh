#!/usr/bin/env bash
#
# Builds a jar with the SPA inside and installs it outside target/.
#
#   scripts/release-prod.sh
#
# The copy is the point. target/ is wiped by every `./mvnw clean`, so a jar left there is an
# artifact that a routine dev command deletes out from under the running application.

. "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

step "Building (-Pfrontend, tests skipped - CI is where they run)"
cd "$REPO_ROOT"
./mvnw -B -Pfrontend clean package -DskipTests

built="$(ls -t "$REPO_ROOT"/target/job-assistant-*.jar 2>/dev/null | grep -v -- '-sources\|-javadoc' | head -1)"
[ -n "$built" ] || die "no jar in target/ after the build"

# A jar without the SPA starts and serves 404s for every page, which looks like a frontend bug
# rather than a build one. Cheaper to catch here.
#
# unzip's own pattern match rather than a pipe into grep: `set -o pipefail` is on, and grep -q
# exiting early sends unzip a SIGPIPE that fails the pipeline even when the file is there.
unzip -Z1 "$built" 'BOOT-INF/classes/static/index.html' > /dev/null 2>&1 \
  || die "$(basename "$built") contains no SPA - was -Pfrontend really active?"

mkdir -p "$PROD_JAR_HOME"
cp "$built" "$PROD_JAR"
step "Installed"
note "$PROD_JAR  ($(du -h "$PROD_JAR" | cut -f1))"
note "run it with scripts/run-prod.sh"
