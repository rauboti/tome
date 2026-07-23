# ADR-001: Strongly-typed, code-first rule-set sheets

**Date:** 2026-07-24
**Status:** Proposed
**Supersedes:** research.md **D3** (Hybrid data-driven engine) and **D8** (definition-driven
compute-on-read), in part — see *Decision* and *What survives* below.
**Relates to:** spec **FR-023 / SC-009** (require softening — see *Propagation*), plan.md
Complexity Tracking rows "MongoDB documents + `RuleSet` strategy engine" and "Derived values
computed on read".

> First ADR for tome. Decisions to date live as `research.md` D-entries and `plan.md` amendment
> blocks; this one is large and reverses two of those, so it gets its own record. Future
> significant decisions may follow suit under `specs/[feature]/decisions/`.

## Context

The Hybrid rule-set engine (D3) models every character/NPC sheet as `SheetData = Map<String, Any?>`
— an opaque map — whose *structure* is authored as data (`rulesets/dnd35/definition.json`: sections,
fields, `derivedFrom` formula strings) and whose *derived values* are computed on read (D8) by a
formula engine (`FormulaEvaluator` + `SheetCompute` on the server, mirrored by `derive.ts` on the
web). Cross-cutting services (characters, combat, dice, realtime, permissions) were deliberately
built to touch only `SheetData + RuleSet`, never a concrete rule set, so that adding a rule set is
"just a definition file + a logic class" with no shared-engine change (FR-023/SC-009).

That flexibility was the right call for a fast prototype and for a hypothetical homebrew-via-JSON
future. It is no longer the right call for where the product actually is:

- **v1 ships D&D 3.5 only, and Dark Souls (US5) is itself a code story** — a new sealed variant with
  its own mechanics — not a non-developer dropping in JSON. The one scenario the untyped engine was
  built to serve does not occur.
- **No compile-time safety.** Field ids are strings everywhere (`data["strength"] as? Number`),
  in both the Kotlin logic and the TypeScript renderer. Typos and shape drift surface only at
  runtime, if at all.
- **Two formula engines that must stay in lockstep.** `SheetCompute`/`FormulaEvaluator` (Kotlin) and
  `derive.ts` (TypeScript) reimplement the same grammar — `sum()`, `abilityMod()` indirection,
  per-row table scope (plan.md Phase 3C) — and any divergence silently breaks client/server parity.
- **Storage cannot be validated.** A malformed sheet is just another shape of `Map<String, Any?>`;
  nothing at the persistence boundary can reject it.
- **Two sources of truth for the sheet shape** — `definition.json` and the Kotlin logic that reads
  it by string — that must be hand-kept in agreement.

The maintainer has decided the prototype-era flexibility is no longer worth the type cost, is not
attached to `SheetData`, and is comfortable moving sheet structure, lookups, and validators from
`definition.json` into typed Kotlin (and typed TypeScript on the web). Accepted goals: (1) safer
rule-set logic, (2) validated storage, (3) typed frontend components.

## Decision

Replace the opaque `SheetData` map as the **domain model** with a **sealed hierarchy of typed,
per-rule-set sheets**, code-first, across backend and frontend.

1. **Sealed sheet, split base-inputs vs. resolved.** A `sealed interface` (the file the maintainer
   started, `characters/data/CharacterData.kt`) has one variant per rule set. Each variant is split:
   a **base-inputs** type (exactly the values an editor enters — the *only* thing persisted) and a
   **resolved** view (base + derived) returned by the API. Derived values become **computed
   properties** (`val strengthMod get() = (base.strength - 10) / 2`) on the resolved type.
2. **Compute-on-read survives, as plain Kotlin.** D8's principle — store base inputs only, never
   persist derived — is *preserved*, but the mechanism changes: computed properties instead of a
   formula engine. This **retires** `FormulaEvaluator`, `SheetCompute`, the definition's
   `derivedFrom` strings, the write-time strip-derived step, and the web `derive.ts`. Derived values
   cannot drift or be stored, by construction — a stronger guarantee than D8's, with less code.
3. **Shared code stays generic via projection interfaces, not the union.** The genuinely
   cross-cutting needs are expressed as small interfaces the sheets implement (e.g.
   `interface Combatant { val hpCurrent: Int; val hpMax: Int; val initiative: Int }`, a roster
   projection). Combat/SSE/roster depend on those; only rule-set-specific code does an exhaustive
   `when` over the sealed type — where the compiler then enumerates every site to touch.
