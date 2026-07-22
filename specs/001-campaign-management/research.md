# Research: Campaign & Character Management

**Feature**: 001-campaign-management | **Date**: 2026-07-20 | **Plan**: [plan.md](plan.md)

Decisions that resolve the plan's unknowns. Platform-standard choices (Kotlin/Spring Boot 4.1 BFF,
Hive OAuth, React 19/`@rauboti/ui`, Vitest/MockK/Testcontainers, Docker Compose) are inherited from
the sibling apps (avec/pulse/taskmaster) and the Tome constitution and are not re-litigated here. Only
the tome-specific decisions are recorded.

> **Amendment 2026-07-22 (post-US1).** Tome's persistence deliberately diverges from the
> Postgres/`JdbcTemplate`/Flyway platform baseline: it uses **MongoDB** (D3), because the domain is
> document-shaped (sheets, notes, campaign aggregates) and the maintainer wants non-Postgres
> competence kept alive on the platform. The divergence is owned and accepted (see plan.md Complexity
> Tracking / Constitution Check). D3 and D5 below are rewritten for MongoDB; D8 records the
> derived-values decision (compute-on-read, never stored), which the original plan had deferred. This
> amendment lands while only US1 exists, so it re-platforms one built slice and redirects US2–US5
> before they are written.

---

## D1 — Authentication & platform roles (Hive BFF)

**Decision**: Reuse the platform BFF pattern verbatim (as avec). The api is an OAuth2 *client*
(`spring-boot-starter-oauth2-client`) that runs Authorization-Code + PKCE against Hive and holds the
tokens server-side behind an HTTP-only session cookie; it is also a *resource server*
(`spring-boot-starter-oauth2-resource-server`) validating Hive-issued RS256 JWTs via JWKS on API
calls. Access requires a Tome role — **Admin** or **User** — read from the audience-scoped `roles`
claim in the JWT (FR-024). The browser never sees Hive tokens.

**Rationale**: Identical to avec/pulse/taskmaster, so it inherits a proven config and the platform's
security posture (Principle V). Roles-in-JWT means Tome does not query Hive per request. The BFF/JWKS
setup (security filter chain, session cookie, client + resource-server config) is **lifted from
taskmaster/pulse** and adapted for tome's client-id/URLs — new code is the Tome role gate only
(a principal without an `Admin`/`User` role is denied, FR-024).

