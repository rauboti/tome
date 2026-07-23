# Quickstart & Validation: Campaign & Character Management

**Feature**: 001-campaign-management | **Date**: 2026-07-20 | **Plan**: [plan.md](plan.md)

A run guide plus end-to-end validation scenarios that prove the feature works. Implementation details
(entity/service bodies, migrations, full test suites) belong in `tasks.md` and the code, not here.

## Prerequisites

- Docker + Docker Compose (the platform-standard way to run an app).
- `RAUBOTI_PACKAGE_TOKEN` exported (web build pulls `@rauboti/ui` from the private registry — passed
  as a BuildKit secret, as in avec/pulse).
- **Hive running and reachable**, with a registered `tome` OAuth client and the Tome `Admin`/`User`
  roles defined (research D1/D6 — the cross-repo change requiring maintainer approval). For local dev,
  point `HIVE_EXTERNAL_URL` / `HIVE_INTERNAL_URL` / `HIVE_CLIENT_ID` / `HIVE_CLIENT_SECRET` at your
  Hive instance in `tome.env` / `.env`.

## Ports (constitution defaults)

| Service | Host port | Container |
|---------|-----------|-----------|
| `tome-web` (nginx) | 3040 | 80 |
| `tome-api` (Spring Boot) | 5040 | 8080 |
| `tome-db` (MongoDB, replica set `rs0`) | 5436 | 27017 |

## Run

```bash
# from tome/
docker compose up --build
# web: http://localhost:3040  → redirects through Hive login on first access
```

Standalone within the combined platform stack, the root `docker-compose.yml` `include`s
`tome/docker-compose.yml` and supplies `tome.env` (Platform Citizenship, Principle IV).

## Dev loop (without full containers)

```bash
# api
cd api && ./mvnw spring-boot:run          # needs tome-db up; uses application-dev.yml
./mvnw verify                              # runs unit + integration (Testcontainers MongoDB replica set) + Spotless gate

# web
cd web && yarn && yarn dev                 # Vite dev server (proxies /api to :5040)
yarn test                                  # Vitest
```

## Validation scenarios

Each scenario maps to a user story and its acceptance criteria in [spec.md](spec.md). "Verify" steps
are what the automated tests assert; they can also be walked by hand in the browser.

### US1 — Keep a character sheet digitally (P1)

1. Sign in via Hive (must hold a Tome role).
2. Create a character; pick rule set **D&D 3.5**. The sheet renders from
   `GET /api/rule-sets/dnd35` (definition-driven).
3. Fill ability scores; **verify** derived values (e.g. ability modifiers, saves) compute
   server-side and return on save (FR-005, `computeDerived`).
4. Reduce `hp_current`, add an inventory item, save; reload; **verify** the sheet persists exactly.
5. Make a change that violates a 3.5 rule (e.g. an out-of-range value); **verify** a **warning**
   is returned but the save still succeeds (Tome guides, never blocks — FR-005).
6. Send a stale `version` on a write; **verify** `409 Conflict` (optimistic concurrency, D5).

### US2 — Run a campaign and build its roster (P2)

1. As a DM, create a campaign bound to **dnd35**; **verify** you are its DM.
2. Have a second user create a 3.5 character and share its id.
3. As the DM, add that character to the roster; **verify** it appears (FR-008/FR-009).
4. Attempt to add a character of a *different* rule set (once a second rule set exists — US5) or,
   in v1, **verify** via a service/unit test that a rule-set mismatch is refused with a clear
   problem detail (FR-008; see spec US2 Independent Test note).
5. As a *second player*, open the campaign; **verify** you see your own sheet + shared content but
   **not** another player's private sheet (FR-011, SC-004).
6. As the DM, open the campaign; **verify** the full roster and every character are visible (FR-012).

### US3 — Track sessions and control NPCs (P3)

1. As the DM, create two NPCs (default private) and one `content` note marked **private**, one
   marked **shared**.
2. As a player, open the campaign; **verify** the private NPCs and private note are **not** visible,
   while the shared note **is** (FR-011/FR-013/FR-016).
3. As the DM, add a player character of your own to your campaign; **verify** you control it plus the
   NPCs, and players gain no DM visibility (FR-017, edge cases).
4. Create a session (planned), then mark it completed; **verify** it is available for prep/continue
   (FR-018).

### US4 — Run live combat with dice and initiative (P4)

1. Open the campaign in two browsers: DM and a player. The player subscribes to
   `GET /api/campaigns/{id}/stream` (SSE).
2. As the DM, start an encounter with the PCs + NPCs; **verify** combatants are ordered by
   initiative and the current turn is marked (FR-021).
3. As the DM, roll `2d6+3` and apply it to an NPC's `hp_current`; **verify** the roll is recorded,
   the sheet updates, and a rule warning shows if HP goes out of range without blocking (FR-020/FR-005).
4. As the DM, advance the turn; **verify** the player's view updates **live** (no refresh) within a
   few seconds via SSE (FR-019, SC-007).
5. **Verify** (integration test) that a player subscriber never receives an event for hidden/private
   combat state or another player's private sheet (SC-004) — the SSE authorization guard.

### US5 — Add the custom Dark Souls rule set (P5, later)

Deferred. When picked up: supply a `darksouls` sheet definition + `DarkSoulsRuleSet` logic only, and
**verify** Dark Souls characters/campaigns work end to end with **no change** to the shared engine or
the dice/combat/sync/permissions code (SC-009). Expect a spec amendment for the rule set's specifics.

## Key references

- Endpoints & schemas: [contracts/openapi.yaml](contracts/openapi.yaml)
- Collections/aggregates, authorization model, indexes & invariants, Spring Data-native migrations: [data-model.md](data-model.md)
- Decisions (SSE, MongoDB document engine, compute-on-read derived values, dice, concurrency, roles): [research.md](research.md)