4. **Polymorphic persistence via Spring Data's own type hint (corrected by the spike, below).**
   Storage is Spring Data's `MappingMongoConverter`, **not** Jackson — so persistence polymorphism uses
   its `_class` type-hint key, and `@TypeAlias("dnd35")` on each variant makes the stored **value** a
   stable string (`"dnd35"`) rather than the Kotlin class FQN, decoupling storage from class names.
   `ruleSetId` stays a top-level `Character` field (already there/indexed). Jackson's
   `@JsonTypeInfo(use = NAME, property = "ruleSetId")` + `@JsonSubTypes` governs the **wire** only.
   **Validated storage (goal 2) comes at the deserialization boundary**: an unknown `_class`/malformed
   sub-document fails to map rather than flowing through as a loose map. (A generated Mongo JSON Schema
   validator is optional later hardening, not required by this ADR.)
5. **Wire contract becomes a discriminated union.** `contracts/openapi.yaml` models `data` as
   `oneOf` the per-rule-set schemas with a `ruleSetId` discriminator; the web **codegens** its TS
   types from that schema, so backend and frontend cannot drift.
6. **Typed frontend.** The web mirrors the union as a discriminated-union type and renders
   rule-set-specific **typed components**, replacing the generic definition-driven `SheetRenderer`
   for rule-set-specific layout. Derived values are displayed from the server-resolved sheet (or a
   typed client mirror where instant feedback warrants it).

### What survives, what retires

| Survives | Retires / changes |
|----------|-------------------|
| Compute-on-read *principle* (D8): base inputs stored, derived never persisted | The formula engine that implemented it — `FormulaEvaluator`, `SheetCompute`, `derivedFrom`, `derive.ts`, strip-derived-on-write |
| `ruleSetId` as the rule-set discriminator | `SheetData = Map<String, Any?>` as the domain/wire model |
| Rule set as a strategy resolved by id; unknown id rejected | `SheetDefinition`/`definition.json` as the structure source of truth (structure moves to typed code) |
| SRD catalog *data* (spells, skill lists) stays as bundled data | It becomes typed/loaded into typed structures, not free-form definition JSON |
| Soft-validation posture (`validate` returns warnings, never blocks — FR-005) | `validate` now reads a typed sheet instead of a map |

## Alternatives Considered

| Option | Pros | Cons | Why chosen / rejected |
|--------|------|------|-----------------------|
| **Typed sealed sheets, code-first (chosen)** | Compile-time safety in logic + UI; one compute path (properties); validated storage for free; wire+client codegen from one schema; derived can't drift or be stored | More code per rule set; adding a rule set now touches Kotlin + exhaustive `when`s (not zero-code); homebrew-via-JSON off the table | **Chosen** — matches the real product (DnD35 + DS are both code); the compiler makes the extra touch-points safe and explicit |
| **Keep `SheetData`, add a typed *view* parsed inside each rule set only** (the earlier recommendation) | Zero shared-engine change; preserves FR-023/SC-009 exactly; safety where string-typos actually bite | Storage/wire stay untyped; two formula engines remain; no validated storage; two sources of truth persist | Rejected — delivers goal 1 only; maintainer wants 2 and 3 too and is not attached to `SheetData` |
| **Status quo (fully data-driven map + formulas)** | Add a rule set with no engine code; maximal flexibility | No type safety anywhere; dual formula engines; unvalidated storage; flexibility unused in practice | Rejected — the flexibility serves a scenario that does not occur |
| **JSON Schema storage validation over the map** | Validated storage without a rewrite | Still no *compile-time* safety in code or UI; schema is a third source of truth | Rejected as the primary move; may return as optional hardening (point 4) |

## Consequences

**Positive**
- Field-name typos and shape mistakes become compile errors in Kotlin *and* TypeScript.
- One compute path (computed properties); `FormulaEvaluator`/`SheetCompute`/`derive.ts` and the
  strip-derived step are deleted — net less code in the compute path, and no client/server parity
  engine to keep in lockstep.
- Derived values are unstoreable by construction — a stronger D8 guarantee.
- Malformed documents are rejected at the deserialization boundary (validated storage).
- Wire + web types are codegen'd from one schema; a backend field change breaks the web build.

**Negative / what we give up**
- **FR-023/SC-009 is softened**: adding a rule set is no longer "definition + logic, no engine
  change" — it is a new sealed variant, its compute, its components, and filling in exhaustive
  `when`s. The compiler points at every site, but it is code, not data.
