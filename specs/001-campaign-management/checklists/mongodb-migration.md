# Checklist: MongoDB Re-platform & Compute-on-Read (Amendment 2026-07-22)

**Purpose**: Requirements-quality gate ("unit tests for English") for the Postgres→MongoDB re-platform
(Phase 3B), the compute-on-read derived-values decision (D8), and the remaining US2–US5 features now
authored against MongoDB. Tests whether the *specs* are complete, clear, consistent, and measurable —
not whether code works.
**Created**: 2026-07-22
**Feature**: [spec.md](../spec.md) · **Sources**: plan.md, research.md (D1–D8), data-model.md, tasks.md, contracts/openapi.yaml

## Requirement Completeness

- [ ] CHK001 - Is "derived values are always recomputed, never stored" captured as a testable requirement (base inputs only persisted; resolved sheet returned on read/write), not only as a decision record? [Completeness, Spec §Clarifications 2026-07-22 / D8]
- [ ] CHK002 - Does the spec define an unambiguous rule for *which* sheet fields are base inputs vs. derived, so the write path knows exactly what to strip/ignore? [Completeness, D8 / data-model §Derived values]
- [x] CHK003 - Is the disposition of existing US1 **Postgres data** stated — clean cutover (dev data discarded) vs. a one-time data migration to MongoDB — rather than left implicit? [Gap, Phase 3B] → **Resolved 2026-07-22**: clean cutover, no migration script (research §D3 "Data disposition"; tasks Phase 3B preflight); export-first safeguard if any data matters.
- [ ] CHK004 - Are the MongoDB **replica-set** requirement and its rationale (multi-document transactions) recorded as a deployment constraint in an authoritative artifact, not only research prose? [Completeness, research §D5]
- [ ] CHK005 - Is each cross-document invariant (one-active-campaign-per-character, rule-set match on join, combatant PC-XOR-NPC, no duplicate member) assigned a single defined enforcement mechanism (index vs. app rule)? [Completeness, data-model §Invariants]
- [ ] CHK006 - Are the Spring Data-native migration expectations (ordering, idempotency, run-on-boot, `_migrations` ledger guard, failure behavior) specified rather than assumed? [Gap, tasks §T089/T091/T038/T062] → **Note 2026-07-23**: migration framework dropped (Mongock deprecated, Flamingock Gradle-only); native index-ensure + ledger (research §Migrations).
- [ ] CHK007 - Are requirements defined for the resolve-on-read helper's role as the *single* source of derived values for every consumer (REST, player view, combat, dice, SSE)? [Completeness, D8 / data-model §Derived values]

## Requirement Clarity & Ambiguity

- [x] CHK008 - Is "the server response is authoritative" for derived values precise about whether the client ever persists its locally-derived values (feedback-only)? [Clarity, D8] → **Resolved 2026-07-23** (T099/T102): the client re-derives locally for display only and sends **base inputs only** on save; `CharacterSheet.test.tsx` asserts the PUT body carries no derived field (`strMod`), and `CharacterSheet.tsx` KDoc states derived are "never persisted and never sent on a write (D8)".
- [ ] CHK009 - Is the tome-db port/replica-set configuration stated unambiguously and identically across compose, quickstart, and env docs (host 5436 → 27017, replica set `rs0`)? [Clarity/Consistency, quickstart §Ports / tasks §T085]
- [ ] CHK010 - Is the replica-set initiation mechanism pinned (init container vs. healthcheck-gated `rs.initiate()`) or explicitly left as an implementer choice? [Ambiguity, tasks §T085]
- [x] CHK011 - Is "no observable REST change / behavioral parity" defined with a verifiable criterion (e.g., the openapi contract tests pass unchanged)? [Measurability, plan §Amendment] → **Resolved 2026-07-23** (T101/T103): the criterion is "`CharacterContractTest` passes unchanged" — its body was untouched by the re-platform (only `@Disabled` + a KDoc dropped) and it is **10/10 green** against the Mongo-backed context in `./mvnw clean verify`.
- [ ] CHK012 - Are the terms "aggregate", "embedded", and "referenced" used consistently and defined once, so US2–US5 authors apply them the same way? [Clarity, data-model §Aggregate boundaries]

## Requirement Consistency (cross-artifact)