**Alternatives considered**: A Tome-local user/role store — rejected (Constitution Principle V:
identity is Hive's). Public-client SPA holding tokens — rejected (tokens would reach the browser).

**Cross-repo impact (flagged, Principle III)**: Hive must register a `tome` OAuth client (client-id,
secret, redirect URI `${WEB_BASE_URL}/auth/callback`) and define the Tome `Admin`/`User` roles. This
owned change is bundled with the auth work and requires explicit maintainer approval before it lands.

---

## D2 — Real-time live table transport

**Decision**: **Server-Sent Events (SSE)** via Spring MVC `SseEmitter`. Each campaign exposes
`GET /api/campaigns/{id}/stream`; an authenticated participant subscribes and receives only events
they are authorized to see. An in-process registry maps `campaignId → set of (subscriber, emitter)`.
Domain changes (sheet update, share/reveal, combat turn/round, roll) publish an event that the
registry fans out **after** applying the same authorization used for REST reads (D-owned private
content and other players' private sheets are filtered per subscriber). Client actions stay ordinary
REST; SSE is one-directional server→client.

**Rationale**: The live-table need (FR-019/FR-021/SC-007) is fan-out of server state to viewers, which
is exactly SSE's shape. SSE is plain HTTP (works through the nginx/BFF same-origin path and the
session cookie), auto-reconnects in the browser (`EventSource`), and needs no broker. Campaign sizes
(~1 DM + ≤6 players) fit an in-process registry comfortably (Simplicity First).

**Alternatives considered**: WebSocket + STOMP — rejected: bidirectional and broker-oriented, more
moving parts than one-way fan-out needs. Polling — rejected: laggy, chatty, poor live-combat feel.
External pub/sub (Redis) — rejected: unjustified infrastructure at this scale (revisit only if
campaigns span multiple api instances).

**Implications**: A single api instance for v1 (the in-process registry assumes one process). If
horizontal scaling is later required, swap the registry for a shared bus behind the same publisher
interface — no domain-code change. Authorization lives in one place and is exercised by both REST and
SSE, guarded by a dedicated integration test (privacy is security-critical, SC-004).

---

## D3 — Hybrid rule-set engine: sheet storage & definition (MongoDB)

> **Rewritten 2026-07-22.** Previously Postgres `JSONB` + promoted columns; now MongoDB documents. The
> `RuleSet` engine (interface, `SheetData`, definitions) is **unchanged** — only where and how a sheet
> is persisted changed.

**Decision**: Store each character/NPC as a **MongoDB document**. The sheet values live in a native
BSON sub-document field `data` holding **base inputs only** (derived values are never stored — D8);
cross-cutting values (`name`, `ruleSetId`, `ownerId`) are ordinary top-level document fields, indexed
as needed. A rule set is defined in two parts:

- **Sheet definition (data)**: a versioned JSON document (`resources/rulesets/dnd35/definition.json`)
  describing sections and fields (id, label key, type, constraints, and simple `derivedFrom`
  formulas). Served to the web via `GET /api/rule-sets/{id}`. **Definitions stay bundled code-owned
  resources** — versioned with the app, not stored in the database.
- **Rule-set logic (code)**: the `RuleSet` strategy (interface unchanged) —
  ```
  interface RuleSet {
    fun id(): String
    fun definition(): SheetDefinition
    fun computeDerived(data: SheetData): SheetData          // e.g. 3.5 ability modifiers, saves, BAB
    fun validate(data: SheetData, change: SheetChange): List<RuleWarning>   // soft; never blocks
  }
  ```
  v1 ships `DnD35RuleSet`. The registry resolves a `RuleSet` by id; unknown ids are rejected.
  `computeDerived` now runs on the **read path** (D8), not before a write.

Persistence uses **Spring Data MongoDB via `MongoTemplate`** — the low-level, no-magic template,
mirroring the platform's no-JPA/`JdbcTemplate` convention (we opt into repositories only where they
earn their keep). `SheetData` (`Map<String, Any?>`) maps directly to a BSON document — **no JSON
string round-trip, no `JsonbSupport`** (the `cast(:data as jsonb)` / `getString` dance is gone).

Cross-cutting services (characters, campaigns, combat, dice, realtime, permissions) still depend only
on `SheetData` + `RuleSet`, never on a concrete rule set (satisfies FR-023/SC-009).

**Rationale**: The sheet *is* a document; BSON stores it natively. Per-rule-set flexibility is
inherent — different rule sets are just different-shaped `data` sub-documents in one collection, no
per-field migration. Top-level fields + indexes keep roster/lookup/combat queries fast. Keeping
derived/validation logic in code remains the pragmatic half of Hybrid (3.5's BAB/save progressions are
painful as pure data). See D-model for the embedded-vs-referenced aggregate boundaries.

**Migrations**: **Mongock** (the Flyway replacement) — versioned, ordered, idempotent changelog units
authored in Kotlin, run on boot. v1 changelogs mainly **create collections + indexes** and seed any
reference data; rule-set definitions stay as resources, so there is little bootstrap data to seed.

**Data disposition (Postgres→MongoDB cutover)**: v1 is **pre-production** — US1's data lives only in a
local dev Postgres volume and is **disposable**, so the switch is a **clean cutover** (drop the
Postgres stack; start fresh on MongoDB) with **no data-migration script**. This assumes no
irreplaceable data exists; **if any character data must be preserved, it MUST be exported before the
cutover** (a one-off export→transform→import, out of scope for the changelogs). Recorded as an
assumption to validate at Phase 3B preflight.

**Alternatives considered**: **Postgres `JSONB` + promoted columns** (the prior v1 baseline) — still
technically sound; superseded 2026-07-22 to model the document-shaped domain natively, let the
campaign aggregate embed its owned children in one document (D-model), and keep non-Postgres
competence alive. Typed collections per rule set — rejected (duplicates cross-cutting features). Fully
data-driven rules — rejected (large upfront build, little v1 payoff).

**Validation posture**: `validate()` returns warnings only; the api attaches them to the response and
the DM may proceed (FR-005). Because derived values are computed on read (D8), they can never drift
from the stored inputs — there is no stored derived state to drift.

---

## D4 — Dice engine