- **Homebrew/non-developer rule sets via JSON** are no longer possible. (Not a v1 goal.)
- More code overall, especially per rule set and in the frontend (accepted by the maintainer).
- OpenAPI `data` stops being a free-form object.
- US1 (already built on `SheetData`) must be re-typed; the Phase 3C/T112–T114 sheet content
  (tables, spells catalog) must be re-expressed as typed structures.

**Risks**
- ✅ **Persistence polymorphism through `MongoTemplate`** — *resolved by the spike (below)*: sealed
  fields round-trip cleanly; discriminator handled by `_class` + `@TypeAlias`; no custom converter.
- **Wire-side polymorphism (Jackson 3)** — not spiked; standard `@JsonTypeInfo`/`@JsonSubTypes` +
  `jackson-module-kotlin`. Low risk, but confirm the request/response `oneOf` binds when built.
- **Table/repeating-group fields** (skills, spells, gear — Phase 3C) are the hardest to type well;
  model rows as typed data classes and confirm ergonomics in the vertical slice.
- **Scope creep**: this touches spec, plan, data-model, contracts, tasks, and both codebases. Land
  as a bounded DnD35 vertical slice first (leaving `DarkSoulsSheet` a stub variant so `when`s
  compile), not a big-bang rewrite.

## Spike outcome (2026-07-24)

Throwaway integration test `api/.../spike/TypedSheetPersistenceSpike.kt` (real MongoDB Testcontainer,
`MongoTemplate`), 3/3 green. Findings:

- **A — round-trip works.** A `sealed interface` sheet field on a `@Document` saves and reloads as the
  correct concrete subtype with **no custom converter**; derived computed properties recompute on the
  reloaded object. The core mechanism is sound.
- **B — discriminator.** Spring Data writes its type hint under **`_class`** (its own converter, not
  Jackson). `@TypeAlias("dnd35")` makes the stored value the stable alias, not the FQN — stored doc was
  `{"strength": 12, "level": 1, "_class": "dnd35"}`. **Design correction:** use `_class` + `@TypeAlias`
  for storage; keep `ruleSetId` as a top-level `Character` field; `@JsonTypeInfo(property="ruleSetId")`
  is wire-only. (Making `ruleSetId` itself the storage key would need a custom `MongoTypeMapper` — not
  worth it.)
- **C — derived never persists.** Stored keys were `[strength, level, _class]` only. A getter-only
  computed `val` (no backing field) is **not** written. **Design simplification:** the base-inputs vs.
  resolved *split is optional*, not required to keep derived out of storage — a single typed class with
  base-input constructor props + derived computed vals already stores inputs only, and the write-time
  strip step (`stripDerived`) can be **deleted**. Keep the split only where a resolved DTO across the
  wire or avoiding recompute is wanted; default to the single-class form.

Delete the spike file before the real work lands (or once the first real typed round-trip test exists).

## Propagation (follow-ups, tracked when we cut tasks)

Not done in this ADR step — listed so nothing is lost when we move to tasks/tests:

- `research.md` **D3/D8**: supersede pointers added to this ADR (done in this step).
- `spec.md` **FR-023 / SC-009**: soften the "no shared-engine change to add a rule set" wording to
  the compiler-enumerated reality. *Maintainer spec edit.*
- `plan.md`: amendment block + Complexity Tracking updates (done in this step); Phase 3C/T112–T114
  sections re-cast to typed structures when tasks are cut.
- `data-model.md`: `data` sub-document described as a typed, discriminated base-inputs shape.
- `contracts/openapi.yaml`: `data` → `oneOf` + `ruleSetId` discriminator; web type codegen.
- `tasks.md` + tests: new/edited tasks and test expectations (the explicit next step after this ADR).
- Delete/retire once migrated: `FormulaEvaluator`, `SheetCompute`, `derive.ts`, `definition.json`
  (structure), the strip-derived logic in `CharacterService`.
- The maintainer's started files in `characters/data/` need a `package` declaration, the Dark Souls
  file needs to declare its own type (currently a duplicate of the DnD35 class), and the base-inputs
  vs. resolved split applied.

## Review Trigger

Revisit if any of these change:
- A requirement for **non-developer / homebrew rule sets** (data-authored, no code) re-emerges —
  that would argue for restoring a data-driven layer alongside the typed one.
- The number of rule sets grows large enough that per-variant code and exhaustive `when`s become a
  maintenance drag a data-driven engine would avoid.
- The Jackson-polymorphism-through-MongoTemplate approach proves unworkable on the spike — reconsider
  the persistence binding (e.g. explicit per-rule-set converters) before broad rollout.