- [ ] CHK013 - Does the openapi `Character.data` description (resolved on read; derived not persisted; ignored on write) agree with D8 and data-model? [Consistency, contracts §Character / D8]
- [ ] CHK014 - Do plan.md and data-model.md agree on the exact collection set — **characters, campaigns, sessions, encounters** (rolls embedded, not a collection) — and on what is embedded vs. referenced? [Consistency, plan §Post-design / data-model]
- [ ] CHK015 - Is the "one active campaign per character" guard described consistently after the D6 correction — enforced by a unique partial multikey index **plus** an app pre-check — across research D6, data-model, and tasks (T038/T041)? [Consistency, research §D6 / data-model §Invariants]
- [x] CHK016 - Is optimistic concurrency described consistently as Spring Data `@Version` → 409 across research D5, data-model, plan, and the mapping task (T098)? [Consistency] → **Resolved 2026-07-23** (T098/T103): verified the `@Version` → `OptimisticLockingFailureException` → `409` chain reads consistently in research §D5 (l.175-177, 298), data-model (l.14-15, 246-247), plan (l.193), and is implemented + net-mapped by T098; exercised by the `CharacterIntegrationTest` stale-write 409 case (green).
- [ ] CHK017 - Do the migration changes named in tasks (C001–C004, now Spring Data-native ledger-guarded units) match the migrations list and index definitions in data-model? [Consistency, tasks §T091/T038/T062 / data-model §Migrations]
- [x] CHK018 - Are all Postgres/Flyway/JdbcTemplate mentions that remain in the artifacts clearly historical/comparative rather than active requirements? [Consistency, Gap] → **Resolved 2026-07-23** (T104): the two stale *active* mentions were fixed — `RuleSet.kt` KDoc (claimed data is "Stored as Postgres `JSONB` … via JdbcTemplate/JsonbSupport") and `pom.xml` ("Jackson … SheetData <-> JSONB"). All remaining mentions are unambiguously historical/comparative: the "old Postgres harness" notes in `IntegrationTest`/`application-*.yml`, and `C001`'s "mirrors Flyway/Flamingock" naming analogy. Grep of `api/` (excl. `target/`) confirms no active Postgres/Flyway/JdbcTemplate/JSONB requirement remains.
- [ ] CHK019 - Do the embedded-vs-referenced boundaries in tasks (T039/T049–T052/T067) match data-model's aggregate table exactly? [Consistency]

## Acceptance Criteria Quality

- [ ] CHK020 - Do the performance success criteria (SC-001 sheet save <300 ms; SC-007 live update <3 s) remain valid and technology-agnostic under MongoDB, with no lingering Postgres-specific assumptions? [Measurability, spec §Success Criteria]
- [x] CHK021 - Is there a measurable criterion that the stored character document contains **no** derived fields (so D8 can be objectively verified)? [Measurability, tasks §T100] → **Resolved 2026-07-23** (T100/T103): `CharacterIntegrationTest` reads the raw BSON (`mongo.getCollection("characters")`, `Filters.eq("_id", uuid)`) and asserts no derived keys (`strMod`/`dexMod`/…) are stored — objective, green in `./mvnw clean verify`.
- [x] CHK022 - Is SC-006 (no data loss on concurrent edits) expressed so it is verifiable against the `@Version`/409 mechanism? [Measurability, research §D5] → **Resolved 2026-07-23** (T098/T100/T103): SC-006 is verifiable as "a stale-version write is rejected with 409, not silently overwritten" — covered by the `CharacterIntegrationTest` 409 case and the `CharacterSheet.test.tsx` client-side conflict-surfacing case (both green).

## Scenario & Edge-Case Coverage

- [ ] CHK023 - Are requirements defined for a **dangling reference** (a `members[]` or `combatants[]` entry pointing at a deleted character), with expected read behavior — not left as an unstated "tolerated"? [Edge Case, data-model §Invariants]
- [ ] CHK024 - Is the interaction between archiving a campaign and the active-only partial unique index specified (an archived campaign frees its characters to rejoin)? [Edge Case, data-model §State transitions]
- [ ] CHK025 - Is the non-atomic apply-roll-to-sheet path (log-then-update, no transaction) explicitly accepted and its failure/partial-write behavior specified? [Exception Flow, research §D5 / tasks §T063]
- [x] CHK026 - Are requirements defined for the test **quarantine window** — what guarantees every `@Disabled` US1 test is restored (a gate before US2 begins)? [Coverage, tasks §T088/T103] → **Resolved 2026-07-23** (T103 is the gate): grep of `api/src` finds **zero** `@Disabled` annotations (the T088 quarantines were dropped in T100/T101), and `./mvnw clean verify` runs all US1 tests (36 green) — a `clean` build was required to unmask a stale phantom `JsonbSupportTest.class` lingering in `target/` (source already deleted in T092; see T103 notes / CHK018).
- [ ] CHK027 - Is behavior specified when a required multi-document transaction cannot run because the replica set is unavailable/misconfigured? [Gap, research §D5]

## Non-Functional / Scaling Requirements

