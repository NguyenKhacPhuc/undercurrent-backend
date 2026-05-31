# undercurrent-backend

Backend service for [Undercurrent](https://github.com/NguyenKhacPhuc/undercurrent) — Ktor on JVM, Postgres on Railway. Owns user accounts (email + password) and session issuance.

Part of the [undercurrent-workspace](https://github.com/NguyenKhacPhuc/undercurrent-workspace) submodule trio (alongside `weft/` and `undercurrent/`).

## Status

🚧 Bootstrapping. First Inception (`backend-bootstrap-auth`) is in flight; tracking issues live in the workspace at `inception/260531-1733-backend-bootstrap-auth/`.

## Build & run

See [`CLAUDE.md`](CLAUDE.md) for the working notes (commands, env vars, ports).

## Deploy

Auto-deployed to Railway on every push to `main`. No CI workflow in v1 — run tests locally before pushing.
