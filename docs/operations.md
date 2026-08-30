# Operations

How this application is run for real, and how its data survives.

Until now there was one database and one way to start the app, so "development" and "the thing I
actually use" were the same rows. This document describes the split that separates them, and the
backup story that makes the production half safe to rely on.

## The two environments

|  | dev | prod |
|---|---|---|
| Started by | `./mvnw spring-boot:run` | `scripts/run-prod.sh` |
| Spring profile | `dev` (the default) | `prod` (explicit only) |
| Application | `127.0.0.1:8080` | `127.0.0.1:8090` |
| Database | `job-assistant-dev-db`, `127.0.0.1:5432`, db `jobassistant_dev` | `job-assistant-prod-db`, `127.0.0.1:5433`, db `jobassistant` |
| Compose file | `docker-compose.yml` | `docker-compose.prod.yml` |
| Volume | `job-assistant-dev-pgdata` | `job-assistant-prod-pgdata` (`external`) |
| Market poll | off | on, daily |
| Logging | `DEBUG` | `INFO` |
| Data | disposable; refilled by `scripts/seed-dev.sh` | the real profile and application history |

Four things do the safety work, and all four are load-bearing:

1. **`spring.profiles.default: dev`** in `application.yaml`. A bare run cannot reach production;
   reaching it takes an explicit `--spring.profiles.active=prod`.
2. **Prod has no fallback for `DB_PASSWORD`.** Every other property could be wrong and still start.
   This one stops a prod launch that never sourced `.env.prod`, instead of letting it inherit dev's
   credentials and write to the wrong database. `ProductionEnvironmentCheck` turns that into a
   named failure before any pool is opened: Spring Boot's `Binder` *ignores* a placeholder it
   cannot resolve, so without it the symptom is a thirty-second Hikari timeout and a buried
   "password authentication failed", which looks nothing like its cause.
3. **The prod volume is `external: true`.** Compose neither creates nor destroys it, so
   `docker compose -f docker-compose.prod.yml down -v` reports it as external and leaves it alone.
4. **The two compose files carry explicit, different project names** (`job-assistant-dev`,
   `job-assistant-prod`). Compose derives the project from the directory otherwise, and since both
   files describe a service called `postgres`, they would be the *same* service: bringing prod up
   recreates the dev container instead of starting a second database. Do not remove the `name:`
   line from either file.

Dev is on 5432 with a `_dev` database name and prod is on 5433 without one, so a connection string
that is half-corrected connects to nothing rather than to the wrong thing.

## First-time setup

```bash
cp .env.example .env.prod        # then fill in DB_PASSWORD, OPENROUTER_API_KEY, BACKUP_MIRROR
                                 # quote every value: the file is sourced with `set -a`, and the
                                 # iCloud path contains a space
docker volume create job-assistant-prod-pgdata
set -a; . ./.env.prod; set +a
docker compose -f docker-compose.prod.yml up -d
scripts/release-prod.sh          # builds the jar with the SPA and installs it outside target/
scripts/run-prod.sh
scripts/install-launchd.sh       # nightly dump + monthly restore drill
```

Dev needs nothing but `docker compose up -d` and `./mvnw spring-boot:run`.

## Daily use

```bash
# prod
docker compose -f docker-compose.prod.yml up -d      # after a reboot
scripts/run-prod.sh                                  # foreground, http://127.0.0.1:8090

# dev
docker compose up -d
./mvnw spring-boot:run                               # http://127.0.0.1:8080
cd frontend && npm run dev                           # http://127.0.0.1:5173, proxies to 8080
scripts/seed-dev.sh                                  # (re)load the synthetic profile
```

Shipping a change to prod is `scripts/release-prod.sh` followed by `scripts/run-prod.sh`. The jar
lives at `~/Applications/job-assistant/job-assistant.jar` rather than in `target/`, so a routine
`./mvnw clean` cannot delete the artifact production is running from.

## Backups

