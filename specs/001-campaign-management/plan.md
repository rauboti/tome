# Implementation Plan: Campaign & Character Management

**Branch**: `001-campaign-management` | **Date**: 2026-07-20 (amended 2026-07-22) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-campaign-management/spec.md`

> **Amendment 2026-07-22 (post-US1).** Persistence switched from **PostgreSQL/JSONB/Flyway/JdbcTemplate
> to MongoDB** (Spring Data MongoDB + `MongoTemplate`, Spring Data-native index/ledger migrations,
> Testcontainers `MongoDBContainer` replica-set), and derived sheet values are now **computed on read, never stored**
> (server-authoritative; the web echoes for instant feedback). See research.md D3/D5/D8 and
> data-model.md. This landed while only US1 was built — US1 is re-platformed and US2–US5 are authored
> against MongoDB from the start. Everything below reflects the amended stack.

## Summary

Tome v1 lets Hive-authenticated tabletop players keep digital character sheets and lets a dungeon
master (DM) run a live campaign from them. The first edition ships **D&D 3.5 only** through a
**Hybrid rule-set engine**: each rule set's sheet structure is data (a *sheet definition* the engine
reads and the web renders generically), while its derived-value and soft-validation logic is code
behind a `RuleSet` strategy — so the cross-cutting capabilities (storage, permissions, dice,
combat, real-time sync) are written once over any rule set's sheet. Players self-create characters;
the DM adds matching-rule-set characters to a campaign, controls NPCs and private/shared content,
optionally runs their own PC, records sessions, and runs live combat with an initiative tracker and
a server-authoritative dice roller. During a session, updates a participant is authorized to see are
pushed live. Technical approach: a two-tier shape — a Kotlin/Spring Boot BFF API
owning all campaign data in **MongoDB** (sheets as native BSON documents holding base inputs only;
derived values computed server-side by the `RuleSet` on read), brokering Hive OAuth, and fanning out
live updates over **Server-Sent Events**; and a React SPA (`@rauboti/ui`) with a definition-driven
sheet renderer, combat/dice UI, and an SSE subscription. Full decisions in [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin (Spring Boot BOM-managed) on JDK 25 (api); TypeScript ~6.0 (web)

**Primary Dependencies**: Spring Boot 4.1 (web, actuator, security, oauth2-client,
oauth2-resource-server, **data-mongodb** starters), `jackson-module-kotlin` (Jackson 3), Maven `mvnw`, Spotless/ktlint;
Vite 8 + React 19 + Chakra UI v3 + `@rauboti/ui` ^0.3.5 + React Router 7 + Zod + react-i18next
(bilingual nb/en). No JPA — persistence via **`MongoTemplate`** (low-level template, mirroring the
platform's `JdbcTemplate` convention). *(Dropped from the Postgres baseline: `jdbc`/`flyway` starters,
`flyway-database-postgresql`, `postgresql` driver.)*

**Storage**: **MongoDB** (`tome-db`), run as a **single-node replica set** (required for multi-document
transactions; research D5). Migrations/indexes are **Spring Data-native** — indexes ensured on boot from
a code-owned catalog + a small applied-changes ledger (`_migrations`), **no migration framework** (Mongock
deprecated, Flamingock Gradle-only — research §Migrations). Sheets stored as native BSON sub-documents holding **base inputs only**; cross-cutting
values (name, rule set, owner) are top-level document fields, indexed as needed. Owned children are
embedded (memberships/NPCs/content in the campaign; combatants in the encounter; **rolls** in whichever
of campaign/session/encounter they belong to — no `rolls` collection). **`characters`, `campaigns`,
`sessions`, and `encounters` are their own collections** (sessions/encounters referenced by id and
assembled into the full campaign view at read time), so live-combat writes hit only the small encounter
document (data-model.md).

**Testing**: api — JUnit 5, MockMvc + `spring-security-test`, MockK, Testcontainers (real MongoDB via
`MongoDBContainer` + `@ServiceConnection`; the container auto-starts a single-node replica set so
transactions and `@Version` work under test); the `RuleSet` (3.5 derived values + soft validation) and
the dice evaluator are pure Kotlin with dense unit tests; contract tests against
`contracts/openapi.yaml`; an integration test asserts SSE fan-out honors per-participant
authorization. web — Vitest + React Testing Library + MSW; the sheet renderer, permission helpers, and
dice formatting are pure and unit-tested; an EventSource stub drives real-time component tests.

**Target Platform**: Docker Compose stack (platform monorepo); desktop/tablet browser is the primary
client (DM screen + players at the table).

**Project Type**: Web application — `api/` + `web/` (platform convention)

**Performance Goals**: Sheet loads/saves feel instant (<300 ms p95 server time); a revealed live
update reaches authorized viewers within a few seconds (SC-007); a DM can run a full combat round for
≤6 PCs plus NPCs without leaving Tome (SC-008); creating a character + filling a full 3.5 sheet is a
single-sitting task (SC-001).

**Constraints**: Hive access/refresh tokens never reach the browser (BFF, session cookie only);
authorization is enforced server-side on every read/write **and** on every SSE event — a player
never receives another player's private sheet or DM-private content (FR-011/FR-014/SC-004); Tome
guides but never blocks the DM — rule violations are warnings, the DM can override (FR-005);
concurrent sheet edits must not silently overwrite (SC-006 → optimistic concurrency, research D5);
adding a second rule set must touch only its definition + logic, not the shared engine (FR-023/SC-009).

**Scale/Scope**: A handful of platform users; campaigns of ~1 DM + up to ~6 players; one bundled rule
set (3.5) in v1 with the engine built for more; SPA areas — Characters, Campaign (DM view / player
view), Combat, plus the auth shell; 2 locales (nb, en).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | Spec-Driven Development | ✅ PASS | spec.md exists, clarified across two sessions (2026-07-19/20); this plan derives from it; tasks will trace to its five prioritized user stories |
| II | Test-First Verification | ✅ PASS | Test stack defined per tier (Technical Context); tasks.md will order tests before implementation; the `RuleSet` and dice evaluator are pure and heavily unit-tested; contract tests against openapi.yaml; an SSE-authorization integration test guards the privacy-critical fan-out |
| III | Human as Conductor | ⚠ PASS (with flag) | Implementation will run on per-increment feature branches (created before code); agents leave work uncommitted; maintainer commits/merges. **One cross-repo change requires explicit maintainer approval**: registering `tome` as an OAuth client in Hive and defining the Tome `Admin`/`User` roles (research D1/D6) |
| IV | Platform Citizenship | ✅ PASS | `tome/docker-compose.yml` is the source of truth; services `tome-db`/`tome-api`/`tome-web`; host ports 3040/5040/5436 (constitution defaults); app-specific env in tome's own env files; stack-facing `tome.env` at platform root |
| V | Platform Building Blocks First | ✅ PASS | UI composed from `@rauboti/ui` (AppShell/Navbar, Card, Dialog, Table, Input, Select, Badge, EmptyState, …); auth fully delegated to Hive (BFF, JWKS validation, audience-scoped `roles` claim); gaps (definition-driven sheet renderer, combat tracker, dice UI, SSE hook) justified in Complexity Tracking with an upstreaming note |
| VI | Simplicity First | ✅ PASS | Server-authoritative logic kept minimal: SSE (not a WebSocket/STOMP broker), MongoDB documents + one `RuleSet` strategy (not a bespoke schema/collection per rule set), derived values computed on read (no stored derived state), a tiny in-house dice evaluator (no dependency); every deviation from the sibling baseline — including the Postgres→MongoDB divergence — is justified in Complexity Tracking |

**Post-design re-check (after Phase 1; re-affirmed 2026-07-22)**: ✅ PASS — the design keeps the same
three services (`tome-db` is now MongoDB rather than Postgres — no new service count). New surface area
(SSE fan-out, MongoDB documents + `RuleSet` engine, compute-on-read derived values, dice/combat) is
required by clarified requirements (FR-001, FR-005, FR-019–FR-021), each documented in Complexity
Tracking rather than left implicit. Data model is four collections (`characters`, `campaigns`,
`sessions`, `encounters`) — sessions/encounters referenced by id and assembled at read time; members,
NPCs, content, combatants, and rolls are embedded in their owning document;
contracts are REST plus one SSE stream endpoint. Authorization is centralized so it applies identically
to REST reads and SSE events. The Postgres→MongoDB divergence from the sibling baseline is an owned,
justified deviation (Complexity Tracking). The one cross-repo Hive change remains flagged for
maintainer approval (Principle III).

## Project Structure

### Documentation (this feature)

```text
specs/001-campaign-management/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output (REST + SSE stream)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
api/
├── src/main/kotlin/no/rauboti/tome/
│   ├── auth/            # BFF: Hive OAuth client, session cookie, /api/auth/me (roles: Admin/User), logout
│   ├── common/          # RFC-7807 errors, optimistic-concurrency helpers, shared config
│   ├── config/          # Security filter chain, CORS, SSE/async config
│   ├── rulesets/        # RuleSet interface + DnD35RuleSet (derived values + soft validation);
│   │                    #   SheetDefinition provider; controller (GET /api/rule-sets, /{id})
│   ├── characters/      # Character document (BSON sheet, base inputs only), MongoTemplate repo, service (validate + resolve-on-read), controller
│   ├── campaigns/       # Campaign aggregate (root doc embedding members/npcs/content/sessions), MongoTemplate repo, permission service, controller
│   ├── npcs/            # DM-controlled NPC logic (BSON sheet via the shared engine); persisted embedded in the campaign aggregate
│   ├── content/         # Notes / shared-vs-private campaign content (embedded in the campaign aggregate)
│   ├── sessions/        # Session records (prep before, continue after) (embedded in the campaign aggregate)
│   ├── combat/          # Encounter + Combatant: initiative order, current turn, rounds
│   ├── dice/            # Dice-expression evaluator, Roll recording + apply-to-sheet, controller
│   └── realtime/        # SSE emitter registry, authorized event publisher, controller (GET /api/campaigns/{id}/stream)
├── src/main/resources/
│   ├── application.yml / application-dev.yml / application-test.yml  # MongoDB URI, Hive URLs, CORS
│   └── rulesets/dnd35/definition.json     # 3.5 sheet definition (data; authored from the SRD)
│   # Migrations are Spring Data-native: index catalog + ordered changes (C001…) applied on boot via a
│   # ledger collection, in Kotlin under src/main/kotlin/no/rauboti/tome/config/migration/ — no framework
├── src/test/kotlin/no/rauboti/tome/       # contract + integration + unit tests
├── Dockerfile
└── mvnw / mvnw.cmd / pom.xml

