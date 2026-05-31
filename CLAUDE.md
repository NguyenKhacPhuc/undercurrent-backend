# undercurrent-backend — Claude working notes

Auto-loaded as context by Claude Code when working in this subrepo. Keep terse.

## What this repo is

Undercurrent's backend. A Ktor app on JVM that owns user accounts (email + password) and session issuance. Deployed on Railway, backed by Postgres. Single-service for v1 — no microservices, no message queues.

Lives as a workspace submodule:

```
undercurrent-workspace/
├── weft/             ← substrate SDK
├── undercurrent/     ← host app (Android + iOS KMP)
└── backend/          ← THIS REPO
```

## Status

> [!warning] **Bootstrap in progress.**
> This file is a skeleton; sections below will be filled in as stories
> land. Tracking Inception lives at
> `../inception/260531-1733-backend-bootstrap-auth/`.

## Build & run (TBD — filled by Story 01)

```bash
# Run tests
./gradlew test

# Run the app locally on the default port
./gradlew run
```

Default port: `8080` (overridable via `PORT` env var — Railway sets this automatically).

## Env vars

| Name | Required | Notes |
|---|---|---|
| `PORT` | no (defaults to 8080) | Set by Railway in production. |
| `DATABASE_URL` | yes (after Story 02) | Postgres connection URL. Comes from Railway's Postgres add-on. |

Other secrets: see [the BE Inception's D7](../inception/260531-1733-backend-bootstrap-auth/decisions.md) — Railway env vars only for v1; no vault.

## Tech stack

- Language: Kotlin / JVM
- Framework: [Ktor](https://ktor.io) (server-side)
- Build: Gradle (Kotlin DSL)
- DB: Postgres (Railway-managed)
- Test: kotest (`BehaviorSpec`, MockK on JVM-only tests)
- Deploy: Railway, auto-deploy from `main`

## Conventions (TBD as code lands)

This section grows as the first Construction stories land. For now, see the
Inception decisions at
`../inception/260531-1733-backend-bootstrap-auth/decisions.md`:

- D2 — email + password is the v1 auth model
- D3 — opaque server-stored 30-day sessions
- D5 — Ktor / Railway / Postgres
- D6 — sign-in rate-limit thresholds (10 / email / 15min)
- D7 — secrets management = Railway env vars only
- D8 — observability = Railway logs + UptimeRobot on `/health`
- D9 — no CI workflow in v1

## What NOT to do

- Don't commit `.env` files or any secret values. Use Railway env vars.
- Don't add a GitHub Actions workflow (per D9). Run tests locally before push.
- Don't make `/health` touch the DB. It must answer even if Postgres is down.
- Don't open PRs from the workspace root — `cd backend/` first.
