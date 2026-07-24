# Tome

Tome helps a dungeon master run a tabletop RPG campaign: digital character sheets, campaigns with a
DM view and a limited player view, NPCs, live sessions, and combat with dice + initiative.

**v1 ships D&D 3.5 only** via a typed, code-first rule-set engine ([ADR-001](specs/001-campaign-management/decisions/ADR-001-typed-ruleset-sheets.md)).
Dark Souls is a later story; 5E is deferred.

Tome is one app in the wider platform monorepo and delegates identity to **Hive** (the platform's
identity service). It runs as its own three-service Docker Compose stack.

## Architecture at a glance

Two tiers, one stack:

| Tier | Path | Stack |
|------|------|-------|
| **BFF API** | [`api/`](api/README.md) | Kotlin + Spring Boot 4.1, `MongoTemplate` (no JPA), package root `no.rauboti.tome` |
| **Web SPA** | [`web/`](web/README.md) | Vite + React 19 + Chakra UI v3 + [`@rauboti/ui`](https://github.com/rauboti), TypeScript |
| **Database** | (container) | MongoDB, single-node replica set |

The **api** is a Backend-for-Frontend: it owns all campaign data in MongoDB, brokers the Hive OAuth
login, holds the Hive token server-side (the browser only ever gets a session cookie), and will fan
out live updates over Server-Sent Events. The **web** SPA talks only to the api under `/api` and never
sees a Hive token. Each tier's README covers its own internals; start there.

## Running the stack

Everything is defined in [`docker-compose.yml`](docker-compose.yml). Copy the env template first
(defaults boot the stack as-is):

```bash
cp .env.example .env
```

**Full stack in Docker** — web on `3040`, api on `5040`, db on `5436`:

```bash
docker compose up
```

**Local web dev against a containerised backend** — Vite dev server on `5173`, proxying `/api` to the
containerised api:

```bash
docker compose up tome-db tome-api
cd web && yarn dev
```

Tome uses the **3040 / 5040 / 5436** port band, reserved for it in the platform stack. See
[`.env.example`](.env.example) for every knob (Hive URLs, CORS origins, the `@rauboti/ui` package token).

## Where things live

- **Feature specs & design** — [`specs/001-campaign-management/`](specs/001-campaign-management/):
  `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, and
  the `decisions/` ADRs. The OpenAPI contract is the source of truth for the wire shape.
- **Project principles** — [`.specify/memory/constitution.md`](.specify/memory/constitution.md).
- **Backend** — [`api/README.md`](api/README.md).
- **Frontend** — [`web/README.md`](web/README.md).

> **Status.** Active feature: **Campaign & Character Management**. Implemented so far: auth (Hive BFF),
> characters with the typed D&D 3.5 sheet, the rule-set engine, and the spell catalog. Campaigns, NPCs,
> sessions, combat, dice, and the SSE real-time layer are planned — see `plan.md` for the full target.

## Contributing

Every implementation task gets a **fresh feature branch off `main`, created before any code**; agents
leave work uncommitted for review. No direct commits to `main`. The full rule (and the hook that
enforces it) is in [`CLAUDE.md`](CLAUDE.md).
