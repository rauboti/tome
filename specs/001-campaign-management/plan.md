# Implementation Plan: Campaign & Character Management

**Branch**: `001-campaign-management` | **Date**: 2026-07-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-campaign-management/spec.md`

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
pushed live. Technical approach: the platform-standard two-tier shape — a Kotlin/Spring Boot BFF API
owning all campaign data in Postgres (sheets as `JSONB`, validated/derived server-side by the
`RuleSet`), brokering Hive OAuth, and fanning out live updates over **Server-Sent Events**; and a
React SPA (`@rauboti/ui`) with a definition-driven sheet renderer, combat/dice UI, and an SSE
subscription. Full decisions in [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin (Spring Boot BOM-managed) on JDK 25 (api); TypeScript ~6.0 (web)

**Primary Dependencies**: Spring Boot 4.1 (web, actuator, security, oauth2-client,
oauth2-resource-server, jdbc, flyway starters), `jackson-module-kotlin` (Jackson 3), Maven `mvnw`,
Spotless/ktlint; Vite 8 + React 19 + Chakra UI v3 + `@rauboti/ui` ^0.3.5 + React Router 7 + Zod +
react-i18next (bilingual nb/en). No JPA — persistence via `JdbcTemplate` (platform convention).

**Storage**: PostgreSQL 17 (`tome-db`), migrations via Flyway (`flyway-database-postgresql`). Sheet
values stored as `JSONB`; cross-cutting values (name, rule set, owner, current HP) promoted to
columns for querying and combat.

**Testing**: api — JUnit 5, MockMvc + `spring-security-test`, MockK, Testcontainers (real Postgres
via `@ServiceConnection`); the `RuleSet` (3.5 derived values + soft validation) and the dice
evaluator are pure Kotlin with dense unit tests; contract tests against `contracts/openapi.yaml`;
an integration test asserts SSE fan-out honors per-participant authorization. web — Vitest + React
Testing Library + MSW; the sheet renderer, permission helpers, and dice formatting are pure and
unit-tested; an EventSource stub drives real-time component tests.

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
| VI | Simplicity First | ✅ PASS | Server-authoritative logic kept minimal: SSE (not a WebSocket/STOMP broker), `JSONB` + one `RuleSet` strategy (not a bespoke schema per rule set), a tiny in-house dice evaluator (no dependency); every deviation from the sibling baseline is justified in Complexity Tracking |

**Post-design re-check (after Phase 1)**: ✅ PASS — the design adds no infrastructure beyond the
platform-standard three services. New surface area (SSE fan-out, `JSONB` sheets + `RuleSet` engine,
dice/combat) is required by clarified requirements (FR-001, FR-005, FR-019–FR-021), each documented in
Complexity Tracking rather than left implicit. Data model is nine tables; contracts are REST plus one
SSE stream endpoint. Authorization is centralized so it applies identically to REST reads and SSE
events. The one cross-repo Hive change remains flagged for maintainer approval (Principle III).

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
│   ├── characters/      # Character (JSONB sheet + promoted cols), JdbcTemplate repo, service (validate/derive), controller
│   ├── campaigns/       # Campaign, Membership (roster), permission service, controller
│   ├── npcs/            # DM-controlled NPCs (JSONB sheet under the campaign's rule set)
│   ├── content/         # Notes / shared-vs-private campaign content
│   ├── sessions/        # Session records (prep before, continue after)
│   ├── combat/          # Encounter + Combatant: initiative order, current turn, rounds
│   ├── dice/            # Dice-expression evaluator, Roll recording + apply-to-sheet, controller
│   └── realtime/        # SSE emitter registry, authorized event publisher, controller (GET /api/campaigns/{id}/stream)
├── src/main/resources/
│   ├── application.yml / application-dev.yml / application-test.yml
│   ├── rulesets/dnd35/definition.json     # 3.5 sheet definition (data; authored from the SRD)
│   └── db/migration/V1__… V2__… …         # Flyway migrations
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

**Structure Decision**: Two-tier web application (`api/` + `web/`) mirroring avec/pulse/taskmaster
exactly — same package root convention (`no.rauboti.tome`), same `JdbcTemplate` persistence, same
frontend layout and `lib/`-of-pure-functions discipline. Tome-specific modules vs. the siblings: api
`rulesets/` (the Hybrid engine), `combat/`, `dice/`, and `realtime/` (SSE); web `sheet/` (the
definition-driven renderer), `combat/`, `dice/`, and `realtime/`. The SSE emitter registry and the
sheet renderer are written lift-ready in case they become shared `@rauboti/*` building blocks.

## Complexity Tracking

> Constitution Check passes; entries below justify the additions that go beyond what the sibling apps
> (avec/pulse/taskmaster) already use. Each traces to a clarified requirement.

| Deviation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **Server-Sent Events real-time layer** (`realtime/`, SSE emitter registry, per-event authorization) | FR-019/FR-021/SC-007: during a session, authorized participants see revealed updates live without refreshing | Poll-and-refresh — rejected: laggy and chatty for a live table. WebSocket/STOMP + broker — rejected as heavier than needed: updates are server→client fan-out; client actions already use REST; a handful of users per campaign fit an in-process emitter registry (research D2) |
| **`JSONB` sheets + `RuleSet` strategy engine** (Hybrid) instead of typed per-rule-set tables | FR-001/FR-023/SC-009: one shared engine so dice/combat/sync/permissions are written once and a 2nd/3rd rule set is just definition + logic | Bespoke typed tables per rule set — rejected (spec clarification 2026-07-20): would duplicate every cross-cutting feature per rule set or force a shared abstraction anyway. Fully data-driven rules (no code) — rejected: a generic engine expressive enough for 3.5's mechanics is a large upfront build (research D3) |
| **In-house dice-expression evaluator** (`dice/`), server-authoritative | FR-020: in-app rolls whose outcomes are recorded and applied to sheets | A client-side roller — rejected: not auditable and can diverge across viewers; a third-party dice library — rejected: the grammar (`NdM±K`, keep/drop) is tiny and dependency-free (research D4) |
| **Optimistic concurrency** (`version` column, 409 on stale write) | SC-006: concurrent edits (DM + player on a shared sheet) must not silently overwrite | Last-write-wins — rejected: silent data loss; field-level CRDT merge — rejected as overkill for a handful of editors (research D5) |
| **i18n infrastructure** (react-i18next + nb/en bundles) | Constitution Platform Integration (bilingual nb/en); consistent with avec | Single-language UI — rejected by platform convention. Game terminology may remain canonical per the spec assumption, so only chrome/labels are translated (research D7) |
| **Cross-repo: Hive client registration + Tome `Admin`/`User` roles** | FR-024: access is gated by a Hive-assigned Tome role; the BFF login needs a registered `tome` client | Not a code-complexity item but a **Principle III maintainer-approval flag** — Tome cannot self-provision Hive; this owned change is bundled and surfaced for explicit approval (research D1/D6) |
