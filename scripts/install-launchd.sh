#!/usr/bin/env bash
#
# Installs (or reinstalls) the nightly backup and monthly restore-drill launchd agents.
#
#   scripts/install-launchd.sh            # install / reload / refresh the copy
#   scripts/install-launchd.sh --remove   # unload, and delete both the agents and the copy
#
# The agents do not run the scripts in this checkout. A launchd job holds no TCC permission for
# /Volumes, so a plist pointing at a repository on a secondary disk dies with "Operation not
# permitted" before bash even starts - and it does so into a log nobody is watching. Instead the
# three files the agents need are copied into ~/Library/Application Support/job-assistant, which a
# background job can always read.
#
# The cost of that is a second copy that can go stale. Re-run this script after editing
# scripts/db-backup.sh, scripts/db-verify-restore.sh, scripts/_common.sh or .env.prod; it prints
# what it copied so the sync is visible rather than assumed.
#
# The alternative is granting Full Disk Access to /bin/bash in System Settings, which would let the
# agents run this checkout directly. That is a much broader permission than this job needs.

. "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

AGENT_HOME="$HOME/Library/Application Support/job-assistant"
AGENTS="$HOME/Library/LaunchAgents"
LABELS=(com.jobassistant.backup com.jobassistant.verify-restore)
# _common.sh resolves REPO_ROOT as the parent of the directory it sits in, and load_env reads
# $REPO_ROOT/.env.prod - so the copy needs no code changes, only this layout.
AGENT_FILES=(_common.sh db-backup.sh db-verify-restore.sh)

if [ "${1:-}" = "--remove" ]; then
  for label in "${LABELS[@]}"; do
    launchctl bootout "gui/$UID/$label" 2>/dev/null || true
    rm -f "$AGENTS/$label.plist"
    note "removed $label"
  done
  rm -rf "$AGENT_HOME"
  note "removed $AGENT_HOME"
  exit 0
fi

load_env
require_env BACKUP_DIR

step "Copying what the agents run into $AGENT_HOME"
mkdir -p "$AGENT_HOME/scripts" "$AGENTS" "$BACKUP_DIR"
chmod 700 "$AGENT_HOME"
for file in "${AGENT_FILES[@]}"; do
  cp "$REPO_ROOT/scripts/$file" "$AGENT_HOME/scripts/$file"
  note "scripts/$file"
done
# The password lives here too, because the agents connect to the database. Same 600 as the original.
cp "$REPO_ROOT/.env.prod" "$AGENT_HOME/.env.prod"
chmod 600 "$AGENT_HOME/.env.prod"
note ".env.prod (0600)"

step "Installing agents"
for label in "${LABELS[@]}"; do
  sed -e "s|__AGENT_HOME__|$AGENT_HOME|g" -e "s|__LOGDIR__|$BACKUP_DIR|g" \
    "$REPO_ROOT/scripts/launchd/$label.plist" > "$AGENTS/$label.plist"
  # bootout first: bootstrap on an already-loaded label fails rather than replacing it.
  launchctl bootout "gui/$UID/$label" 2>/dev/null || true
  launchctl bootstrap "gui/$UID" "$AGENTS/$label.plist"
  note "$label"
done

step "Installed. Verify with:"
note "launchctl list | grep jobassistant"
note "launchctl kickstart -p gui/$UID/com.jobassistant.backup   # run one now"
note "logs: $BACKUP_DIR/launchd-*.log"
