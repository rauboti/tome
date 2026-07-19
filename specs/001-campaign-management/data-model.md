# Data Model: Campaign & Character Management

**Feature**: 001-campaign-management | **Date**: 2026-07-20 | **Plan**: [plan.md](plan.md)

Persistence is PostgreSQL via `JdbcTemplate` + Flyway (no JPA). Sheet values live in `JSONB`;
cross-cutting values are promoted to columns. All ids are UUIDs. `owner_id` / `dm_id` / `user_id`
hold the **Hive subject** (there is no local user table — identity is Hive's, research D1). Every
mutable aggregate carries a `version` integer for optimistic concurrency (research D5) and
`created_at` / `updated_at` timestamps.

Rule sets are **not** a user-writable table: a rule set is a bundled *definition* (JSON) + *logic*
(`RuleSet` code), resolved by `rule_set_id` (a validated string, e.g. `dnd35`). v1 recognizes exactly
one: `dnd35`.

---

## Entities

### character (player character)

The paper-sheet replacement (User Story 1). Owned by a user, built for one rule set.

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `owner_id` | text | Hive subject of the owner |
| `rule_set_id` | text | Validated against the engine; fixed for life (FR-002) |
| `name` | text | Promoted from the sheet for lists/rosters |
| `hp_current` | int | Promoted for combat/dice; nullable until set |
| `hp_max` | int | Promoted; nullable until set |
| `data` | jsonb | The sheet values, shaped by the rule set's definition |
| `version` | int | Optimistic concurrency |
| `created_at` / `updated_at` | timestamptz | |

- **Relationships**: owned by a user (`owner_id`); may have 0..1 active `membership` (v1: one active
  campaign per character — Assumptions).
- **Validation**: `rule_set_id` must be recognized; `data` validated by `RuleSet.validate` on write
  (warnings only, never blocks — FR-005); derived values recomputed by `RuleSet.computeDerived` on
  every write so stored derived values never drift.

### campaign

A game run by a DM, bound to one rule set (User Story 2).

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `dm_id` | text | Hive subject of the sole DM (v1 — Assumptions) |
| `rule_set_id` | text | Fixed for life; gates which characters may join (FR-002/FR-008) |
| `name` | text | |
| `status` | text | `active` \| `archived` (see state transitions) |
| `version` | int | |
| `created_at` / `updated_at` | timestamptz | |

- **Relationships**: has many `membership`, `npc`, `content`, `session`, `encounter`.

### membership (roster entry)

Links a player's character into a campaign (research D6). Created by the DM.

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `campaign_id` | UUID FK → campaign | |
| `character_id` | UUID FK → character | |
| `player_id` | text | Hive subject of the character's owner (denormalized for authz) |
| `created_at` | timestamptz | |

- **Constraints**: `UNIQUE(campaign_id, character_id)`; **and** `UNIQUE(character_id)` in v1 (one
  active campaign per character). Insert is rejected unless `character.rule_set_id =
  campaign.rule_set_id` (FR-008) — enforced in the service (cross-table rule).
- **Lifecycle**: deleting a membership removes the character from the roster without deleting the
  `character` (FR-009, edge case).

### npc

A DM-controlled non-player character under the campaign's rule set (User Story 3).

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `campaign_id` | UUID FK → campaign | |
| `rule_set_id` | text | Equals the campaign's rule set |
| `name` | text | |
| `hp_current` / `hp_max` | int | Promoted for combat |
| `data` | jsonb | Sheet values (same engine as `character`) |
| `is_private` | boolean | If true, hidden from players (FR-011/FR-013); default true |
| `version` | int | |
| `created_at` / `updated_at` | timestamptz | |

- **Note**: `character` and `npc` share the same sheet engine (`SheetData` + `RuleSet`); they are
  separate tables because ownership/visibility differ (player-owned vs DM-owned).

### content (note / shared content)

DM-authored information, private or shared (User Story 3, FR-013/FR-016).

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `campaign_id` | UUID FK → campaign | |
| `title` | text | |
| `body` | text | |
| `visibility` | text | `private` (DM-only) \| `shared` (all players in the campaign) |
| `version` | int | |
| `created_at` / `updated_at` | timestamptz | |

### session

A recorded unit of play for prep-before / continue-after (User Story 3, FR-018).

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `campaign_id` | UUID FK → campaign | |
| `title` | text | |
| `scheduled_for` | timestamptz | nullable (prep before play) |
| `notes` | text | DM prep/recap; not player-visible unless surfaced as `content` |
| `status` | text | `planned` \| `completed` |
| `created_at` / `updated_at` | timestamptz | |

### encounter

An active combat within a campaign (User Story 4, FR-021).

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `campaign_id` | UUID FK → campaign | |
| `session_id` | UUID FK → session | nullable |
| `status` | text | `pending` \| `active` \| `ended` |
| `round` | int | ≥ 1 while active |
| `current_turn` | int | Index into the initiative-ordered combatants |
| `version` | int | |
| `created_at` / `updated_at` | timestamptz | |

### combatant

A participant in an encounter — a `character` or an `npc` (User Story 4).

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `encounter_id` | UUID FK → encounter | |
| `character_id` | UUID FK → character | nullable (set for PCs) |
| `npc_id` | UUID FK → npc | nullable (set for NPCs) |
| `initiative` | int | Determines order (desc) |
| `display_order` | int | Tie-break / manual reorder |
| `is_revealed` | boolean | Whether players see this combatant's presence/state (FR-021) |

- **Constraint**: exactly one of `character_id` / `npc_id` is non-null.

### roll

A recorded in-app dice roll (User Story 4, FR-020).

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `campaign_id` | UUID FK → campaign | nullable (a roll can be made outside a campaign) |
| `encounter_id` | UUID FK → encounter | nullable |
| `roller_id` | text | Hive subject who rolled |
| `expression` | text | e.g. `2d6+3`, `4d6kh3` |
| `results` | jsonb | Individual die faces |
| `total` | int | Computed total |
| `applied_to` | jsonb | Optional: `{targetType, targetId, field}` when applied to a sheet |
| `created_at` | timestamptz | |

---

## State transitions

- **campaign.status**: `active` → `archived` (FR-010, preserves members' characters). No hard delete
  in v1; archiving is the terminal state.
- **encounter.status**: `pending` → `active` (DM starts combat; initiative ordered) → `ended`. While
  `active`, `round` and `current_turn` advance on the DM's turn action (FR-021); advancing past the
  last combatant increments `round` and resets `current_turn`.
- **session.status**: `planned` → `completed`.

## Authorization model (applies to REST reads/writes and SSE events — research D1/D2)

- A **character** is readable/writable by its `owner_id`; readable by the DM of any campaign it is a
  member of; **not** readable by other players (FR-011/FR-014).
- **npc** and `content` with `is_private`/`visibility = private` are DM-only; `content` with
  `visibility = shared` and `npc`/combatant state with `is_revealed = true` are visible to all
  players in the campaign (FR-011/FR-013).
- **campaign**/roster is visible to the DM (full) and to each player (self + shared) — the limited vs
  full view (FR-011/FR-012).
- The DM optionally owning a `character` in their own campaign does not grant players DM visibility,
  and does not let the DM hide content from themselves (spec edge cases).
- The same `PermissionService` decision is applied when fanning out SSE events, so a live update is
  delivered only to subscribers authorized for that content (SC-004).

## Migrations (Flyway, indicative order)

`V1` character · `V2` campaign + membership · `V3` npc · `V4` content · `V5` session · `V6`
encounter + combatant · `V7` roll. Indexes: `membership(campaign_id)`, `UNIQUE membership(character_id)`,
`npc(campaign_id)`, `content(campaign_id)`, `combatant(encounter_id)`, `roll(campaign_id)`.