- [x] CHK028 - Are growth bounds considered for the campaign aggregate's embedded arrays (`content[]`, `sessions[]`, `npcs[]`) against MongoDB's **16 MB document limit** — is there a stated bound or a spill-to-collection threshold? [Gap, data-model §Aggregate boundaries] → **Resolved 2026-07-22, revised 2026-07-23**: `sessions`/`encounters` are now their own collections (campaign doc stays small); residual candidates `content`/campaign-`rolls` carry the ~2 MB soft-cap + spill plan (data-model "Document-size bound"; plan Deferred Decisions; write-guard T049).
- [x] CHK029 - Are the write-amplification implications of embedding acknowledged (editing one NPC/note — and now every live-combat turn — updates the campaign document) and deemed acceptable at the stated scale? [Coverage, data-model §Aggregate boundaries] → **Resolved 2026-07-23**: write-amplification is **designed out** — `sessions`/`encounters` are their own collections, so a live-combat turn-advance writes only the small encounter document (its own `@Version`), never the campaign (data-model §Aggregate boundaries / Invariants).
- [ ] CHK030 - Is the operational cost of running two database technologies (backup, restore, runbook, local dev) acknowledged as an owned trade in an authoritative place? [Dependency, plan §Complexity Tracking]

## Dependencies & Assumptions

- [x] CHK031 - Is the assumption that no production/irreplaceable US1 data exists (making a clean cutover safe) documented and validated? [Assumption, Phase 3B] → **Resolved 2026-07-22**: documented as an assumption in research §D3 + Phase 3B preflight (validate at preflight; export-first if untrue).
- [x] CHK032 - Is version compatibility among Spring Boot 4.1, Spring Data MongoDB, and the MongoDB server version documented or flagged for validation? [Assumption/Dependency, plan §Technical Context] → **Resolved 2026-07-23**: the Mongock driver dependency is gone (framework dropped). Remaining stack is Boot 4.1 → Spring Data MongoDB 5.1.0 → `mongodb-driver-sync` 5.8.0 (Boot-BOM-managed) against `mongo:8`; no third-party migration lib to version-match (research §Migrations).
- [ ] CHK033 - Is it stated that the Hive auth/session/cookie layer is unaffected by the storage switch (no coupling to the database)? [Assumption, research §D1]

## US2–US5 Requirement Quality (authored on MongoDB)

- [ ] CHK034 - Are the SSE authorization requirements confirmed unchanged and consistent with the embedded model (permission decisions made over a loaded aggregate, applied identically to REST and SSE)? [Consistency, research §D2 / data-model §Authorization]
- [ ] CHK035 - Is it specified whether the limited player view loads the whole campaign document and filters server-side, and is that approach acceptable for privacy (SC-004) and payload size? [Clarity, spec §FR-011 / data-model §Authorization]
- [ ] CHK036 - Are US2–US5 acceptance scenarios still fully traceable to requirements after the storage rewrite (no scenario silently orphaned by moving to embedded aggregates)? [Traceability, spec §User Scenarios]

## Amendment Follow-ups (2026-07-23 model refinements)

> Model settled on: `sessions` and `encounters` as **their own collections** (referenced by id,
> assembled at read time); `rolls` **embedded** in their container; combatant `combatantId`/`combatantType`.

- [x] CHK037 - Are `sessions` and `encounters` specified consistently as **their own collections**
  (session→campaignId, encounter→sessionId/campaignId, each with `@Version`, assembled into the campaign
  view at read time) across data-model, openapi (nested endpoints + schemas), plan, and tasks
  (T052/T062/T067)? [Consistency] → **Resolved 2026-07-23** (supersedes the earlier nest-in-campaign idea).
- [x] CHK038 - Is the combatant model specified consistently as `combatantId` + `combatantType ∈ {character, npc}` (replacing the `characterId`/`npcId` XOR) across data-model Invariants, the openapi `Encounter`/start-combatants schemas, and tasks T067? [Consistency] → **Resolved 2026-07-23**.
- [x] CHK039 - Are `rolls` specified consistently as **embedded per-container** (`campaign`/`session`/`encounter` each own a `rolls[]`; no `rolls` collection; no `campaignId`/`sessionId`/`encounterId` on the roll; created via nested endpoints) across data-model, openapi, and tasks T063 — resolving the earlier "where do rolls belong" thinker? [Ambiguity → resolved] → **Resolved 2026-07-23**; field is `initiatorId` (spelling corrected).
- [x] CHK040 - Is the referential integrity for the now-referenced `sessions`/`encounters` specified (app-maintained `campaignId`/`sessionId`, cascade-on-hard-delete, orphan tolerance) since Mongo enforces no FK? [Completeness, data-model §Invariants] → **Resolved 2026-07-23**.

## Notes

- Items are requirement-quality checks, not implementation tests. Resolve by editing the spec/plan/
  research/data-model/tasks — not by writing code.
- High-signal gaps to weigh first: ~~CHK003/CHK031~~ (existing-data disposition — **resolved
  2026-07-22**), ~~CHK028~~ (16 MB document limit — **resolved 2026-07-22**), **CHK005/CHK015**
  (invariant enforcement clarity), **CHK006** (native migration/ledger discipline) — the latter two
  remain open for a reviewer pass. *(CHK032 version-compat resolved 2026-07-23: migration framework
  dropped — Mongock deprecated, Flamingock Gradle-only; see research §Migrations.)*
