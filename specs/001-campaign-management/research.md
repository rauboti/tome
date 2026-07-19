# Research: Campaign & Character Management

**Feature**: 001-campaign-management | **Date**: 2026-07-20 | **Plan**: [plan.md](plan.md)

Decisions that resolve the plan's unknowns. Platform-standard choices (Kotlin/Spring Boot 4.1 BFF,
`JdbcTemplate`, Postgres/Flyway, Hive OAuth, React 19/`@rauboti/ui`, Vitest/MockK/Testcontainers,
Docker Compose) are inherited from the sibling apps (avec/pulse/taskmaster) and the Tome constitution
and are not re-litigated here. Only the tome-specific decisions are recorded.

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

## D3 — Hybrid rule-set engine: sheet storage & definition

**Decision**: Store each character's/NPC's sheet values as a Postgres **`JSONB`** column `data`, with
cross-cutting values promoted to real columns (`name`, `rule_set_id`, `owner_id`, `hp_current`,
`hp_max`, `version`). A rule set is defined in two parts:

- **Sheet definition (data)**: a versioned JSON document (`resources/rulesets/dnd35/definition.json`)
  describing sections and fields (id, label key, type, constraints, and simple `derivedFrom`
  formulas). Served to the web via `GET /api/rule-sets/{id}` so the generic renderer builds the UI.
- **Rule-set logic (code)**: a `RuleSet` strategy —
  ```
  interface RuleSet {
    fun id(): String
    fun definition(): SheetDefinition
    fun computeDerived(data: SheetData): SheetData          // e.g. 3.5 ability modifiers, saves, BAB
    fun validate(data: SheetData, change: SheetChange): List<RuleWarning>   // soft; never blocks
  }
  ```
  v1 ships `DnD35RuleSet`. The registry resolves a `RuleSet` by id; unknown ids are rejected.

Cross-cutting services (characters, campaigns, combat, dice, realtime, permissions) depend only on
`SheetData` + `RuleSet`, never on a concrete rule set (satisfies FR-023/SC-009).

**Rationale**: `JSONB` gives per-rule-set flexibility without a schema migration per field, while
promoted columns keep the queries combat/roster/lookup need fast and typed. Jackson 3
(`jackson-module-kotlin`) serializes `SheetData` to/from `JSONB` through `JdbcTemplate`. Keeping
derived/validation logic in code is the pragmatic half of Hybrid (3.5's BAB/save progressions and
resource rules are painful as pure data). This is the model the spec's 2026-07-20 clarification chose.

**Alternatives considered**: Typed tables per rule set — rejected (spec clarification): duplicates
cross-cutting features. Fully data-driven rules — rejected: a rules DSL expressive enough for 3.5 is a
large upfront build with little v1 payoff. EAV tables — rejected: worse ergonomics than `JSONB` for
document-shaped sheets.

**Validation posture**: `validate()` returns warnings only; the api attaches them to the response and
the DM may proceed (FR-005). Derived values are recomputed server-side on every write so they can
never drift from stored inputs.

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

**Decision**: **Optimistic concurrency**. Every sheet (and other mutable aggregate) carries a
`version` integer. A write sends the `version` it read; the api `UPDATE … WHERE id = ? AND version =
?` and bumps `version`. A no-row-updated result → `409 Conflict` (RFC-7807 body) and the client
refetches and reapplies. SSE updates keep other viewers current, which makes conflicts rare.

**Rationale**: Satisfies SC-006 ("no data loss from concurrent edits") with a single column and no
locks. This resolves the item the spec deferred from clarification to planning.

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

## Summary of new dependencies / infrastructure

| Item | Status |
|------|--------|
| Spring Boot SSE (`SseEmitter`) | Built into Spring MVC — no new dependency |
| Dice evaluator | In-house, no dependency |
| `JSONB` sheet storage | Postgres native + Jackson 3 (already in the stack) |
| Optimistic concurrency | One `version` column per mutable aggregate |
| Hive `tome` client + roles | **Cross-repo change, maintainer approval required (D1/D6)** |
| Everything else | Inherited from the platform stack (no new infrastructure) |