**Decision**: A small in-house, **server-authoritative** dice-expression evaluator supporting the
grammar Tome needs: `NdM`, modifiers `± K`, multiple terms (`1d8+1d6+3`), and keep/drop
(`4d6kh3` — keep-highest-3, for ability generation). A roll records its expression, the individual
die results, and the total; results are persisted (`roll` table) and fanned out over SSE. A roll may
optionally target a sheet value (e.g. apply damage to `hp_current`), which goes through the normal
sheet-write path (so `RuleSet` warnings still apply).

**Rationale**: Server-side rolls are auditable and identical for every viewer (a client roll could
diverge or be tampered with). The grammar is tiny and dependency-free (Simplicity First); RNG is
`java.security.SecureRandom` seeded by the JVM (no reproducibility requirement).

**Alternatives considered**: Client-side rolling — rejected (not auditable, can diverge). A dice
library — rejected: more surface than a ~100-line evaluator with focused unit tests.

---

## D5 — Concurrent-edit conflict policy

> **Rewritten 2026-07-22** for MongoDB — same optimistic-concurrency guarantee, native mechanism.

**Decision**: **Optimistic concurrency via Spring Data MongoDB `@Version`**. Every mutable aggregate
document carries a `@Version` field. Saving a document whose stored version no longer matches the one
the caller read throws `OptimisticLockingFailureException`, which the api maps to `409 Conflict`
(RFC-7807 body); the client refetches and reapplies. (Under the hood this is a
`findAndModify`/replace guarded by `{_id, version}` with a `$inc` on version — the same guard the
Postgres `WHERE id = ? AND version = ?` gave, now handled by the framework.) SSE updates keep other
viewers current, so conflicts stay rare.

**Rationale**: Satisfies SC-006 ("no data loss from concurrent edits") with a framework-native
mechanism and no locks — less hand-rolled SQL than the Postgres version. This resolves the item the
spec deferred from clarification to planning.

**Note on transactions**: Multi-document writes that must be atomic require MongoDB multi-document
transactions, which require the server to run as a **replica set** (even single-node). The stack runs
one mongod in replica-set mode (D-model / plan.md), and Testcontainers' `MongoDBContainer` starts a
single-node replica set automatically. Aggregates are designed so most writes touch one document — a
character, a campaign (with its members/npcs/content/rolls), a session, or an encounter (with its
combatants/rolls) — keeping cross-document transactions rare (e.g. apply-roll-to-sheet appends the roll
to its container document and updates a character — two documents, acceptable as a non-atomic
log-then-update).

**Alternatives considered**: Last-write-wins — rejected (silent loss). Pessimistic locking — rejected
(a live table with a DM + players should not block on held locks). Field-level CRDT merge — rejected
(overkill for a handful of editors; revisit only if simultaneous editing of one sheet becomes common).

---

## D6 — Campaign roster & membership (v1 join model)

