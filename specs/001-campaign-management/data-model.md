# Data Model: Campaign & Character Management

**Feature**: 001-campaign-management | **Date**: 2026-07-20 (rewritten 2026-07-22) | **Plan**: [plan.md](plan.md)

> **Rewritten 2026-07-22 (post-US1)** for **MongoDB** (research D3/D5/D8). Previously nine relational
> Postgres tables with `JSONB` sheets; now a small set of document collections with owned children
> embedded. The `RuleSet` engine and REST contract are unchanged; only persistence shape changed.

Persistence is **MongoDB** via Spring Data MongoDB (`MongoTemplate` — the low-level template, no JPA,
mirroring the platform's `JdbcTemplate` convention). Migrations/indexes are **Spring Data-native** (index
ensure on boot + a small applied-changes ledger — no migration framework; see §Migrations). Sheet values live in a native BSON sub-document — a **typed, discriminated
sheet** (ADR-001; `_class` = the rule set's `@TypeAlias`, e.g. `dnd35`) holding **base inputs
only**; derived values are **computed properties on read, never stored** (D8 principle, typed
mechanism — ADR-001). All ids are UUIDs (`_id`). Identity
is Hive's — `userId` / `dmId` / `playerId` / `initiatorId` hold the **Hive subject**; there is no local
user collection (research D1). Every mutable aggregate carries a Spring Data **`@Version`** field for
optimistic concurrency (research D5) and `createdAt` / `updatedAt` timestamps.

Rule sets are **not** stored: a rule set is a bundled *definition* (JSON resource) + *logic* (`RuleSet`
code), resolved by `ruleSetId` (a validated string, e.g. `dnd35`). v1 recognizes exactly one: `dnd35`.

## Aggregate boundaries (embedded vs. referenced)

The core modeling decision. A document embeds what it **wholly owns and that has no independent
lifecycle**; it **references** (by id) anything **shared or independently owned**.

| Concept | Home | Why |
|---------|------|-----|
| **character** | own collection `characters` | player-owned, shared across contexts (referenced by campaign `members[]` and encounter `combatants[]`) — cannot be embedded |
| **campaign** | own collection `campaigns` (aggregate root) | the DM's hub; embeds members/npcs/content and campaign-level rolls |
| membership | **embedded** in `campaign.members[]` | a campaign-owned link; references a `characterId` |
| npc | **embedded** in `campaign.npcs[]` | DM-owned, campaign-scoped, no life outside the campaign |
| content (note) | **embedded** in `campaign.content[]` | DM-authored, campaign-scoped |
| **session** | own collection `sessions` | references `campaignId`; **its own document** so a single session is directly queryable and the campaign doc stays small (well clear of 16 MB); embeds its own `rolls[]` |
| **encounter** | own collection `encounters` | a combat **happens in a session** (references `sessionId` + `campaignId`); **its own document** so live-combat writes hit a small doc and combat history never bloats the campaign; embeds `combatants[]` and its own `rolls[]` |
| combatant | **embedded** in `encounter.combatants[]` | wholly owned by the encounter |
| roll | **embedded** in its container — `campaign.rolls[]` / `session.rolls[]` / `encounter.rolls[]` | scope **is** the container, so the roll needs no `campaignId`/`sessionId`/`encounterId` fields |
| rule set | **not stored** — bundled JSON resource + `RuleSet` code | code-owned, versioned with the app |

**Payoff**: each document stays small and **independently queryable** — a single session or encounter
is fetched directly by `_id`; live-combat writes touch only the small `encounter` document (its own
`@Version`), so a turn-advance never rewrites or version-locks the whole campaign. The DM's full
campaign view is **assembled at read time** — load the `campaign`, its `sessions` (by `campaignId`) and
their `encounters` (by `sessionId`), and stitch by id. **Trade**: the full view is a few indexed
queries + an application-side join rather than one read (trivial at v1 scale), and referential
integrity between `campaign → sessions → encounters` is **app-maintained** (no FK — see Invariants).

**Document-size bound (MongoDB 16 MB/document)** — *largely designed out*: splitting `sessions` and
`encounters` into their own collections keeps the campaign document small and far from the 16 MB limit
even for a long campaign; each session/encounter document is itself bounded (one session's notes/rolls;
one combat's combatants/rolls). The residual in-campaign growth candidates are `content[]` and
campaign-level `rolls[]` (and `npcs[]`); if either ever approaches a soft cap (guideline **~2 MB**), the
same split-to-own-collection move applies. The service SHOULD still guard any single write against the
hard limit. Tracked as a post-v1 revisit (plan.md Deferred Decisions).

---

## Collections

### `characters`

The paper-sheet replacement (User Story 1). Owned by a user, built for one rule set.

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | |
| `userId` | UUID | Hive subject of the owning user |
| `ruleSetId` | string | Validated against the engine; fixed for life (FR-002) |
| `name` | string | Top-level for lists/rosters |
| `data` | document | The character's **typed sheet base inputs** (ADR-001) — a discriminated sub-document (`_class` = the rule set's `@TypeAlias`, e.g. `dnd35`); base inputs only, incl. HP inputs `hpCurrent`/`hpMax` (entered). Derived values (ability modifiers, saves, BAB, AC, …) are **computed properties on read, never stored — by construction** (a getter-only property has no backing field), not a strip step (D8/ADR-001) |
| `version` | int (`@Version`) | Optimistic concurrency |
| `createdAt` / `updatedAt` | instant | |

- **Base-inputs-only**: the stored sub-document is a typed base-inputs object; derived values are
  **computed properties** on the typed sheet, so they are **never persisted by construction** — no
  write-time strip step, because a getter-only `val` has no backing field (ADR-001, superseding the
  definition-driven `computeDerived`/strip of D8). The REST response (GET, and the POST/PUT echo)
  serializes the sheet with its computed derived values — a **fully resolved** sheet.
- **Relationships**: owned by a user (`userId`); referenced by `campaign.members[]` and
  `encounter.combatants[]`. A character participates in at most one **active** campaign (v1 —
  Invariants).
- **Validation**: `ruleSetId` must be recognized; `data` validated by `RuleSet.validate` on write
  (warnings only, never blocks — FR-005).
- **Indexes**: `{ userId: 1 }` (list a user's characters).

### `campaigns`

A game run by a DM, bound to one rule set (User Story 2) — the aggregate root that **embeds** its
roster, NPCs, content, and campaign-level rolls, and **references** its sessions (own collection).

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | |
| `dmId` | UUID | Hive subject of the sole DM (v1 — Assumptions) |
| `ruleSetId` | string | Fixed for life; gates which characters may join (FR-002/FR-008) |
| `name` | string | |
| `status` | string | `active` \| `archived` (state transitions) |
| `version` | int (`@Version`) | |
| `members[]` | embedded | roster entries (below) |
| `npcs[]` | embedded | DM NPCs (below) |
| `content[]` | embedded | notes / shared content (below) |
| `rolls[]` | embedded | campaign-level rolls (not tied to a session/encounter) — the shared roll sub-doc (below) |
| `createdAt` / `updatedAt` | instant | |

- **Sessions are not embedded** — they live in the `sessions` collection and reference `campaignId`
  (below). Load them separately when assembling the full campaign view.
- **Indexes**: `{ dmId: 1 }` (a DM's campaigns); `{ "members.playerId": 1 }` (campaigns a player is
  in); **unique partial multikey** `{ "members.characterId": 1 }` with
  `partialFilterExpression: { status: "active" }` (see Invariants).

#### `members[]` (roster entry — embedded)

Links a player's character into the campaign (research D6). Created by the DM.

| Field | Type | Notes |
|-------|------|-------|
| `characterId` | UUID | reference into `characters` |
| `playerId` | UUID | Hive subject of the character's owner (denormalized for authz) |
| `addedAt` | instant | |

- Insert is rejected unless `character.ruleSetId == campaign.ruleSetId` (FR-008) — enforced in the
  service (cross-document rule). Removing an entry drops it from `members[]` without deleting the
  `character` (FR-009). No `_id` needed — the entry is keyed by `characterId` within the campaign.

#### `npcs[]` (DM-controlled NPC — embedded)

A DM-controlled non-player character under the campaign's rule set (User Story 3). Same sheet engine
as `character` (the typed sheet + `RuleSet`, ADR-001); embedded because ownership/visibility are the
DM's and it has no life outside the campaign.

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | stable id (referenced by `encounter.combatants[]`) |
| `name` | string | |
| `data` | document | The NPC's **typed sheet base inputs** (ADR-001; discriminated by `_class`) incl. HP inputs `hpCurrent`/`hpMax` (entered); derived values are computed properties on read, never stored (D8/ADR-001) |
| `isPrivate` | boolean | If true, hidden from players (FR-011/FR-013); default true |

#### `content[]` (note / shared content — embedded)

DM-authored information, private or shared (User Story 3, FR-013/FR-016).

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | |
| `title` | string | |
| `body` | string | |
| `visibility` | string | `private` (DM-only) \| `shared` (all players in the campaign) |

### `sessions`

A unit of play (User Story 3, FR-018) — **its own collection**, referencing the campaign. Prep-before /
continue-after; embeds any rolls made at session scope (outside an encounter).

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | referenced by `encounter.sessionId` |
| `campaignId` | UUID | parent campaign (reference) |
| `title` | string | |
| `scheduledFor` | instant | nullable (prep before play) |
| `notes` | string | DM prep/recap; not player-visible unless surfaced as `content` |
| `status` | string | `planned` \| `completed` |
| `rolls[]` | embedded | session-scoped rolls — the shared roll sub-doc (below) |
| `version` | int (`@Version`) | |
| `createdAt` / `updatedAt` | instant | |

- **Indexes**: `{ campaignId: 1 }` (list a campaign's sessions).

### `encounters`

An active combat that **happens in a session** (User Story 4, FR-021) — **its own collection**,
referencing both its session and campaign. Its own document keeps live-combat writes small and off the
campaign doc. Embeds its combatants and encounter-scoped rolls.

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | used by combat endpoints and roll context |
| `sessionId` | UUID | parent session (reference) |
| `campaignId` | UUID | parent campaign — **denormalized** for per-campaign scoping (SSE stream, authz) without a session hop |
| `status` | string | `pending` \| `active` \| `ended` |
| `round` | int | ≥ 1 while active |
| `currentTurn` | int | Index into the initiative-ordered `combatants[]` |
| `combatants[]` | embedded | participants (below) |
| `rolls[]` | embedded | encounter-scoped rolls — the shared roll sub-doc (below) |
| `version` | int (`@Version`) | **live-combat concurrency unit** — a turn-advance locks only this encounter, not the campaign |
| `createdAt` / `updatedAt` | instant | |

- **Indexes**: `{ sessionId: 1 }`, `{ campaignId: 1 }`.

#### `combatants[]` (embedded in an encounter)

A participant — a player character or an NPC (User Story 4).

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | the combatant entry's own id |
| `combatantId` | UUID | the participant's id — a `characters._id` **or** a `campaign.npcs[]._id` |
| `combatantType` | string | `character` \| `npc` — discriminates what `combatantId` points at |
| `initiative` | int | Determines order (desc) |
| `displayOrder` | int | Tie-break / manual reorder |
| `isRevealed` | boolean | Whether players see this combatant's presence/state (FR-021) |

- **Invariant** (app-enforced): `combatantType ∈ {character, npc}`, and `combatantId` resolves to the
  matching entity — a `characters` document when `character`, a `campaign.npcs[]` entry when `npc`.
  Optionally backed by a `$jsonSchema` validator.

### Roll (embedded sub-document — no own collection)

A recorded in-app dice roll (User Story 4, FR-020). A roll is **embedded in the entity it belongs to** —
`campaign.rolls[]` (campaign-scoped), `session.rolls[]` (session-scoped), or `encounter.rolls[]`
(encounter-scoped). Its scope **is** its container, so it carries **no `campaignId`/`sessionId`/
`encounterId`** fields. Append-only within its container.

| Field | Type | Notes |
|-------|------|-------|
| `_id` | UUID | |
| `initiatorId` | UUID | Hive subject who initiated the roll |
| `expression` | string | e.g. `2d6+3`, `4d6kh3` |
| `results` | array | Individual die faces |
| `total` | int | Computed total |
| `appliedTo` | document | Optional: `{ targetType, targetId, field }` when applied to a sheet |
| `createdAt` | instant | |

- **No separate `rolls` collection and no scope-id fields** (resolves the earlier "where do rolls
  belong?" thinker): the container answers "where". A roll is created under its container resource
  (see contracts) and appended to that container's `rolls[]`.

---

## Invariants (app-enforced, since Mongo has no FK/CHECK across documents)

- **One active campaign per character** (was `UNIQUE(character_id)`): enforced by the unique partial
  multikey index on `campaigns.members.characterId` (active only) **plus** a service pre-check for a
  friendly refusal (research D6).
- **No duplicate member in a campaign** (was `UNIQUE(campaign_id, character_id)`): the same unique
  multikey index rejects a `characterId` appearing twice in one campaign's `members[]`.
- **Rule-set match on join** (FR-008): service checks `character.ruleSetId == campaign.ruleSetId`
  before pushing a member (cross-document rule; no DB constraint).
- **Combatant type**: `combatantType ∈ {character, npc}` and `combatantId` resolves to the matching
  entity — service-enforced (optionally a `$jsonSchema` validator). Replaces the former "exactly one of
  `characterId`/`npcId`" XOR rule.
- **Parent references (no FK)**: `session.campaignId` and `encounter.sessionId`/`encounter.campaignId`
  are app-maintained references — Mongo enforces nothing. Archiving a campaign preserves its sessions
  and encounters (FR-010). A future hard-delete of a campaign/session MUST cascade in the service
  (delete its sessions, and their encounters); until hard-delete exists, an orphaned session/encounter
  is not reachable via its campaign and is tolerated.
- **Referential cleanup**: removing a `member` never deletes the `character` (FR-009). Deleting a
  character does not cascade — a stale `characterId` (in `members[]`) or `combatantId` (in an
  encounter's `combatants[]`) is tolerated and resolved defensively on read (the reference simply no
  longer resolves).
- **Optimistic concurrency**: `@Version` on the aggregate roots `characters`, `campaigns`, `sessions`,
  and `encounters` → `OptimisticLockingFailureException` → `409` (research D5, SC-006). Each root's
  embedded children share that root's version: `members`/`npcs`/`content` and campaign-level `rolls`
  under the **campaign**; `combatants` and encounter-level `rolls` under the **encounter**. Because
  `sessions`/`encounters` are their own documents, **live-combat writes lock only the small encounter**
  — a turn-advance never contends with a DM content edit on the campaign. *(Revisit the openapi
  `Npc.version` field when US3 is built — an embedded NPC has no independent version; it would echo the
  campaign's or be dropped.)*

## State transitions

- **campaign.status**: `active` → `archived` (FR-010, preserves members' characters). No hard delete
  in v1; archiving is terminal. (Archiving clears a character from the active-uniqueness index, freeing
  it to join a new campaign.)
- **encounter.status**: `pending` → `active` (DM starts combat; initiative ordered) → `ended`. While
  `active`, `round`/`currentTurn` advance on the DM's turn action (FR-021); advancing past the last
  combatant increments `round` and resets `currentTurn`.
- **session.status**: `planned` → `completed`.

## Authorization model (applies to REST reads/writes and SSE events — research D1/D2)

Unchanged by the storage switch — authorization is service logic over the loaded aggregate, not a
storage feature.

- A **character** is readable/writable by its `userId`; readable by the DM of any campaign it is a
  member of; **not** readable by other players (FR-011/FR-014).
- Embedded `npcs`/`content` with `isPrivate` / `visibility = private` are DM-only; `content` with
  `visibility = shared` and `npc`/combatant state with `isRevealed = true` are visible to all players
  in the campaign (FR-011/FR-013).
- **campaign**/roster is visible to the DM (full) and to each player (self + shared) — the limited vs.
  full view (FR-011/FR-012).
- The DM optionally owning a `character` in their own campaign does not grant players DM visibility,
  and does not let the DM hide content from themselves (spec edge cases).
- The same `PermissionService` decision is applied when fanning out SSE events, so a live update is
  delivered only to subscribers authorized for that content (SC-004).

## Derived values (D8 principle; typed mechanism — ADR-001)

Stored sheets hold base inputs only. Derived values are **computed properties on the typed sheet**
(ADR-001) — evaluated on access, never persisted (a getter-only property has no backing field), so
they cannot drift from their inputs. There is **no resolve-on-read helper and no write-time strip**:
the earlier `CharacterDataResolver` / definition-driven `RuleSet.computeDerived` are retired. Every
consumer — REST responses, the player view, combat/dice, SSE payloads — reads the same typed sheet
and its computed derived directly; cross-cutting consumers depend on the sheet's **projection
interfaces** (e.g. a combatant view), not on any concrete rule set (FR-023/SC-009). The web mirrors
the typed sheet and computes the same derived for instant feedback, reconciling to the server response.

## Migrations (Spring Data-native, indicative order)

No migration framework (Mongock deprecated; Flamingock is Gradle-only — see research §Migrations).
On boot the app **ensures indexes idempotently** from a code-owned index catalog, and records ordered
structural changes in a small **applied-changes ledger** (`_migrations`: `{_id: changeId, appliedAt}`)
so each runs at most once. Indicative order (`changeId`s):

`C001` create `characters` + index `{userId:1}` · `C002` create `campaigns` + indexes
(`{dmId:1}`, `{"members.playerId":1}`, unique-partial `{"members.characterId":1}`) · `C003` create
`sessions` + index `{campaignId:1}` · `C004` create `encounters` + indexes `{sessionId:1}`,
`{campaignId:1}`. **No `rolls` change** — rolls are embedded in their container
(`campaign`/`session`/`encounter`), not a collection. Rule-set definitions stay bundled JSON resources
— no seed data needed in v1. (`C00x` ids are retained as stable change identifiers, now ledger keys
rather than Mongock changelog classes.)