web/
├── src/
│   ├── api/             # typed clients (Zod) per resource; SSE subscription helper
│   ├── auth/            # session context, login redirect handling
│   ├── components/
│   │   ├── layout/      # app shell / navbar (@rauboti/ui)
│   │   ├── sheet/       # definition-driven SheetRenderer + field widgets (escape hatch for custom widgets)
│   │   ├── characters/  # character list, create, sheet editing
│   │   ├── campaigns/   # campaign create, roster (DM add/remove), DM view vs limited player view, share/private toggles
│   │   ├── combat/      # InitiativeTracker, turn/round control (DM), revealed-state view (players)
│   │   └── dice/        # DiceRoller UI, roll log, apply-to-sheet prompt
│   ├── i18n/            # i18next setup + nb.json / en.json
│   ├── lib/             # pure helpers: permission/visibility, dice formatting, derived-value display
│   ├── pages/           # CharactersPage, CampaignPage (role-aware), CombatPage, auth shell
│   ├── realtime/        # useCampaignStream (EventSource) hook
│   ├── mocks/           # MSW handlers (incl. an SSE stub)
│   └── test/            # test setup
├── Dockerfile / nginx.conf / vite.config.ts / package.json

docker-compose.yml       # tome-db + tome-api + tome-web (healthcheck-gated)
.env.example
```

**Structure Decision**: Two-tier web application (`api/` + `web/`) mirroring avec/pulse/taskmaster —
same package root convention (`no.rauboti.tome`), same low-level-template persistence discipline (but
**`MongoTemplate` on MongoDB** rather than the siblings' `JdbcTemplate` on Postgres — the owned
divergence, Complexity Tracking), same frontend layout and `lib/`-of-pure-functions discipline.
Tome-specific modules vs. the siblings: api
`rulesets/` (the Hybrid engine), `combat/`, `dice/`, and `realtime/` (SSE); web `sheet/` (the
definition-driven renderer), `combat/`, `dice/`, and `realtime/`. The SSE emitter registry and the
sheet renderer are written lift-ready in case they become shared `@rauboti/*` building blocks.

## Complexity Tracking

> Constitution Check passes; entries below justify the additions that go beyond what the sibling apps
> (avec/pulse/taskmaster) already use. Each traces to a clarified requirement.

| Deviation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **Server-Sent Events real-time layer** (`realtime/`, SSE emitter registry, per-event authorization) | FR-019/FR-021/SC-007: during a session, authorized participants see revealed updates live without refreshing | Poll-and-refresh — rejected: laggy and chatty for a live table. WebSocket/STOMP + broker — rejected as heavier than needed: updates are server→client fan-out; client actions already use REST; a handful of users per campaign fit an in-process emitter registry (research D2) |
| **MongoDB persistence** instead of the Postgres/`JdbcTemplate`/Flyway platform baseline (avec/pulse/taskmaster) | The domain is document-shaped (sheets, notes, campaign aggregates); BSON stores it natively and lets the campaign aggregate embed its owned children in one document; the maintainer also wants non-Postgres competence kept alive on the platform (owned, accepted divergence) | Stay on Postgres `JSONB` (the prior v1 baseline) — technically sound but superseded 2026-07-22: JSONB is a document store bolted onto a relational engine, and the relational strengths (FK/CHECK) are not what this domain leans on. Trade accepted: cross-document invariants become index + app rules (data-model.md Invariants); the platform now runs two DB technologies (research D3/D5) |
| **MongoDB documents + `RuleSet` strategy engine** (Hybrid) instead of typed per-rule-set collections | FR-001/FR-023/SC-009: one shared engine so dice/combat/sync/permissions are written once and a 2nd/3rd rule set is just definition + logic | Bespoke typed collections per rule set — rejected (spec clarification 2026-07-20): would duplicate every cross-cutting feature per rule set or force a shared abstraction anyway. Fully data-driven rules (no code) — rejected: a generic engine expressive enough for 3.5's mechanics is a large upfront build (research D3) |
| **Derived values computed on read, never stored** (server-authoritative; web echoes for feedback) | FR-005 / Clarification 2026-07-22: single source of truth = base inputs; a stored derived value cannot drift from its inputs because none is stored; less stored state = fewer error points | Persist derived on write (prior v1 behavior) — rejected: redundant state that can drift, doubled by the client also computing. Client-only derivation — rejected: server dice/combat would lose derived values and 3.5's rules would be duplicated in TypeScript (research D8) |
| **In-house dice-expression evaluator** (`dice/`), server-authoritative | FR-020: in-app rolls whose outcomes are recorded and applied to sheets | A client-side roller — rejected: not auditable and can diverge across viewers; a third-party dice library — rejected: the grammar (`NdM±K`, keep/drop) is tiny and dependency-free (research D4) |
| **Optimistic concurrency** (Spring Data `@Version`, 409 on stale write) | SC-006: concurrent edits (DM + player on a shared sheet) must not silently overwrite | Last-write-wins — rejected: silent data loss; field-level CRDT merge — rejected as overkill for a handful of editors (research D5) |
| **MongoDB single-node replica set** (compose + Testcontainers) | Multi-document transactions require it (research D5); `MongoDBContainer` auto-provides it under test | Standalone `mongod` — rejected: no transactions at all, so any future atomic multi-doc write would be impossible; a full multi-node RS — rejected: unjustified ops overhead at this scale |
| **i18n infrastructure** (react-i18next + nb/en bundles) | Constitution Platform Integration (bilingual nb/en); consistent with avec | Single-language UI — rejected by platform convention. Game terminology may remain canonical per the spec assumption, so only chrome/labels are translated (research D7) |
| **Cross-repo: Hive client registration + Tome `Admin`/`User` roles** | FR-024: access is gated by a Hive-assigned Tome role; the BFF login needs a registered `tome` client | Not a code-complexity item but a **Principle III maintainer-approval flag** — Tome cannot self-provision Hive; this owned change is bundled and surfaced for explicit approval (research D1/D6) |

## Deferred Decisions (revisit)

> Choices intentionally made "good enough for now", flagged for a later pros/cons pass rather than
> resolved up front. Not blocking; revisit once US1 is exercised end to end.

- **Stored vs. computed derived sheet values — ✅ RESOLVED 2026-07-22 (research D8).** Decision:
  derived values are **computed on read, never stored**. A stored sheet holds base inputs only;
  `RuleSet.computeDerived` runs on the read path via a per-entity resolver (v1 `CharacterDataResolver`),
  so every consumer (REST response, combat, dice, SSE, player view) still depends only on
  `SheetData` + `RuleSet` (FR-023/SC-009). The web also derives on change for instant feedback but
  reconciles to the authoritative server response. Rationale: single source of truth = base inputs;
  less stored state = fewer error points. No longer deferred.
- **Which sheet values to surface as top-level fields / indexes.** Under MongoDB the "promote to a
  column" question becomes "lift out of `data` to a top-level document field and/or index it." v1
  keeps `name`/`ruleSetId`/`userId` top-level and indexes `characters.userId`; HP and initiative stay
  inside `data` (they're derived or per-rule-set inputs). Revisit once Dark Souls (US5) exists: with
  two rule sets in hand, decide which cross-cutting values genuinely earn a top-level field/index for
  combat/roster queries (tracked as a task after US5). Note: derived values are never stored, so a
  value that is *derived* is surfaced by the resolve-on-read helper, not by lifting it into storage.
- **Iterative attacks from BAB.** `baseAttackBonus` is a single player-entered `int` for v1. At higher
  levels 3.5 grants extra attacks (`+6/+1`, `+11/+6/+1`, … −5 each, up to 4). A later enhancement can
  *derive and display* the full iterative-attack sequence from the single BAB value (a read-time
  presentation concern, so it dovetails with the stored-vs-computed decision above).
- **Embedded-array spill vs. the 16 MB document limit.** `sessions` and `encounters` are **already
  their own collections** (referenced by id), which keeps the campaign document small and moves
  live-combat writes onto a small per-encounter document — the write-amplification concern is designed
  out. The residual in-campaign growth candidates are `content[]` and campaign-level `rolls[]` (and
  `npcs[]`); the revisit is whether/when to **spill those to their own collections** (referenced by
  `campaignId`) behind the same service API, triggered by a soft ~2 MB guideline well under MongoDB's
  hard 16 MB/document cap (data-model.md "Document-size bound"). No REST-contract impact.
  Decide with real long-running-campaign data in hand; until then the service guards the hard limit.
