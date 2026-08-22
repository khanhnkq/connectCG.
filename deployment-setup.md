# Backend pull-based deployment

## Goal
Deploy the backend from GHCR without building source on the VPS, with either local Docker PostgreSQL or a managed PostgreSQL provider.

## Tasks
- [x] Publish verified backend images as `latest`, `sha-*`, and release tags.
- [x] Add pull-only production Compose files for shared services and optional local PostgreSQL.
- [x] Add an interactive environment generator that never prints secrets.
- [x] Add backup, deploy verification, logs, status, and image-tag rollback actions.
- [x] Validate shell syntax, rendered Compose for both DB modes, and backend tests.

## Done When
- [x] `./deploy.sh` can configure and deploy either database mode on a clean VPS.
- [x] `./gradlew check` and both Compose configuration checks pass.