**Decision**: The DM builds the roster by **adding existing characters** owned by other users
(FR-009). To add a character the DM references it by an owner-shared handle (the character's id, which
the owner can copy from their character list); the api verifies the character's `rule_set_id` matches
the campaign's before creating the `membership` (FR-008). Removing a member deletes the `membership`,
never the character. No in-app self-service invite in v1 (Assumptions); the future Hive invitation-link
capability is noted so the membership model stays compatible (a later `invitation` concept can create
memberships without changing the roster's shape).

**Storage note (2026-07-22, MongoDB)**: A membership is no longer a join row — it is an **embedded
entry in the campaign document** (`campaign.members[]`, each `{characterId, playerId, addedAt}`).
Referencing a shared character by id is still exactly right (characters are their own collection —
D-model). Because members are embedded in the single `campaigns` collection, the relational
`UNIQUE(character_id)` guard for "one active campaign per character" **does** have an index equivalent:
a **unique partial multikey index** on `campaigns` `{"members.characterId": 1}` with
`partialFilterExpression: { status: "active" }` — a unique multikey index rejects the same
`characterId` appearing in two active campaigns' `members[]` (and twice within one). The service still
does an app-level pre-check to return a friendly refusal before the write hits the index. The
rule-set-match check (FR-008) was already a service-level cross-entity rule, unaffected by the switch.

**Rationale**: Matches the maintainer's stated v1 flow (clarify 2026-07-20) and keeps membership a
thin join between an existing character and a campaign. Referencing by character id (owner-shared)
avoids a user-directory search UI in v1 while remaining unambiguous.

**Open UX detail (deferred to tasks)**: exactly how the DM discovers a player's character id (copy
button on the owner's list vs. a lookup) is a small UI decision for `/speckit-tasks`; the API contract
(add character-by-id to a campaign) is unaffected.

**Alternatives considered**: DM searching all platform users/characters — rejected for v1 (needs a
directory surface; heavier than needed). Player self-join via link — deferred to the future Hive
capability.

---

## D7 — Bilingual UI (nb/en)

**Decision**: Mirror avec — `react-i18next` + `i18next` with `nb.json`/`en.json` bundles, English
fallback. Only application chrome and labels are translated; **rule-set game terminology may remain in
its canonical language** (spec Assumption), so sheet-definition field labels come from the rule set's
definition (which may be single-language for 3.5) rather than the UI bundles. Language preference
follows the platform convention (Hive `locale`) where available, otherwise the browser default.

**Rationale**: Consistency with the platform's bilingual requirement and avec's proven setup, without
forcing translation of D&D terminology that players expect in its original form.

**Alternatives considered**: Translating all game terms — rejected (out of scope per the spec and
error-prone for rules text). No i18n — rejected (platform convention requires nb/en).

---

## D8 — Derived sheet values: computed on read, never stored

> **New decision 2026-07-22.** Resolves the "Stored vs. computed derived sheet values" item the
> original plan deferred (plan.md Deferred Decisions). Refines spec FR-005 / Clarification 2026-07-22.

**Decision**: **The server owns the truth; derived values are never persisted.** A stored sheet's
`data` holds **only base inputs** — the values an editor actually enters (ability scores, class,
level, HP, inventory, …). `RuleSet.computeDerived` runs on the **read path**: every response that
carries a sheet (GET, and the echo returned by POST/PUT) returns a **fully resolved** sheet with
derived values (ability modifiers, saves, BAB, initiative, …) filled in. On write, the api strips /
ignores any field the definition marks `derived` before persisting, so only inputs are stored. The
**server response is authoritative**. The web **also** derives on change (client-side) purely for
instant feedback while editing, then reconciles to the server's resolved sheet on save.

**Rationale**: Single source of truth = base inputs; a stored derived value can never disagree with
its inputs because none is stored ("less stored state → fewer error points"). Smaller, more uniform
documents. One authoritative rules implementation (the Kotlin `RuleSet`); the client echo is a UX
nicety, not a second source of truth. Complex 3.5 derivations (BAB/save progressions by class/level)
live only in the server engine — they are **not** reimplemented in TypeScript; the client only echoes
cheap, purely-local derivations (e.g. ability modifier = ⌊(score−10)/2⌋) for zero-latency feel, and
always yields to the server response.

**Consumer impact (FR-023/SC-009 preserved)**: Cross-cutting consumers that need a derived value
(combat, dice apply-to-sheet, the player view, SSE payloads) obtain it from a **per-entity
resolve-on-read helper** (v1: `CharacterDataResolver`; analogous resolvers for other entities such as
NPCs) rather than from stored data. They still touch only `SheetData` + `RuleSet`, never a concrete
rule set. This is the compute-on-read coupling the plan named as the trade — accepted, and centralized
per entity so it stays cheap and consistent.

**Alternatives considered**: **Persist derived values on write** (the prior v1 behavior) — rejected:
redundant state that can drift, and now doubled by the client also computing them. **Client-only
derivation** — rejected: server-authoritative dice/combat would lose access to derived values, and
3.5's non-trivial rules would be duplicated in TypeScript and silently diverge.

---

## Summary of new dependencies / infrastructure

| Item | Status |
|------|--------|
| Spring Boot SSE (`SseEmitter`) | Built into Spring MVC — no new dependency |
| Dice evaluator | In-house, no dependency |
| **MongoDB document storage** | **`spring-boot-starter-data-mongodb` (`MongoTemplate`); replaces Postgres/`JdbcTemplate`/Jackson-JSONB glue** |
| **Schema/data migrations** | **Mongock (Kotlin changelog units); replaces Flyway (`flyway-*`, `postgresql` driver dropped)** |
| **Integration-test DB** | **Testcontainers `MongoDBContainer` (single-node replica set) via `@ServiceConnection`; replaces `testcontainers-postgresql`** |
| Optimistic concurrency | Spring Data MongoDB `@Version` field per mutable aggregate (was a `version` column) |
| Derived sheet values | Computed on read (D8) — no storage, no new dependency |
| Hive `tome` client + roles | **Cross-repo change, maintainer approval required (D1/D6)** |
| Everything else | Inherited from the platform stack (no new infrastructure) |
