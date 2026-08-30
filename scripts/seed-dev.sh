#!/usr/bin/env bash
#
# Loads the synthetic development profile into a running dev instance.
#
#   scripts/seed-dev.sh
#
# Pointed at 127.0.0.1:8080 with no way to override, because the one thing this script must never
# do is replace a profile in production. POST .../import is a full replace, so re-running it is
# idempotent: the fixture profile is refreshed, never duplicated.

. "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

DEV_BASE="http://127.0.0.1:8080"
FIXTURE_PROFILE_NAME="Dev Fixture"

fixture="$REPO_ROOT/docs/fixtures/dev-profile.json"
[ -f "$fixture" ] || die "$fixture not found"

curl -sSf "$DEV_BASE/actuator/health" > /dev/null 2>&1 \
  || die "nothing answering on $DEV_BASE - start the dev app with ./mvnw spring-boot:run"

step "Finding or creating the '$FIXTURE_PROFILE_NAME' profile"
profile_id="$(curl -sSf "$DEV_BASE/api/profiles" \
  | jq -r --arg n "$FIXTURE_PROFILE_NAME" 'map(select(.name == $n)) | .[0].id // empty')"

if [ -z "$profile_id" ]; then
  profile_id="$(curl -sSf -X POST "$DEV_BASE/api/profiles" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n --arg n "$FIXTURE_PROFILE_NAME" '{name: $n}')" | jq -r '.id')"
  note "created profile $profile_id"
  curl -sSf -X PUT "$DEV_BASE/api/profiles/$profile_id/default" > /dev/null
else
  note "reusing profile $profile_id"
fi

step "Importing $(basename "$fixture")"
response="$(mktemp)"
trap 'rm -f "$response"' EXIT
status="$(curl -sS -o "$response" -w '%{http_code}' \
  -X POST "$DEV_BASE/api/profiles/$profile_id/import" \
  -H 'Content-Type: application/json' --data-binary "@$fixture")"

case "$status" in
  2*) note "HTTP $status - $(jq -r '[.skills // [] | length] | "\(.[0]) skills"' < "$response" 2>/dev/null || echo ok)" ;;
  # A 400 here is almost always a skill name the catalog cannot resolve. Import rejects rather than
  # dropping it, so the response names what failed - print it rather than a generic failure.
  *)  cat "$response" >&2; die "import failed with HTTP $status" ;;
esac