`scripts/db-backup.sh` writes `jobassistant-<utc-stamp>-<label>.dump` (pg_dump custom format) to
`$BACKUP_DIR`, copies it to `$BACKUP_MIRROR`, and prunes anything older than
`$BACKUP_RETENTION_DAYS` — except that the newest `$BACKUP_KEEP_MINIMUM` are always kept. A machine
left switched off for two months should come back to stale backups, not to none.

It runs in three situations: nightly from launchd, before every `scripts/run-prod.sh` (start-up is
when Flyway can change the schema), and before any restore into prod.

**The launchd agents run a copy, not this checkout.** A launchd job holds no TCC permission for
`/Volumes`, so a plist pointing at a repository on a secondary disk fails with "Operation not
permitted" into a log nobody reads. `scripts/install-launchd.sh` therefore copies `_common.sh`,
`db-backup.sh`, `db-verify-restore.sh` and `.env.prod` into
`~/Library/Application Support/job-assistant` and points the agents there. **Re-run
`scripts/install-launchd.sh` after editing any of those four files** - it prints what it copied, so
the sync is visible rather than assumed. The alternative, granting Full Disk Access to `/bin/bash`,
is a far broader permission than this job needs.

**`$BACKUP_DIR` must be outside this repository.** A dump contains the profile's name, email and
phone, and this repository is public.

Each dump gets a `.counts` sidecar recording the row counts of the tables that cannot be rebuilt
from migrations or a re-poll. That sidecar is what makes verification mean something:

```bash
scripts/db-verify-restore.sh          # newest dump, or pass a path
launchctl kickstart -p gui/$UID/com.jobassistant.backup          # run the nightly job now
launchctl kickstart -p gui/$UID/com.jobassistant.verify-restore  # run the drill now
```

It restores into a throwaway `jobassistant_restorecheck` database, diffs the restored counts
against the sidecar, checks that `profile_details` is non-empty — a dump of an empty database also
restores cleanly and matches its own sidecar, which would be a green check over nothing — and drops
the scratch database. Non-zero exit on any mismatch.

A backup that has never been restored is a file, not a backup. The monthly launchd job exists so
that "can we restore" is answered before it matters rather than after.

### Restoring

```bash
scripts/db-restore.sh <dump>                 # into dev, no confirmation needed
scripts/db-restore.sh <dump> --into prod     # takes a safety dump first, then asks you to type
                                             # the database name
```

## What not to run

- `docker compose -f docker-compose.prod.yml down -v` — the `external` volume declaration is what
  makes this survivable. Do not "fix" it by removing that line.
- `docker volume rm job-assistant-prod-pgdata` — this is the one command with nothing between it
  and total loss.
- `./mvnw clean` while prod is running, *if* you have moved the jar back into `target/`. Don't.
- Flyway `clean`, under any profile. It is disabled in `application.yaml`; leave it disabled.
- `SPRING_PROFILES_ACTIVE=prod` against a jar you have not just built — the prod profile runs
  migrations on boot, so an old jar can be a schema downgrade attempt.
- `docker compose down` from this directory expecting it to stop everything. It stops dev only;
  prod needs `-f docker-compose.prod.yml`. That is the intent, not an inconvenience.

## Recovering from a bad migration

Migrations run at start-up and `scripts/run-prod.sh` dumps first, so the dump labelled
`pre-launch` from the failed start is the state immediately before the migration:

```bash
docker compose -f docker-compose.prod.yml up -d
scripts/db-restore.sh "$BACKUP_DIR"/jobassistant-<stamp>-pre-launch.dump --into prod
```

Then fix the migration on a branch and verify it against dev before releasing again.

## Where this could go next

Prod runs on this machine because the application has no authentication and binds to loopback by
design (`CLAUDE.md`, Decision 12), and because the profile holds direct identifiers that
`docs/adr/0002-no-personal-data-to-model-providers.md` goes to some length to keep off third-party
machines. Hosting it is an authentication project, not a configuration change.

Moving the production database to Neon, if that ever becomes worth the trade, is a `DB_URL` in
`.env.prod` and nothing else — which is why `application-prod.yaml` reads it from the environment
instead of hard-coding a host. The backup scripts would need `pg_dump` reaching a remote host
rather than `docker exec`; nothing else in this document changes.
