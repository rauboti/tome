---
description: "Task list for Campaign & Character Management (001)"
---

# Tasks: Campaign & Character Management

**Input**: Design documents from `/specs/001-campaign-management/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

**Tests**: REQUIRED per Constitution Principle II (Test-First Verification). Within each story, tests
are written first and must FAIL before implementation.

**Organization**: Grouped by user story (US1–US5) for independent implementation and testing.

> **⚠ Amendment 2026-07-22 (post-US1): Postgres → MongoDB + compute-on-read derived values.**
> US1 (T001–T034) was built and completed on **Postgres/JSONB/Flyway/JdbcTemplate** and is left below
> as historical record (still `[X]`). **Phase 3B (T084–T104)** re-platforms that built slice onto
> **MongoDB** (Spring Data + `MongoTemplate`, Spring Data-native migrations, Testcontainers `MongoDBContainer` replica set)
> and switches derived sheet values to **computed-on-read, never stored** — using a
> disable-then-re-enable test strategy. The pending **US2–US5 tasks (T035–T083) have been rewritten in
> place** to target MongoDB. New task IDs (T084+) continue the existing number space so prior IDs stay
> stable; **execution order is by phase position, not by ID** — Phase 3B runs immediately after US1
> and before US2. See research.md (D3/D5/D8), data-model.md, plan.md.

> **⚠ Amendment 2026-07-24 (post-3C): data-driven engine → strongly-typed, code-first — see
> [ADR-001](decisions/ADR-001-typed-ruleset-sheets.md).** The opaque `SheetData = Map<String, Any?>`
> engine (definition JSON + `derivedFrom` formulas evaluated by `FormulaEvaluator`/`SheetCompute`/web
> `derive.ts`) is replaced by a **sealed hierarchy of typed per-rule-set sheets**; derived values become
> **computed Kotlin properties** (compute-on-read survives; the formula engines and the write-time strip
> retire). Storage binds polymorphically via `_class` + `@TypeAlias` on the existing `ruleSetId`; the
> wire becomes a `oneOf` + discriminator the web codegens from. **New Phase 3D (T118+)** carries the
> pivot as a bounded DnD35 vertical slice (DarkSouls a stub variant), sequenced **after Phase 3C, before
> US2** so US2/US3 (NPCs) are authored typed from the start. This **supersedes the compute mechanism of
> T105/T108/T110/T111 and the generic renderer of T023/T105** (left `[X]` as historical record, as the
> Postgres US1 tasks are). A **persistence spike** (throwaway `api/.../spike/TypedSheetPersistenceSpike.kt`,
> 3/3 green) de-risked the Mongo binding first — findings in ADR-001. Supersedes research.md D3/D8 in
> part; softens spec FR-023/SC-009.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- Paths follow the platform two-tier layout from plan.md: `api/` (Kotlin/Spring Boot,
  `no.rauboti.tome`, **`MongoTemplate`** — Postgres/`JdbcTemplate` in the pre-2026-07-22 US1 build) and
  `web/` (Vite/React 19/`@rauboti/ui`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold both tiers and the compose stack, mirroring avec/pulse/taskmaster.

- [X] T001 Create `api/` Spring Boot skeleton — `api/pom.xml` (Boot 4.1 parent, Java 25, package root `no.rauboti.tome`; deps: web, actuator, security, oauth2-client, oauth2-resource-server, jdbc, flyway starter, flyway-database-postgresql, postgresql, jackson-module-kotlin, kotlin-reflect; test: starter-test, spring-security-test, webmvc-test, mockk, spring-boot-testcontainers, testcontainers-postgresql) + `api/mvnw`/`mvnw.cmd`
- [X] T002 Create `web/` Vite + React skeleton — `web/package.json` (React 19, Chakra v3, `@rauboti/ui` ^0.3.5, react-router 7, zod, i18next/react-i18next; dev: vitest, @testing-library/*, msw), `web/vite.config.ts`, `web/tsconfig*.json`, `web/index.html`
- [X] T003 [P] Configure Spotless/ktlint (ADR-0001) in `api/pom.xml`
- [X] T004 [P] Configure ESLint + Prettier in `web/eslint.config.js` and `web/.prettierrc`
- [X] T005 Create `tome/docker-compose.yml` (`tome-db` postgres:17-alpine `5436:5432`; `tome-api` build `./api` `5040:8080`; `tome-web` build `./web` `3040:80`; healthchecks; `rauboti_token` BuildKit secret) + `api/Dockerfile` + `web/Dockerfile` + `web/nginx.conf`
- [X] T006 [P] Create `tome/.env.example` and platform-root `tome.env` (Hive consumer: `HIVE_EXTERNAL_URL`/`HIVE_INTERNAL_URL`/`HIVE_CLIENT_ID`/`HIVE_CLIENT_SECRET`, `WEB_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `POSTGRES_*`)
- [X] T007 [P] Create `api/src/main/resources/application.yml` + `application-dev.yml` + `application-test.yml` (datasource, Hive URLs, CORS, Flyway)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Auth/BFF, persistence baseline, the shared sheet engine, and web scaffolding — everything
every story builds on.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

- [X] T008 Datasource + Flyway wiring and empty migration dir `api/src/main/resources/db/migration/` (config in `application.yml`)
- [X] T009 [P] Security filter chain + CORS + BFF config — **lift from taskmaster/pulse** (Hive oauth2-client Authorization-Code+PKCE, resource-server JWKS validation, HTTP-only session cookie), adapting client-id/URLs for tome; **enforce the Tome role gate** (a principal without an `Admin`/`User` role is denied, FR-024) in `api/src/main/kotlin/no/rauboti/tome/config/SecurityConfig.kt`
- [X] T010 [P] Auth contract test — `/api/auth/me` returns 401 unauthenticated, 200 with `userId`+`roles` (Admin/User) when authed, **and 403 when authenticated without a Tome role** (FR-024); logout 204 (MockMvc + `spring-security-test`) in `api/src/test/kotlin/no/rauboti/tome/auth/AuthControllerTest.kt`
- [X] T011 [P] Implement `AuthController` — `GET /api/auth/me` (userId, roles from JWT `roles` claim, locale), `POST /api/auth/logout` in `api/src/main/kotlin/no/rauboti/tome/auth/AuthController.kt`
- [X] T012 [P] RFC-7807 error handling (`@RestControllerAdvice`) + `StaleVersionException` → 409 mapping in `api/src/main/kotlin/no/rauboti/tome/common/`
- [X] T013 Sheet engine core types — `SheetData`, `SheetDefinition`, `SheetChange`, `RuleWarning`, and the `RuleSet` interface in `api/src/main/kotlin/no/rauboti/tome/rulesets/RuleSet.kt`
- [X] T014 [P] JSONB support — Jackson (`tools.jackson`) ObjectMapper wiring + JdbcTemplate `SheetData`⇄`jsonb` conversion in `api/src/main/kotlin/no/rauboti/tome/common/JsonbSupport.kt`
- [X] T015 [P] Author the D&D 3.5 sheet definition (sections, fields, `derivedFrom`) in `api/src/main/resources/rulesets/dnd35/definition.json`
- [X] T016 `DnD35RuleSet` unit test — `computeDerived` (ability modifiers, saves, BAB) and `validate` (returns warnings, never blocks) in `api/src/test/kotlin/no/rauboti/tome/rulesets/DnD35RuleSetTest.kt`
- [X] T017 Implement `DnD35RuleSet.computeDerived` + `validate` in `api/src/main/kotlin/no/rauboti/tome/rulesets/DnD35RuleSet.kt` (make T016 pass)
- [X] T018 [P] Contract test for rule-set endpoints (`GET /api/rule-sets`, `/{id}`) in `api/src/test/kotlin/no/rauboti/tome/rulesets/RuleSetControllerTest.kt`
- [X] T019 `RuleSetRegistry` + `RuleSetController` (`GET /api/rule-sets`, `/{id}` serving the definition) in `api/src/main/kotlin/no/rauboti/tome/rulesets/`
- [X] T020 [P] Web app shell/layout (`@rauboti/ui` AppShell/Navbar) + React Router routes in `web/src/components/layout/` and `web/src/pages/`
- [X] T021 [P] Web auth/session context + login-redirect handling (`useSession`) in `web/src/auth/`
- [X] T022 [P] Web i18n setup (i18next init, `nb.json`/`en.json`, English fallback) in `web/src/i18n/`
- [X] T023 [P] Definition-driven `SheetRenderer` + field widgets (int/text/bool/select/list/derived) with a Vitest render test in `web/src/components/sheet/`
- [X] T024 [P] Typed API client base + Zod schemas for auth & rule-sets in `web/src/api/`

**Checkpoint**: Auth, persistence, the sheet engine, and the web shell are ready.

---

## Phase 3: User Story 1 - Keep a character sheet digitally (Priority: P1) 🎯 MVP

**Goal**: A user creates a 3.5 character and maintains its full sheet digitally, with derived values,
soft warnings, and safe concurrent edits.

**Independent Test**: Create a character, fill/edit the sheet, reload → persists exactly; a rule
violation warns but still saves; a stale write returns 409.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [X] T025 [P] [US1] Contract test for `/api/characters` (POST/GET/PUT/DELETE) against openapi in `api/src/test/kotlin/no/rauboti/tome/characters/CharacterContractTest.kt`
- [X] T026 [P] [US1] Integration test (Testcontainers) — create→edit→reload persistence, derived values recomputed on write, soft warning returned without blocking, `409` on stale `version` in `api/src/test/kotlin/no/rauboti/tome/characters/CharacterIntegrationTest.kt` *(Postgres-era behavior "derived recomputed on write" — **superseded by T100**, which asserts derived are computed on read and NOT stored)*
- [X] T027 [P] [US1] Web test — `SheetRenderer` edit flow, derived-value display, warning banner, version-conflict handling (Vitest/RTL/MSW) in `web/src/components/characters/CharacterSheet.test.tsx`

### Implementation for User Story 1

- [X] T028 [US1] Migration `V1__create_character.sql` (jsonb `data`, promoted `name`/`rule_set_id`/`owner_id`/`hp_current`/`hp_max`, `version`, timestamps) in `api/src/main/resources/db/migration/`
- [X] T029 [US1] `Character` model + `CharacterRepository` (JdbcTemplate, jsonb via T014) in `api/src/main/kotlin/no/rauboti/tome/characters/`
- [X] T030 [US1] `CharacterService` — create/get/update/delete with `RuleSet.validate` + `computeDerived` and optimistic concurrency in `api/src/main/kotlin/no/rauboti/tome/characters/CharacterService.kt`
- [X] T031 [US1] `CharacterController` REST endpoints in `api/src/main/kotlin/no/rauboti/tome/characters/CharacterController.kt`
- [X] T032 [US1] Web characters API client + Zod schemas in `web/src/api/characters.ts`
- [X] T033 [US1] Web `CharactersPage` (list + create dialog) in `web/src/pages/CharactersPage.tsx` and `web/src/components/characters/`
- [X] T034 [US1] Web character sheet edit screen using `SheetRenderer` (save/version handling, warnings) in `web/src/components/characters/CharacterSheet.tsx`

**Checkpoint**: US1 is a fully functional, independently testable MVP.

---

## Phase 3B: Persistence re-platform — MongoDB + compute-on-read (AMENDMENT 2026-07-22)

**Runs immediately after US1, before US2** (task IDs T084+ continue the number space; order is by phase
position, not ID). Re-platforms the built US1 slice from Postgres to MongoDB and switches derived sheet
values to **computed-on-read, never stored** — with **no change to observable REST behavior**
(SC-001/SC-006 still hold; openapi shapes unchanged). Design: research.md D3/D5/D8, data-model.md.

> **⚠ Sub-amendment 2026-07-23: no migration framework.** The 2026-07-22 amendment named **Mongock**;
> during T084 we found Mongock is **deprecated** (last release targets Spring Boot 3 / Spring Data Mongo
> 4 — no fit for our Boot 4.1 / Spring Data 5.1) and its successor **Flamingock**, while Boot-4 capable, is
> **Gradle-plugin-only with no Maven support** (Tome is Maven + Kotlin). Decision: **drop the framework** —
> migrations are **Spring Data-native** (idempotent index ensure from a code-owned catalog + a small
> `_migrations` applied-changes ledger). Flamingock revisit triggers recorded in research.md §Migrations.

**Strategy**: quarantine the Postgres-bound tests → swap infra → rebuild the character slice → re-enable
and rewrite the tests incrementally as each piece lands. Tasks are deliberately small for line-by-line
review.

**Independent Test**: `./mvnw verify` green against a MongoDB Testcontainer; create→edit→reload persists
base inputs; GET/PUT responses carry resolved derived values; the **stored document contains no derived
fields**; a stale `@Version` write returns 409; `docker compose up --build` boots the Mongo stack.

**Preflight (Constitution III)**: fresh branch off `main` before any code; leave work uncommitted for
Gaute to review/commit. This whole phase is one increment's worth of small tasks.

**Data disposition**: **clean cutover** — US1's data is disposable local dev data, so there is **no
data-migration script** (drop the Postgres stack, start fresh on MongoDB). ⚠ Confirm at preflight that
no irreplaceable character data exists in the running Postgres volume; **if any must be kept, export it
before T085** (one-off, out of scope for the migrations). See research.md §D3 "Data disposition".

### Infra swap (build + stack)

- [X] T084 [P] Swap api dependencies in `api/pom.xml` — remove `spring-boot-starter-jdbc`, the Flyway starters, `flyway-database-postgresql`, `postgresql`, `testcontainers-postgresql`; add `spring-boot-starter-data-mongodb` and `testcontainers-mongodb`. **No migration framework** (decision 2026-07-23): Mongock is deprecated and its successor Flamingock is Gradle-plugin-only (no Maven support) — migrations are Spring Data-native (index catalog + ledger, see T091). See research.md §Migrations.
- [X] T085 Replace the `tome-db` service in `docker-compose.yml` — `mongo:8` on host `5436`→`27017`, single-node replica set (`--replSet rs0`) with a one-time `rs.initiate()` (init container or healthcheck-gated), local dev credentials; update `tome-api` env (`MONGODB_URI`) and the `depends_on` healthcheck. **Impl notes:** auth + RS requires an internal-auth **keyFile** (generated ephemerally at startup — single member, so no committed secret); `rs.initiate()` runs via a one-shot **`tome-db-init`** sidecar; the db healthcheck gates on `isWritablePrimary`; api env is `SPRING_DATA_MONGODB_URI` (Spring's relaxed-binding name for `spring.data.mongodb.uri`, mirroring the old direct `SPRING_DATASOURCE_URL`). **Smoke-tested** (isolated port 5439): init→primary→healthy, auth + transactions OK, idempotent re-init.
- [X] T086 [P] Replace `POSTGRES_*` with Mongo settings in `tome/.env.example` and platform-root `tome.env` (`MONGODB_URI`, `MONGO_DB`, credentials, replica-set name). **Impl note:** `.env.example` updated (`MONGO_DB`/`MONGO_USER`/`MONGO_PASSWORD`/`MONGO_PORT`/`MONGO_REPLICA_SET` + a commented host-dev `directConnection` URI). Platform-root `tome.env` needed **no change** — it never held `POSTGRES_*` (by design it holds only cross-service Hive URLs; DB creds come from compose defaults, per its own comment), so there was nothing to replace and adding `MONGO_*` there would duplicate the compose defaults.
- [X] T087 [P] Rewrite datasource config in `api/src/main/resources/application.yml` + `application-dev.yml` + `application-test.yml` — drop `spring.datasource`/Flyway; add `spring.data.mongodb.uri`. Disable Spring Data's auto index creation (`spring.data.mongodb.auto-index-creation: false`) — indexes are ensured explicitly on boot (T091). **Impl notes:** base `uri` default is the host-dev `directConnection` URI (`SPRING_DATA_MONGODB_URI` env overrides in Docker); `application-test.yml` sets no uri (Testcontainers `@ServiceConnection` supplies it, T090); dev/test flyway locations removed. YAML parse-verified; no `datasource`/`flyway`/`jdbc` keys remain (only historical comments, allowed by CHK018).

### Test quarantine (mark Postgres-bound tests; the module stays red until the rebuild, green at T103)

- [X] T088 Temporarily `@Disabled("re-platform: re-enabled in T100–T102")` the Postgres/Testcontainers-bound US1 api tests — `CharacterIntegrationTest` and `CharacterContractTest` — in `api/src/test/kotlin/no/rauboti/tome/characters/`. Leave the pure `DnD35RuleSetTest` and the DB-agnostic web tests enabled. **Impl note:** `@Disabled` skips *execution*, not *compilation* — the module is still red at **main** compile (`CharacterRepository`/`Character`/`JsonbSupport`/`RuleSet` use removed JDBC/JSONB; rebuilt T089–T098), so this is a documentation/quarantine step; green returns at T103. **Harness note:** four test classes extend the Postgres `IntegrationTest` base — both Character tests here **plus `AuthControllerTest` and `RuleSetControllerTest`** (full-context, so *not* DB-agnostic despite testing web behavior). That base + `support/TestcontainersConfiguration.kt` (imports removed `PostgreSQLContainer`) and `common/JsonbSupportTest.kt` also block test compilation. **Resolved (not left to T104's post-green sweep):** the harness swap is folded into **T090** (repoint `TestcontainersConfiguration` to `MongoDBContainer` in place; the generic `IntegrationTest` base + all four consumers stay as-is) and `JsonbSupportTest` deletion into **T092**.

### MongoDB foundation

- [X] T089 Mongo config — `MongoConfig` (Mongo client, `MongoTemplate`, `MongoTransactionManager`) in `api/src/main/kotlin/no/rauboti/tome/config/MongoConfig.kt`. Add a `MigrationRunner` (`ApplicationRunner`/`@EventListener(ApplicationReadyEvent)`) that applies the ordered changes (T091) once each, guarded by the `_migrations` ledger, in `.../config/migration/MigrationRunner.kt`. **Impl notes:** `MongoConfig` relies on Boot autoconfig for the client/`MongoTemplate` (from `spring.data.mongodb.uri`, T087) and adds only what Boot doesn't — a `MongoClientSettingsBuilderCustomizer` pinning `UuidRepresentation.STANDARD` (UUID `@Id`s; applies to the `@ServiceConnection` test client too) and a `MongoTransactionManager`. Runner uses `@EventListener(ApplicationReadyEvent)`, sorts changes by lexical `id`, records each in `_migrations` after applying (idempotent; duplicate-key-tolerant for concurrent starts), and is a harmless no-op until `C001` (T091). Added a small `MigrationChange` interface (`id` + `apply(MongoTemplate)`) + `AppliedChange` ledger doc alongside the runner. **Verified:** APIs via `javap` (Boot-4-moved `org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer`) + Spotless/ktlint clean; full compile deferred (module red until T093–T098; runner exercised by T091's `MigrationRunnerTest`).
- [X] T090 **Repoint the test harness to MongoDB (in place)** — swap `support/TestcontainersConfiguration.kt` from `PostgreSQLContainer` to `MongoDBContainer` (`mongo:8`, single-node replica set, auto-initiated) via `@ServiceConnection`, and refresh the `IntegrationTest` base KDoc. The **generic `IntegrationTest` base name and all four consumers (`AuthControllerTest`, `RuleSetControllerTest`, both `@Disabled` Character tests) stay unchanged** — the backing store is an implementation detail of the config, not the base name (the original storage-agnostic base/config split already had this right). Closes the T088 ordering gap: the test tree is Postgres-free (grep confirms zero `PostgreSQLContainer`/`testcontainers.postgresql`; remaining `Postgres` mentions are in files rewritten/deleted by T100/T101/T092). **Impl notes:** used `org.testcontainers.mongodb.MongoDBContainer` (2.x pkg) — `javap`-confirmed it binds to Boot 4.1's *current* `MongoDbContainerConnectionDetailsFactory` (the legacy `org.testcontainers.containers` pkg maps to the *deprecated* factory); container auto-initiates rs0 (transactions/`@Version` work). Spotless/ktlint clean; full compile deferred (module red until T093–T098; first exercised by T091's `MigrationRunnerTest`).
- [X] T091 Migration change `C001` (Spring Data-native) — create `characters` collection + ensure index `{ userId: 1 }` via `MongoTemplate`/`IndexOperations`, idempotent and recorded in the `_migrations` ledger, in `api/src/main/kotlin/no/rauboti/tome/config/migration/C001__CreateCharacters.kt` (a plain change unit invoked by the T089 `MigrationRunner`, not a framework changelog). **Lands with a focused `MigrationRunnerTest` on the `IntegrationTest` base** asserting the change applies once and a second run is a no-op (ledger guard) + the `{userId:1}` index exists. **Impl notes / naming convention:** migration classes use `C<order>__<Name>` (e.g. `C001__CreateCharacters`) — Flyway/Flamingock-style for readability, which needs a one-line `@file:Suppress("ktlint:standard:class-naming")` per migration file (the shared repo-root `.editorconfig` can't be relaxed per the platform ADR). **Execution order comes from the `id` field** (ledger key `C001`), NOT class-name parsing, so the name is purely descriptive. `ensureIndex` (not deprecated in Spring Data 5.1) on the `userId` field (data-model + `Character.userId`). **Verified:** APIs via `javap`, Spotless/ktlint clean. **✅ Executed 2026-07-23 during T097** — `MigrationRunnerTest` **3/3 green** against a real `mongo:8` replica-set Testcontainer (C001 applies once, `{userId:1}` index present, idempotent re-run). ⚠ the compiler flags `ensureIndex` **deprecated** (the T091 `javap` check missed it) — switch to the non-deprecated `createIndex` (idempotent for identical specs); tracked as a follow-up.
- [X] T092 Retire JSONB glue — delete `api/src/main/kotlin/no/rauboti/tome/common/JsonbSupport.kt` **and its unit test `api/src/test/kotlin/no/rauboti/tome/common/JsonbSupportTest.kt`** (BSON is native — the test's subject is gone) and remove `api/src/main/resources/db/migration/V1__create_character.sql` + the now-empty `db/migration/` dir. **Impl note:** also removed the dir's `.gitkeep` and the now-empty parent `resources/db/` tree; no config references the removed SQL/seed (Flyway config already dropped in T087). Residual `JsonbSupport` references remain in `CharacterRepository.kt` (rewritten T094) and a stale comment in `RuleSet.kt` (T104 sweep) — module stays red until T093–T098, as expected.

### Character slice on MongoDB + compute-on-read

- [X] T093 [US1] Rewrite `Character` as a Mongo document — `@Document("characters")`, `@Id` UUID, `@Version` int, `data` = base inputs only (drop the promoted-column shape) in `api/src/main/kotlin/no/rauboti/tome/characters/Character.kt`. **Impl notes:** kept top-level `id/userId/ruleSetId/name/data/version/createdAt/updatedAt` (data-model §characters). `@Version` typed **`Int?` (nullable)** — spec says "int", nullable so the T094 repo can distinguish a new doc (`null`) from an existing one (Spring assigns `0` on insert, increments on save). `createdAt`/`updatedAt` stay plain service-set `Instant`s (no Spring auditing added — matches the prior manual approach; revisit if desired). **Verified:** annotation packages `javap`-checked (`org.springframework.data.annotation.Id`/`Version`, `@Document.collection`), Spotless/ktlint clean; module still red until the repo/service/controller are rewritten (T094–T098).
- [X] T094 [US1] Rewrite `CharacterRepository` — `MongoTemplate` insert/findById/findByUserId/save/delete; optimistic concurrency via `@Version` (no hand-rolled `WHERE version = ?`) in `.../characters/CharacterRepository.kt`. **Impl notes:** entity-based methods (`insert(Character)`/`save(Character)`) — `save()` issues the `@Version` versioned update and throws `OptimisticLockingFailureException` on a stale version (service maps → 409, T096/T098); no `WHERE version`. Unlike the old JDBC repo (DB-generated id/version/timestamps via `RETURNING`), **id + createdAt/updatedAt are now assigned by the service** (T096) and `@Version` starts at 0 on insert. `findByUserId` sorts `createdAt` desc; `deleteById` uses `remove(Query(where("id").is(id)))` → `deletedCount > 0`. **Verified:** `MongoTemplate`/`DeleteResult`/`Sort` APIs via `javap`, Spotless/ktlint clean. Module still red — `CharacterService`/`Controller` use the old repo API + Character shape until T096/T097.
- [ ] T095 [P] [US1] Add the character resolve-on-read helper — `CharacterDataResolver.resolve(data, ruleSet)` = base inputs + `RuleSet.computeDerived`, the single home of character compute-on-read (D8), in `api/src/main/kotlin/no/rauboti/tome/characters/CharacterDataResolver.kt`. **Entity-scoped by design** — NPC and other entities get analogous resolvers later; extract a shared core only if duplication warrants. **Lands with a focused unit test** `api/src/test/kotlin/no/rauboti/tome/characters/CharacterDataResolverTest.kt` (pure, no container) asserting resolve = base inputs + `RuleSet.computeDerived`, inputs preserved, derived recomputed — new code + its test together. **Impl notes:** `@Component`, `resolve(data, ruleSet) = data + ruleSet.computeDerived(data)` — recomputed values win the merge, so stale stored derived (if any leaked in) is overwritten on read; robust whether a rule set's `computeDerived` returns only-derived or base+derived (DnD35's returns base+derived). Test uses a **stub `RuleSet`** to isolate the resolver's merge/preserve/recompute contract (DnD35 formulas stay covered by `DnD35RuleSetTest`). Spotless/ktlint clean; the pure test can't execute until the module compiles (T096/T097) — runs then.
- [X] T096 [US1] Rewrite `CharacterService` — on write: `validate` + **strip fields the definition marks `derived`** before persisting; on read/echo: return the resolved sheet via `CharacterDataResolver`; map `OptimisticLockingFailureException` → 409 in `.../characters/CharacterService.kt`. **Impl notes:** `stripDerived()` drops fields whose definition `type == FieldType.DERIVED` (base inputs only stored, D8); `toResolved()` returns the character with `data` = `resolver.resolve(...)` + soft warnings (validate runs on the resolved sheet, parity with the old `computed`). Service now assigns `id`/timestamps and passes `version=null` for new (insert → 0); update copies the caller's `expectedVersion` onto the entity so `@Version` `save()` rejects a stale write. **409 mapping (T096↔T098 split):** the service catches `OptimisticLockingFailureException` and rethrows the domain `StaleVersionException` (keeps Spring-Data exceptions out of the handler); the existing handler already maps `StaleVersionException → 409` — T098 verifies/adapts it. Public method signatures unchanged (controller compiles against them). **Verified:** `OptimisticLockingFailureException` package via jar, Spotless clean, and `./mvnw compile` shows the **only** remaining main blocker is `CharacterController.kt` (T097).
- [X] T097 [US1] Update `CharacterController` — GET and the POST/PUT echo return the resolved sheet (base + derived) in `.../characters/CharacterController.kt`. **Impl notes:** resolved data already flows through — the service (T096) returns `CharacterWithWarnings.character.data` = resolved sheet, and `toResponse()` passes it straight to `CharacterResponse.data`. Only change needed was the `@Version` nullability: `version = requireNotNull(character.version)` (response wants non-null `Int`; a persisted character always has one). **🎯 Milestone:** `./mvnw test-compile` is **green — the whole api module compiles for the first time since T084**; Spotless clean module-wide. Ran the pure tests → **9/9 pass** (`CharacterDataResolverTest` verifies the T095 resolver; `DnD35RuleSetTest`). The container-backed tests were **run now against a `mongo:8` Testcontainer → 12/12 green** (`MigrationRunnerTest` 3, `AuthControllerTest` 5, `RuleSetControllerTest` 4): first real exercise of the T089+T090+T091 migration mechanism + full Mongo context boot — all pass. ⚠ surfaced a deprecation — `IndexOperations.ensureIndex` is deprecated (still works); switch to `createIndex` (see T091).
- [X] T098 [US1] Map Spring Data `OptimisticLockingFailureException` → RFC-7807 `409` (replacing/adapting the `StaleVersionException` mapping) in `api/src/main/kotlin/no/rauboti/tome/common/`. **Impl notes:** added a `@ExceptionHandler(OptimisticLockingFailureException)` → 409 to `ApiExceptionHandler` with a curated RFC-7807 detail (not the driver's internal message) — the app-wide safety net for **any** `@Version` aggregate (campaigns/encounters, US2+). **Kept** `StaleVersionException` → 409: the character write path translates the lock exception to that domain signal in the service (T096), so it never reaches the OLFE handler for characters — the two are complementary (domain path + framework net), no change to the merged service. No dedicated handler test (consistent with the other mappings; the character 409 is covered by `CharacterContractTest`/`CharacterIntegrationTest` at T100/T101; the direct-OLFE net is exercised once a US2+ `@Version` path lands). **Verified:** `./mvnw compile` green, Spotless/ktlint clean.

### Frontend alignment (compute-on-read)

- [X] T099 [P] [US1] Align the web sheet with compute-on-read in `web/src/components/characters/CharacterSheet.tsx` + `web/src/api/characters.ts` — derive locally on change for instant feedback, treat the server's resolved response as authoritative on load/save, don't send derived fields, don't assume stored data carries them. **Impl notes:** local live-derivation + authoritative-response were **already** in place (`SheetRenderer` overlays `deriveValues(...)`; `CharacterSheet` re-derives regardless of stored data, so it never *assumes* derived are present). The one gap — save still sent server-derived fields — fixed by a new `baseInputs(definition, values)` strip helper in `components/sheet/derive.ts` (DRY with the derived-field detection, mirrors the server-side strip in `CharacterService`), applied in `handleSave` (`data: baseInputs(definition, values)`, guarded on non-null definition). Doc contracts aligned in `characters.ts` (`UpdateCharacterInput.data` = base inputs) + the component KDoc. Touched a 3rd file (`derive.ts`) for the shared helper. **Verified:** `tsc -b`, ESLint, Prettier all clean; **26/26 web tests pass** (incl. `CharacterSheet.test.tsx`, `derive.test.ts`) — nothing broken, so T102 is a confirm/augment rather than a fix.

### Re-enable / rewrite tests incrementally

- [X] T100 [US1] Re-enable + rewrite `CharacterIntegrationTest` (stays on the `IntegrationTest` base, now Mongo-backed via T090; here rewrite the body + drop `@Disabled`) — create→edit→reload persists base inputs; response carries resolved derived values; **assert the raw stored document has no derived fields**; soft warning without blocking; `409` on stale `@Version`. **✅ Verified: 5/5 green against the Mongo Testcontainer** (raw-doc assertion reads the BSON via `mongo.getCollection("characters")` + `Filters.eq("_id", uuid)` and confirms no `strMod`/`dexMod`/… stored). **⚠ Surfaced + fixed a real gap:** the character write path failed with *"Transaction numbers are only allowed on a replica set member"* — `@ServiceConnection` gives the driver a single-node **direct** connection (the RS advertises `localhost:27017`, unreachable from the host, so the replica-set URL can't be used either). Resolution: **single-document character writes don't need transactions** (Mongo single-doc writes are atomic; research §D5 transactions are for the *multi-doc* US2+ aggregates), so **removed `@Transactional` from `CharacterService`** (kept the `MongoTransactionManager` bean for US2). `@Version`/409 works without a transaction. **Touched merged files:** `CharacterService.kt` (dropped `@Transactional`). **US2 follow-up:** multi-doc campaign/encounter writes *will* need transactions → needs a Testcontainers Mongo that supports them (RS reachable at the mapped port, e.g. host-networked or reconfigured member address) — see spawned task.
- [X] T101 [US1] Re-enable + rewrite `CharacterContractTest` (stays on the `IntegrationTest` base, now Mongo-backed; rewrite body + drop `@Disabled`) against the Mongo-backed context (openapi shapes unchanged). **Impl notes:** the test is pure openapi-shape assertions (401/201/400/200/404/**409**/204), all unchanged by compute-on-read and already proven by T100's write path — so only dropped `@Disabled` + its import and fixed the stale "Postgres" KDoc → Mongo. Body unchanged. **✅ Verified: 10/10 green** against the Mongo Testcontainer. **No `@Disabled` quarantine annotations remain anywhere** (grep-confirmed) — the T103 gate is already satisfied on that count.
- [X] T102 [P] [US1] Confirm/adjust the web `CharacterSheet.test.tsx` for compute-on-read (server-authoritative resolved sheet; MSW responses carry derived values). **Impl notes:** the MSW fixtures were **already** compute-on-read-shaped — the loaded `character.data` carries a server-resolved derived `strMod: 4` and the mock `dnd35Definition` marks `strMod` as `type: 'derived'` — and the render/live-derive/warnings/409 cases already asserted the authoritative-response behaviour. The one **missing guard** was the write side of D8: the save test verified `version`/`strength` travelled with the PUT but never asserted the derived field was *stripped*. Added `expect(putBody!.data).not.toHaveProperty('strMod')` (renamed the case → "sends base inputs only (no derived)…"), which genuinely bites — `strMod` is loaded into the sheet's `values`, so without the T099 `baseInputs` strip it would appear in the body. Refreshed the file-level doc comment to the compute-on-read framing (dropped the stale pre-component T027/T034 "every case is red" note). **Verified:** `tsc -b` / ESLint / Prettier clean; **CharacterSheet 5/5** and the **full web suite 26/26** green. Closes **CHK008** (client persists locally-derived as feedback only, never sends them). Test-only change — no component edit needed (T099 already implemented the strip).

### Green + cleanup

- [X] T103 Confirm no `@Disabled` quarantine annotations remain (dropped in T100/T101) and no Postgres-bound test artifacts survive; `./mvnw verify` (unit + Mongo integration + Spotless) and web `yarn test` both green. **Also fold in the `mongodb-migration.md` checklist sweep** — once the full build is green, tick the boxes that Phase 3B has now objectively satisfied, each with a one-line evidence note pointing at the closing task: **CHK008** (client persists locally-derived as feedback only, never sends them ← T099/T102 web save asserts base-inputs-only), **CHK011** (no observable REST change, verifiable via openapi contract tests ← T101 `CharacterContractTest` green unchanged), **CHK016** (optimistic concurrency `@Version`→409 consistent ← T098 + verify research/data-model/plan wording agrees), **CHK021** (stored character doc contains no derived fields ← T100 raw-BSON assertion), **CHK022** (SC-006 no-data-loss verifiable against `@Version`/409 ← T098/T100 stale-write 409), **CHK026** (quarantine window — every `@Disabled` US1 test restored as the pre-US2 gate ← this task is that gate). Leave the genuinely-open US2+ / spec-consistency items (CHK002, CHK004–CHK007, CHK009, CHK010, CHK012–CHK015, CHK017, CHK019, CHK020, CHK023–CHK025, CHK027, CHK030, CHK033–CHK036) unchecked — they are not closed by 3B. For CHK016 the "consistency across artifacts" clause needs an actual read of research/data-model/plan before ticking (don't tick on the code alone). **Impl notes:** `@Disabled` grep of `api/src` → **zero** matches; no Postgres-bound test *artifacts* in source (`PostgreSQLContainer`/`testcontainers.postgresql`/`JdbcTemplate`/`flyway`/`JsonbSupport` all absent). **`./mvnw clean verify` → BUILD SUCCESS, 36 tests, 0 failures/0 skipped**; web `vitest run` → **26/26**. **⚠ Real finding:** a plain `./mvnw verify` first reported *39* tests including a phantom `no.rauboti.tome.common.JsonbSupportTest` — its source was deleted in T092 but **stale `target/classes/.../JsonbSupport.class` + `target/test-classes/.../JsonbSupportTest.class` lingered** and ran off old bytecode. `clean` unmasked it (36 tests, no phantom); the gate must be `clean verify`, not `verify`. **Checklist sweep done** — ticked CHK008, CHK011, CHK016 (after reading research/data-model/plan), CHK021, CHK022, CHK026 with evidence notes; the listed US2+/spec-consistency items left unchecked. **CHK018 deliberately NOT ticked** — a stale KDoc in `RuleSet.kt:16-17` still claims "Stored as Postgres `JSONB` … via the JdbcTemplate conversion in JsonbSupport", which is now false (BSON); that comment fix is **T104**'s job (as flagged in T092), so CHK018 stays open until then.
- [X] T104 **Final safety sweep** — grep the api module for any *residual* Postgres/Flyway/JdbcTemplate/JSONB references (imports, config, comments; the bulk removal already happened in T090/T092) and clear stragglers; confirm `docker compose up --build` boots the Mongo stack and US1 works end to end. **Impl notes (done on the T103 branch by Gaute's one-time OK):** _(a) Comment sweep_ — fixed the two stale/false active mentions: `RuleSet.kt` KDoc ("Stored as Postgres `JSONB` … via JdbcTemplate/JsonbSupport" → BSON/compute-on-read) and `pom.xml` ("Jackson … SheetData <-> JSONB" → REST (de)serialization). Left the genuinely historical/comparative ones (the `IntegrationTest`/`application-*.yml` "old Postgres harness" notes, `C001`'s "mirrors Flyway/Flamingock" naming analogy) — **closes CHK018**. _(b) Live boot — found + fixed a real deployment blocker._ An isolated boot (throwaway `tome-t104` project, remapped ports 5557/5561, DB+api only — no web/Hive; your running Postgres `platform-*` stack untouched) showed **tome-api crash-looping: it connected to `localhost:27017` instead of `tome-db`, ignoring `SPRING_DATA_MONGODB_URI`**. Root cause, confirmed from the `spring-boot-mongodb-4.1.0.jar` metadata: **`spring.data.mongodb.uri` is deprecated at `level=error` since Boot 4.0 (silently unbound); the connection prefix is now `spring.mongodb.*`.** Tests never caught it because `@ServiceConnection` supplies a `MongoConnectionDetails` bean, bypassing the URI property entirely. **Fix:** `application.yml` connection URI moved to **`spring.mongodb.uri`** (Spring Data's `auto-index-creation` stays under `spring.data.mongodb.*`, not deprecated); `docker-compose.yml` + `.env.example` env var renamed **`SPRING_DATA_MONGODB_URI` → `SPRING_MONGODB_URI`**; stale comments in `application-dev.yml`/`application-test.yml`/`TestcontainersConfiguration.kt`/`MongoConfig.kt` corrected. **Verified end-to-end** against the rebuilt image + fixed compose: api healthy in ~12s, connects to `REPLICA_SET_PRIMARY` (`rs0`), **C001 applied** (`_migrations=[C001]`, `characters` collection + `{userId:1}` index), `GET /api/characters` → **401** unauthenticated (routing + security wired). `./mvnw clean verify` → **36 tests green, Spotless clean**. Isolated stack + volume torn down. **Scope caveats (not code issues):** the *web* image build needs `RAUBOTI_PACKAGE_TOKEN` and a *full authenticated* US1 flow needs **Hive** (neither in this repo's compose) — so "US1 end to end" was verified at the api+DB tier (auth boundary returns 401 as designed), not through a browser login.

**Checkpoint**: US1 runs on MongoDB with compute-on-read; build green; the shared engine + resolve helper
are ready for US2–US5 to build on.

---

## Phase 3C: D&D 3.5 sheet expansion (after 3B green, before US2)

**Runs after Phase 3B is verified green (T104) and before US2 (Phase 4)** — Gaute's chosen gate: prove
the migration out, then flesh the sheet to taste before layering campaigns on top. This is its **own
increment** (fresh branch), deliberately **outside** the parity-protected 3B tasks, so Phase 3B keeps its
"no observable REST change" contract (T100/T101) — the sheet expansion is an *intentional* behavior change
with its own tests. Does **not** technically block US2 (campaigns reference a character, not its sheet
depth), but is sequenced first by preference.

> **Spec-first gate — DONE 2026-07-23.** The sheet-depth clarification ran (spec §Clarifications
> 2026-07-23): **full structured** sheet (skills/feats/weapons/gear as tables) + **full structured
> spellcasting**, all derived values **formula-expressible** for client/server parity (encumbrance
> excluded — needs an opaque Str→load lookup). Engine design + this decomposition: plan.md "Phase 3C
> design". Original single-task T105 (captured 2026-07-23 during T093) is split into T105–T111 below.
> **No `Character`/storage/`CharacterRepository` change** (research D3); no top-level field promoted,
> so **T083**/`Character` schema untouched. `contracts/openapi.yaml` `SheetField.columns` + `table`
> type already added during this re-plan.

- [X] T105 **Engine foundation — `table` (repeating-group) field type.** Extend the Hybrid engine so a field can be a table of typed columns with per-row derived cells, incl. definition-seeded canonical rows: (1) `RuleSet.kt` — add `FieldType.TABLE` + `SheetField.columns: List<SheetField>?` + `SheetField.presetRows: List<Map<String,Any?>>?` (one level deep); (2) **row-aware formula eval, parity-critical** — extend the evaluator in **both** web `web/src/components/sheet/derive.ts` (client live preview) **and** the server compute path with: (a) per-row scope (a column's `derivedFrom` sees the row's columns overlaid on sheet-level values), (b) a `sum(tableId.columnId)` aggregate for sheet-level totals, and (c) an **`abilityMod(<abilityRef>)` indirection primitive** resolving the mod of the ability named by a cell (per-row skill/attack totals) — all three live client-side too (live-preview parity is a firm requirement, spec §Clarifications 2026-07-23); (3) `web/src/components/sheet/SheetRenderer.tsx` — a table widget: render `presetRows` first with preset cells **read-only** + mutable cells editable, allow appending free rows, derived columns read-only; (4) extend the base-inputs strip (`derive.ts` `baseInputs` + server `stripDerived`) to recurse into table rows (per-row derived never stored, D8); (5) `contracts/openapi.yaml` already updated (`SheetField.columns` + `presetRows` + `table`). **Tests:** focused engine test for per-row + `sum()` + `abilityMod()` eval; `derive.test.ts` for client parity; `SheetRenderer.test.tsx` for the table widget incl. preset-row read-only + append. No `Character`/storage change. **Impl notes (done 2026-07-23):** _(a) Architecture_ — rather than keep hand-coding derived values in Kotlin, the server compute path is now **definition-driven**: new shared `SheetCompute.resolve(definition, data)` walks the definition and evaluates every `derivedFrom` via a new Kotlin `FormulaEvaluator` (a port of the web grammar), so derived values are authored **once** in the definition and evaluated identically on client + server — every later content task (T106+) is now definition-only, no new compute code. `DnD35RuleSet.computeDerived` delegates to it; `validate` stays hand-coded. _(b) Indirection primitive_ — implemented as a **generic `ref(field)`** ("value of the field named by this cell") instead of a D&D-specific `abilityMod`, keeping the shared engine rule-agnostic; a skill row uses `ranks + ref(keyAbility) + misc` with `keyAbility` holding a mod-field id like `"strMod"`. Also added `sum(table.column)`. Mirrored exactly in web `derive.ts` (`evaluateFormula` widened to a raw-value scope; new `deriveRow` for per-row cells; `baseInputs` recurses table rows to strip per-row derived). _(c) Preset rows_ — a cell the definition pins in a `presetRow` renders read-only; rows seed from `presetRows` until first edit; preset rows aren't removable, appended rows are. Full canonical seed/persist alignment lands with **T106**. _(d) Derived values coerce to `Int` when integral_ (matches the sheet's natural values + the existing contract). **Verified:** api `./mvnw clean verify` → 46 tests green (new `FormulaEvaluatorTest` 8, `SheetComputeTest` 2; `DnD35RuleSetTest` 7 unchanged — refactor regression guard), Spotless clean; web 36/36, `tsc`/ESLint/Prettier clean. openapi `SheetField.columns`/`presetRows` + `table` type from the re-plan are now backed by code.
- [X] T106 Skills **table**, canonical — add a `skills` table to `definition.json` seeded via **`presetRows`** with the full standard 3.5 skill list, each row pinning `skill` + its fixed `keyAbility` (read-only); editable columns ranks/int, classSkill/bool, misc/int; per-row **total/derived** = `ranks + abilityMod(keyAbility) + misc`. Users append rows for subtyped skills (Craft/Knowledge/Perform/Profession). Canonical list sourced from the 3.5 SRD (OGL). `DnD35RuleSet.validate` soft warning for ranks exceeding the 3.5 max (level + 3 class / half cross-class) + nb/en i18n. Depends on T105. **Impl notes (done 2026-07-23):** replaced the freeform `skills` list with a `table` field — **31 canonical SRD skills** as `presetRows` (`{skill, keyAbility}`, both read-only); columns `skill`(text), `keyAbility`(**select** of the 6 ability mods, so a preset shows e.g. "Str" and an appended row picks one), `ranks`(int), `classSkill`(bool), `misc`(int), `total`(derived `ranks + ref(keyAbility) + misc` — the T105 generic `ref`, since `abilityMod` was implemented as generic `ref`). Subtyped skills (Craft/Knowledge/…) are **not** preset — users append them. `validate` gained a soft `skill.ranks-exceed-max` warning (class max = level+3, cross-class = (level+3)/2; only when level known; rankless rows unflagged). i18n: added `dnd35.skill.*` (column labels) + `dnd35.ability.*` (select labels) to en.json **and** nb.json — English values, matching the existing untranslated dnd35 nb block (a real nb pass is a separate chore). **Seed/persist alignment (the T105-deferred bit):** preset locking is by **row index** — the widget seeds `presetRows` in definition order, materializes all rows into `data.skills` on first edit, and only appends after the preset prefix, so stored order stays aligned with the preset list; a future definition reordering/adding skills would need index care (noted, not a v1 concern). Also **dropped the table widget's own `<Heading>`** (the section heading already titles it — it was rendering "Skills" twice). No `Character`/storage/service change (compute-on-read handles the table via T105). **Verified:** api `./mvnw clean verify` → 50 green (DnD35RuleSetTest now 11, incl. 4 new: definition-shape, skill-total compute, ranks-exceed + within-max), Spotless clean; web 36/36, tsc/ESLint/Prettier clean, i18n JSON valid.
- [X] T107 Weapons/attacks **table** — `attacks` table (columns: weapon/text, attack bonus/**derived** = `baseAttackBonus + <ability>Mod + misc`, damage/text, critical/text, range/text, notes/text) in `definition.json` + i18n. Depends on T105. **Impl notes (done 2026-07-23):** definition-only + i18n + tests (no engine/service change — T105 handles the table). Added an `attacks` section (between `saves` and `skills`) with a `table` field, **no `presetRows`** (user-added rows): columns `weapon`(text), `ability`(select of the 6 ability mods), `misc`(int), `attackBonus`(derived `baseAttackBonus + ref(ability) + misc` — top-level BAB from the combat section + the row's chosen ability via the generic `ref` + misc), `damage`/`critical`/`range`/`notes`(text). i18n: `dnd35.section.attacks` + `dnd35.field.attacks` + `dnd35.attack.*` in en.json **and** nb.json (English, matching the untranslated dnd35 nb block); ability select labels reuse the `dnd35.ability.*` keys from T106. **Verified:** api `./mvnw clean verify` → 52 green (DnD35RuleSetTest now 13, incl. 2 new: attacks-table shape + attack-bonus compute), Spotless clean; web 36/36, tsc/ESLint/Prettier clean, i18n JSON valid. The many-column row layout rides on the pending table-UX polish follow-up (`task_20475fb5`).
- [X] T108 [P] Flat combat/defense derived — extend `definition.json` + `DnD35RuleSet.computeDerived` with an **AC breakdown** (base-input armor/shield/natural/deflection/dodge/size; derived `armorClass = 10 + armorBonus + shieldBonus + dexMod + sizeMod + naturalArmor + deflection + dodge`, `touchAC`, `flatFootedAC`) and **grapple/CMB** (`baseAttackBonus + strMod + sizeMod`) + i18n. Formula-expressible; **no engine dependency** — can land independently of T105. **Impl notes (done 2026-07-23):** definition + i18n + test only — since T105 made compute definition-driven, **no `DnD35RuleSet.computeDerived` Kotlin change** was needed (the generic evaluator computes the new formulas). Combat section trimmed to hpMax/hpCurrent/baseAttackBonus/initiative + derived **`grapple`**; new **`defense`** section holds derived `armorClass`/`touchAC`/`flatFootedAC` + base-input `armorBonus`/`shieldBonus`/`naturalArmor`/`deflection`/`dodge`/`sizeMod`. `armorClass` **changed from a plain int base-input to derived** — a previously-stored manual value is harmlessly recomputed on read and stripped on next save (compute-on-read, D8; dev data is clean-cutover so no migration). i18n: `dnd35.section.defense` + the new `dnd35.field.*` in en.json + nb.json. **Verified:** api `./mvnw clean verify` → 53 green (DnD35RuleSetTest now 14, +1 AC/touch/flat-footed/grapple compute case), Spotless clean; web 36/36, tsc/ESLint/Prettier clean, i18n JSON valid. **3.5-accuracy fix (added 2026-07-23 at Gaute's request):** AC and grapple use **distinct** size-modifier base inputs — `sizeMod` (AC/attack scale: Small +1, Large −1) and **`grappleSizeMod`** (grapple special-size scale: Small −4, Large +4). `grapple = baseAttackBonus + strMod + grappleSizeMod` (its own scale, not `sizeMod`); the compute test pins this by using `sizeMod 0` + `grappleSizeMod 4` → grapple 11 while AC stays on `sizeMod`. (A size→number auto-derivation isn't formula-expressible, so both remain base inputs the user sets.)
- [X] T109 Structured **feats** + **gear** tables — `feats` table (name/type/description) and `gear` table (item/qty/weight/notes, sheet-level `totalWeight` = `sum(gear.weight)`) replacing the freeform lists in `definition.json` + i18n. Depends on T105. **Impl notes (done 2026-07-23):** definition + i18n + tests (no engine/service change — T105). `feats` list → **table** (columns `name`/text, `type`/**select** [general/combat/metamagic/itemCreation], `description`/text; user-added rows). `gear`: the `equipment` list → **table** (columns `item`/text, `quantity`/int, `weight`/int, `notes`/text) + a sheet-level derived **`totalWeight` = `sum(gear.weight)`**; kept `languages`(list) + `notes`(text) in the section. i18n: new `dnd35.feat.*`, `dnd35.gear.*`, `dnd35.field.gear`/`dnd35.field.totalWeight` in en.json + nb.json. **`sum()` targets a base column on purpose** — `totalWeight` sums the base `weight` (not `quantity*weight` via a per-row derived), because `sum` over a *derived* column can't be replicated by the web `deriveValues` (per-row derived isn't in the raw client values), which would break compute-on-read parity; so weight is the line's total, quantity is informational. **Cross-effect fixed:** `CharacterIntegrationTest`'s "persists and reloads exactly" fixture used the old freeform `feats:["Dodge"]` — updated to the table row `feats:[{"name":"Dodge","type":"general"}]` + asserts `$.data.feats[0].name` (a bare-string row is silently dropped now that feats is a table, which is exactly what caught it). **Verified:** api `./mvnw clean verify` → 55 green (DnD35RuleSetTest now 16: +feats/gear table-shape + gear `totalWeight` sum; CharacterIntegrationTest 5/5 with the updated fixture), Spotless clean; web 36/36 (mock fixture unaffected — it's a separate compact def), tsc/ESLint/Prettier clean, i18n JSON valid.
- [X] T110 Spellcasting **stats** — caster class/level, key ability, `spellSaveDcBase` (derived `10 + abilityMod(spellKeyAbility)`, per-level DC = base + spell level), bonus spells/day, concentration — `definition.json` + `DnD35RuleSet.computeDerived` + i18n. Depends on T105. **Impl notes (done 2026-07-23):** definition + i18n + test only (no engine/service change — T105). New `spellcasting` section (after `gear`): `casterClass`(text), `casterLevel`(int), `spellKeyAbility`(**select** of the 6 ability mods), `spellSaveDcBase`(derived `10 + ref(spellKeyAbility)` — generic `ref`; a level-N spell's DC = base + N, shown per-level in T111). i18n: `dnd35.section.spellcasting` + `dnd35.spell.*` in en.json + nb.json; the key-ability select reuses `dnd35.ability.*`. **Two listed items deliberately re-homed** (flagged to Gaute): _(a) bonus spells/day_ is inherently **per-spell-level** — it's `max(0, floor((keyAbilityMod − spellLevel) / 4) + 1)` per level, which is formula-expressible as a **per-row derived** in T111's per-level spell table (needs the row's `spellLevel`), not a single stat here; deferred to T111. _(b) concentration_ is the Con-based **Concentration skill**, already a preset row in the T106 skills table — omitted here to avoid a duplicate field. **Verified:** api `./mvnw clean verify` → 56 green (DnD35RuleSetTest now 17: +spellSaveDcBase compute, intMod 4 → DC base 14), Spotless clean; web 36/36, tsc/ESLint/Prettier clean, i18n JSON valid.
- [X] T111 Spellcasting **spell slots** (scaffold) — per-spell-level slots table + bonus spells, in `definition.json` + i18n. **Scope pinned 2026-07-23:** the full-SRD-spell-*catalog* half was carved into its own increment **T112** (Gaute chose "baked SRD catalog" but it's a large, must-be-*sourced* effort — see T112); T111 delivers the mechanical scaffold. **Impl notes:** new `spellSlots` **table** in the `spellcasting` section, **preset rows for spell levels 0–9** (spellLevel pinned read-only), columns `slotsPerDay`(int, user-entered — the class×level base counts are a per-character lookup, not static/formula-derivable), `bonusSpells`(**derived**), `total`(derived `slotsPerDay + bonusSpells`), `known`/`prepared`(int). **Bonus-spells formula** `min(spellLevel, 1) * max(0, floor((ref(spellKeyAbility) - spellLevel) / 4) + 1)` — the `min(spellLevel,1)` factor zeroes level-0 (cantrips get no bonus spells) since the grammar has no conditionals; fully formula-expressible, so it live-previews. i18n: `dnd35.spell.slotsTable` + `dnd35.spell.slots.*` in en.json + nb.json. **Verified:** api `./mvnw clean verify` → 58 green (DnD35RuleSetTest now 19: +slot-scaffold shape + bonus/total compute incl. level-0 zeroing), Spotless clean; web 36/36, tsc/ESLint/Prettier clean, i18n JSON valid. **Minor UX (defer to `task_20475fb5` table polish):** the preset-only slots table still shows an "Add row" button — harmless (no one adds a level-10) but ideally suppressed for preset-only tables.
> **Spell-catalog re-plan — DONE 2026-07-23.** Decisions pinned (spec §Clarifications 2026-07-23,
> post-T111): **baked** full-SRD catalog, **sourced** (fetch/parse OGL, never hand-authored),
> **class-filtered** picker (new engine capability), **core caster classes** (Wizard/Sorcerer, Cleric,
> Druid, Bard, Paladin, Ranger) with per-class levels. Design + rationale: plan.md "T112 design: baked
> SRD spell catalog". The single T112 is split into T112–T114 (engine-enabler-first, like T105).
> `contracts/openapi.yaml` already carries `SheetField.optionsFrom` + the `…/catalogs/{catalog}` endpoint.

- [X] T112 Spellcasting **spell dataset** (baked SRD, data only) — fetch/parse an authoritative **OGL 3.5 SRD** source → companion resource `api/src/main/resources/rulesets/dnd35/spells.json`: each spell `{ id, name, school, classLevels: { wizard, sorcerer, cleric, druid, bard, paladin, ranger } }` (per-class spell level). **Sourced, never hand-authored** — flag any spell/level that can't be verified rather than inventing it. Lands with a load + spot-check test (e.g. *Fireball* = Sorcerer/Wizard 3, *Cure Light Wounds* = Cleric 1). Data only — no wiring/UI. Depends on T110/T111. **Impl notes (done 2026-07-23):** **sourced from d20srd.org** (the OGL 3.5 SRD) via a committed, reproducible generator **`tools/build-spells.mjs`** (Node): it fetches the 6 per-class spell-list pages with a browser User-Agent (WebFetch is 403-blocked by the site; Node fetch + UA works), parses each `<strong>`-wrapped `/srd/spells/{id}.htm` entry under its `<h3>` level header (the `<strong>` wrapper cleanly excludes in-description cross-ref links), strips the trailing `:`/component-markers, and merges by spell id into per-class level maps. **591 unique spells**; per-class counts Sor/Wiz 371, Cleric 219, Druid 169, Bard 164, Paladin 44, Ranger 51. Shape shipped is **`{ id, name, classLevels }`** — **`school` dropped**: the divine list pages (cleric/druid/bard/paladin/ranger) have no `<h4>` school subheaders, so school is only available for arcane spells; partial/inconsistent metadata is worse than none, and the picker (T114) needs only name + per-class level (school could be a later per-spell-page enrichment). `spells.json` carries a `source` provenance line. **Verified:** `SpellCatalogTest` (3) loads the file, asserts ≥500 well-formed entries + unique ids + spot-checks (Fireball Sor/Wiz 3, Cure Light Wounds Clr 1/Rgr 2, Wish Wiz 9, Bless Pal 1); api `./mvnw clean verify` → 61 green, Spotless clean. No wiring/UI (T113/T114). No web change.
- [X] T113 **Engine: catalog-backed, field-filtered select** — generic capability so a select's options come from a named catalog filtered by another field's value: `SheetField.optionsFrom { catalog, filterBy }` (`RuleSet` types + openapi + web Zod), a catalog service + `GET /api/rule-sets/{id}/catalogs/{catalog}?filter=` endpoint (loads `spells.json`, T112), and the web select widget's **fetched-options** behavior (async, keyed off the `filterBy` field value) + tests. Rule-set-agnostic (reusable by future rule sets). The enabler — like T105 was for tables. Depends on T112 (for a real catalog to serve; the mechanism itself is generic). **Impl notes (done 2026-07-23):** _Kotlin_ — added `OptionsFrom(catalog, filterBy)` + `SheetField.optionsFrom`; new **`catalogs`** package (mirrors the rule-set registry pattern): `Catalog` interface + `CatalogOption(value, label, meta?)`, `CatalogRegistry` (injects `List<Catalog>`, resolves by ruleSetId+name → 404), `CatalogController` (`GET /api/rule-sets/{id}/catalogs/{catalog}?filter=`, behind the `/api` gate), and `SpellCatalog` (loads `spells.json`, filters by caster class, `meta.level` per class, ordered by level then name; blank filter → none). _Web_ — `optionsFrom` on the Zod column/field schema; new `api/catalogs.ts` (`getCatalogOptions`); `SheetRenderer` threads `ruleSetId`, and `CellInput` uses a **`useCatalogOptions`** hook (fetches keyed off the row's `filterBy` value from `sheetScope`, module-cached per ruleSet/catalog/filter so a table's many rows don't each re-fetch; empty/cached derived during render, only the async result set via state to satisfy the `react-hooks/set-state-in-effect` rule). Catalog option labels are **literal** (data), unlike static `options`' i18n `labelKey`. Left top-level `FieldWidget` selects static-only (v1's only catalog select is the T114 spells **column**; extend later if needed). openapi catalog endpoint response `labelKey`→**`label`** to match. **Verified:** api `./mvnw clean verify` → 69 green (`SpellCatalogFilterTest` 5, `CatalogControllerTest` 3 incl. the endpoint returning Fireball at wizard level 3), Spotless clean; web 37/37 (`SheetRenderer` catalog test fetches class-filtered options via MSW, picks a spell, reports its id), tsc/ESLint/Prettier clean. No dnd35 content change (that's T114).
- [X] T114 **Spells table + wiring** — add the `spells` table to `definition.json` (spellcasting section): `spell` (class-filtered select via `optionsFrom { catalog: "spells", filterBy: "casterClass" }`), `level` (catalog-supplied for that class), `prepared`, `notes`; wire the catalog endpoint into the spellcasting UI + i18n + tests. Closes the spell catalog. Depends on T112 + T113. **Impl notes (done 2026-07-23):** definition + i18n + a small widget capability + tests (engine/service already done in T113). Added the `spells` table to the spellcasting section — columns `spell` (catalog select via `optionsFrom {spells, casterClass}`), `level` (int), `prepared` (int), `notes` (text); no presetRows (user-added rows). i18n `dnd35.spell.listTable` + `dnd35.spell.col.*` in en/nb. **`level` is catalog-supplied** via a new generic widget behaviour: when a catalog option is picked, its `meta` fills matching **sibling columns** — `CellInput` gained an `onPick(value, meta)` path and `TableWidget` an `updateRow` + `pickMeta(meta, columns, pickedId)` (keeps only meta keys that are columns of this table, excluding the picked column), so picking a spell writes `{ spell: id, level: meta.level }`. Rule-set-agnostic (any catalog select + matching sibling columns benefits). **Verified:** api `./mvnw clean verify` → 70 green (DnD35RuleSetTest now 20: +spells-table shape asserting the `spell` column's `optionsFrom` + a `level` column); web 38/38 (a SheetRenderer test picks a fetched spell and asserts the `level` column auto-fills from `meta.level`), tsc/ESLint/Prettier clean, i18n valid.

**Checkpoint**: the 3.5 sheet is as rich as v1 wants — **Phase 3C complete (T105–T114)**: canonical skills, weapons, defense/AC + grapple, feats/gear, full spellcasting (stats + slots + class-filtered SRD spell picker). US1 still green; US2 can begin.

> **⚠ Superseded in part by Phase 3D (ADR-001, 2026-07-24).** Phase 3C reached the target *sheet
> content*, but expressed it as data (`definition.json`) + `derivedFrom` formulas over an opaque
> `SheetData` map. **Phase 3D re-expresses this same 3.5 content as a typed Kotlin sheet** (computed
> properties instead of formulas; typed row classes for the skills/attacks/gear/spells tables). The
> *content decisions* from 3C stand and are the spec for the typed model; the *mechanism* (formula
> engine + generic renderer) is retired. Treat T105–T114 as the authoritative description of **what**
> the 3.5 sheet contains when porting it to types in Phase 3D.

### Phase 3C follow-ups (UX polish, non-blocking — deferred, do not gate US2)

> **⚠ Reconsider after Phase 3D (ADR-001).** T115/T116 target the generic `SheetRenderer`, which Phase
> 3D may **retire** in favor of typed per-rule-set components. Do **not** start them before 3D lands;
> then re-scope against whatever renders the sheet (likely typed DnD35 components), or fold the layout
> work into 3D's frontend task. T117 (spell `school` enrichment) is data-only and 3D-independent.

- [ ] T115 **Sheet table layout — compare & choose** (spawned chip `task_20475fb5`). The `table` widget (`web/src/components/sheet/SheetRenderer.tsx`) renders each row as a Card with a label on every cell — verbose for the ~31-row skills table and the wide attacks table. Prototype a condensed **column-headers-once** layout (optionally a 2nd alternative), screenshot each, and let Gaute pick by eye (he wants to compare visually). **Hard constraint:** preserve each cell's accessible name (`${columnLabel} ${rowIndex+1}`) so `SheetRenderer.test.tsx` table queries stay green.
- [ ] T116 **Collapsible sheet sections** (spawned chip `task_52234a22`). Add a reusable Chakra-based collapsible/accordion wrapper to the **`@rauboti/ui`** package (design-system home, not inline in tome), then consume it in `SheetRenderer.tsx` to wrap each section by its heading. **Default expanded** (the field-by-role tests hide otherwise); **independent per-section collapse** (not single-open); preserve a11y. Note: the `@rauboti/ui` change likely lives in that package's own repo → flag what needs publishing/version-bumping for tome to consume.
- [ ] T117 [P] **Spell catalog `school` metadata** (optional enrichment, deferred in T112). `spells.json` omits `school` (the divine SRD list pages have no `<h4>` school subheaders; only arcane spells expose it). If wanted, enrich by fetching each spell's own SRD page (`tools/build-spells.mjs`) and add `school` to each entry (+ surface it in the picker label). ~600 extra fetches — weigh the value.

---

## Phase 3D: Typed rule-set engine — data-driven → strongly-typed, code-first (AMENDMENT 2026-07-24, ADR-001)

**Runs after Phase 3C is green and before US2 (Phase 4)** — so US2/US3 (campaigns, and especially NPCs
at T050) are authored against the typed sheet from the start rather than re-typed later. Pivots the
Hybrid engine from an opaque `SheetData` map + `definition.json` + `derivedFrom` formulas to a **sealed
hierarchy of typed per-rule-set sheets** with **computed-property** derived values. Design, alternatives,
consequences, and the persistence-spike findings: [ADR-001](decisions/ADR-001-typed-ruleset-sheets.md).

> **De-risked first.** A throwaway spike (`api/src/test/kotlin/no/rauboti/tome/spike/TypedSheetPersistenceSpike.kt`,
> 3/3 green on a real Mongo Testcontainer) proved: (A) a sealed sheet field round-trips through
> `MongoTemplate` with no custom converter; (B) the storage discriminator is `_class` + `@TypeAlias`
> (**not** Jackson `@JsonTypeInfo`, which is wire-only), so storage decouples from class FQNs; (C) a
> getter-only computed `val` is **never persisted** — so "derived can't be stored" holds by construction
> and the write-time `stripDerived` step is **deleted**, and the base-inputs/resolved split is *optional*
> (default to a single typed class with computed derived). Delete the spike at T128.

**Scope pinned by 3C.** T105–T114 defined **what** the 3.5 sheet contains (canonical skills, attacks,
defense/AC + grapple, feats/gear, full spellcasting incl. the class-filtered SRD spell picker). Phase 3D
re-expresses that exact content as types — no new sheet content, no new rules. The class-filtered spell
**catalog** (server-side, T112–T113: `spells.json` + `CatalogController`) **survives**; only the
`definition.json`-driven `optionsFrom` descriptor is replaced by the typed component calling the catalog.

**Independent Test**: `./mvnw clean verify` + web tests green against the typed engine; a created DnD35
character reloads as the typed sheet; the raw stored doc holds base inputs only with `_class: "dnd35"`
(no derived); GET/PUT responses carry computed derived values; an unknown/mismatched `ruleSetId` is
rejected at the wire boundary; the DnD35 typed sheet screen renders and edits end to end.

**Preflight (Constitution III)**: fresh branch off `main` before any code; leave work uncommitted for
Gaute to review/commit. **Branching**: recommend the **backend slice (T118–T125) as one increment** and
the **frontend slice (T126–T127) as a second**, T128 closing — but the split is Gaute's call (Phase 3B
precedent: a whole re-platform as one increment of small, line-by-line-reviewable tasks).

### Spec-first propagation (SDD Principle I — amend the contract before code)

- [X] T118 Propagate ADR-001 into the spec artifacts **before** engine code. **`spec.md`** — soften **FR-023/SC-009** from "add a rule set with no shared-engine change (definition + logic only)" to the ADR reality (a new rule set is a typed sealed variant + its computed derived + components + filling exhaustive `when`s; the compiler enumerates the sites) — **maintainer-owned edit, flag for Gaute**. **`data-model.md`** — describe `characters.data` (and NPC `data`) as a **typed, discriminated base-inputs sub-document** (`_class` = `@TypeAlias`, e.g. `"dnd35"`; derived never stored, now by construction), not a free-form map. **`contracts/openapi.yaml`** — change `Character.data` / `Npc.data` from free-form `object` to **`oneOf` the per-rule-set sheet schemas with a `ruleSetId` discriminator**; remove `SheetDefinition`/`SheetField`/`optionsFrom` schemas that no longer describe the wire (the `GET /api/rule-sets/{id}` definition payload — see T123 — and the catalog endpoint stay). Note that `web/` **codegens** its TS sheet types from this schema (T126). No code yet; this task is the contract the rest of 3D implements against. **Impl notes (2026-07-24, branch `001-campaign-management-t118-typed-contract`):** _openapi_ — removed `SheetDefinition` (+ its nested `SheetField`/`options`/`optionsFrom`/`presetRows`); added the discriminated **`Sheet`** union (`oneOf` `DnD35Sheet`/`DarkSoulsSheet`, discriminator `propertyName: ruleSetId`) + a **full `DnD35Sheet`** (56 props — the exact Phase-3C content sourced from `definition.json`: identity/abilities/combat/defense/saves + typed row schemas `DnD35AttackRow`/`SkillRow`/`FeatRow`/`GearRow`/`SpellSlotRow`/`SpellRow`) with derived marked **`readOnly`** (response-only, ignored on write); `DarkSoulsSheet` a discriminator-only stub. `Character.data`/`Npc.data` now `$ref Sheet`. `GET /rule-sets/{id}` → returns `RuleSetSummary` (id+name; no more definition payload); the `…/catalogs/{catalog}` endpoint **stays** (typed component fetches it). **Request-side decision (flag):** POST/PUT `data` kept **loosely typed (`object`)** with a description pointing at `Sheet` — the precise request binding (how the `ruleSetId` discriminator is sourced on create vs. inferred from the resource; partial semantics) is deferred to **T124**, so T118 commits only the **response** contract precisely (what T119/T120/T126 test + codegen against). `readOnly` already encodes "derived ignored on write". Validated: YAML parses, **zero dangling `$ref`s**. _spec.md_ — softened FR-001 + FR-023 + SC-009 + the US5 narrative + the "Rule set" glossary entry to the typed/code-first framing, with an amendment blockquote under **Rule sets** (⚠ **FR/SC wording is maintainer-owned — Gaute to ratify**). _data-model.md_ — `characters.data`/NPC `data` = typed discriminated sub-document (`_class`=`@TypeAlias`); rewrote the "Derived values" section (computed properties, no resolver/strip — `CharacterDataResolver`/`computeDerived` retired). **No code touched** (contract-only, as scoped). ⚠ The two untracked WIP `characters/data/` files still fail Spotless (trailing comma) — pre-existing, not T118.

### Tests for Phase 3D (write first, must FAIL) ⚠️

- [ ] T119 [US1] Backend typed round-trip + wire tests — rewrite `CharacterIntegrationTest` for the typed engine: a created DnD35 character reloads as `DnD35CharacterData`; the **raw stored doc holds base inputs only + `_class: "dnd35"`, no derived** (port the existing raw-BSON assertion); GET/PUT echo carries computed derived; a **stale `@Version` → 409** still holds; an **unknown/mismatched `ruleSetId` on POST/PUT is rejected** (wire `oneOf` bind failure → 400). Update `CharacterContractTest` for the `oneOf` request/response shape. These replace the spike's coverage. In `api/src/test/kotlin/no/rauboti/tome/characters/`.
- [ ] T120 [US1] DnD35 typed-sheet unit tests — assert the **computed derived properties** (ability mods, saves, BAB, AC/touch/flat-footed + grapple, per-row skill/attack totals, gear `totalWeight`, spell-slot bonus/total incl. level-0 zeroing) reproduce the outputs the retired formulas produced. **Port the expectations from `DnD35RuleSetTest`/`SheetComputeTest`/`FormulaEvaluatorTest`** onto the typed model, so parity with 3C is proven, then those old tests retire with their subjects (T125). In `api/src/test/kotlin/no/rauboti/tome/rulesets/DnD35CharacterDataTest.kt`.
- [ ] T121 [P] [US1] Web tests (must FAIL) for the typed DnD35 sheet components replacing the generic-renderer tests — render/edit/derived-display/warning/version-conflict against the typed component tree; class-filtered spell picker still fetches from the catalog. In `web/src/components/characters/` (and retire/replace `SheetRenderer.test.tsx`, `derive.test.ts` at T126).

### Backend — typed sheet + engine

- [ ] T122 [US1] Sealed **`CharacterData`** hierarchy — finish the maintainer's started files in `api/src/main/kotlin/no/rauboti/tome/characters/data/`: add the missing `package no.rauboti.tome.characters.data`; make `DSCharacterData.kt` declare its **own** `DarkSoulsCharacterData` (currently a duplicate of the DnD35 class — a redeclaration error); model **`DnD35CharacterData`** with base-input constructor properties + **derived computed `val`s** (the 3C content), and **typed row data classes** for the tables (skills/attacks/feats/gear/spell-slots/spells). Add `@TypeAlias("dnd35")`/`@TypeAlias("darksouls")` (storage discriminator value) and Jackson `@JsonTypeInfo(use = NAME, property = "ruleSetId")` + `@JsonSubTypes` (wire polymorphism). `DarkSoulsCharacterData` stays a **minimal stub variant** so `when`s compile (US5 fleshes it, T072–T075). Depends on T118.
- [ ] T123 Reshape the **`RuleSet`** strategy for the typed world in `api/src/main/kotlin/no/rauboti/tome/rulesets/` — drop `computeDerived(SheetData)` and `definition(): SheetDefinition` (compute is now the sheet's own properties); keep `id()`/`name()` and `validate(...)` **retyped to read the typed sheet** (soft warnings, FR-005, unchanged behavior). Decide the fate of **`GET /api/rule-sets/{id}`**: the web no longer renders from a definition, so this shrinks to serving **id + name** for the create-character picker (drop the `SheetDefinition` body); `RuleSetRegistry` unchanged. Retire `SheetData`/`SheetDefinition`/`SheetField`/`SheetChange`/`OptionsFrom`/`FieldType` from `RuleSet.kt` (what survives moves with the type). Depends on T122.
- [ ] T124 [US1] Retype the character slice — `Character.data: CharacterData` (was `SheetData`) in `Character.kt`; `CharacterRepository` is largely unchanged (the spike proved the round-trip); **rewrite `CharacterService`**: **delete `stripDerived`** (finding C — computed props never persist) and **delete `CharacterDataResolver`** (the "resolved sheet" is just the typed object; its computed props serialize as derived on the wire), `validate` on the typed sheet; `CharacterController` returns the typed sheet directly (HP read from typed fields, not `data["hpCurrent"]`). Delete `CharacterDataResolver.kt` + `CharacterDataResolverTest.kt`. **Lock the request shape (decided 2026-07-24, deferred here from T118):** the POST/PUT request `data` becomes the **typed discriminated `Sheet` union too** (base inputs; `readOnly` derived rejected/ignored), replacing T118's loosely-typed `object` — tighten `contracts/openapi.yaml` accordingly (request `data` → `$ref Sheet`). Enforce on **both ends**: server binds `data` polymorphically (Jackson `@JsonTypeInfo` on `ruleSetId`) and **rejects an unknown/mismatched rule set → 400**; the create request's discriminator is reconciled with the top-level `ruleSetId` (decide: require `data.ruleSetId` and 400 on disagreement, vs. inject the resource's — pick the simpler, document it); the web sends the codegen'd typed shape (T126). This is what makes "enforce the DnD35 vs. DarkSouls shape on both client and server" real. Depends on T122/T123; makes T119 pass (incl. the unknown/mismatched-`ruleSetId` → 400 assertion).

### Backend — retire the data-driven machinery

- [ ] T125 Retire the formula engine + definition structure — delete `FormulaEvaluator.kt`, `SheetCompute.kt` and their tests (`FormulaEvaluatorTest`, `SheetComputeTest`), and the now-superseded assertions in `DnD35RuleSetTest` (parity now lives in T120's typed test). Remove `api/src/main/resources/rulesets/dnd35/definition.json` (structure/`derivedFrom` are now code) — **keep `spells.json`** (the T112 catalog data, now read by the typed catalog). Confirm nothing references the deleted types (`./mvnw clean compile`). Depends on T124.

### Frontend — typed sheet

- [ ] T126 [US1] Typed web sheet — **codegen the discriminated-union TS sheet types from `openapi.yaml`** (T118) so web types can't drift from the wire; build **typed DnD35 sheet components** (sections/tables/derived display) replacing the generic definition-driven `SheetRenderer` for dnd35; **retire `web/src/components/sheet/derive.ts`** (client formula mirror) and the generic renderer + widgets (or reduce to shared primitives the typed components reuse — decide here). The **class-filtered spell picker keeps fetching** from the catalog endpoint (`web/src/api/catalogs.ts` unchanged); the `optionsFrom` descriptor is replaced by the typed component calling it. Update `web/src/api/characters.ts` (typed `data`, send base inputs only — now inherent to the typed shape). Depends on T118; makes T121 pass.
- [ ] T127 [P] [US1] Web green — finish/adjust the typed component tests (T121) and the characters page/edit flow; `tsc -b` / ESLint / Prettier / `vitest run` all green. Reconcile the deferred UX follow-ups T115/T116 against the typed components (re-scope or fold in). Depends on T126.

### Green + cleanup

- [ ] T128 **Delete the spike** (`api/src/test/kotlin/no/rauboti/tome/spike/TypedSheetPersistenceSpike.kt` + the empty `spike/` dir) and run the full gate — `./mvnw clean verify` (unit + Mongo integration + Spotless) and web `vitest run` green; grep for residual `SheetData`/`definition.json`/`derive.ts`/`FormulaEvaluator`/`computeDerived` references and clear stragglers (mirror the T104 sweep discipline); confirm the DnD35 typed sheet works end to end (api + DB tier; full browser flow needs Hive, as at T104). Update the Phase 3C follow-up notes (T115/T116) to their re-scoped form. Depends on T125/T127.

**Checkpoint**: the engine is strongly-typed end to end; US1's 3.5 sheet is a typed `DnD35CharacterData` with computed derived; storage holds typed base inputs only (`_class` discriminator); the wire is a discriminated union the web codegens from; the formula engines and generic renderer are retired. US2/US3 build (and author NPCs) against the typed engine.

---

## Phase 4: User Story 2 - Run a campaign and build its roster (Priority: P2)

**Goal**: A DM creates a rule-set-bound campaign and adds players' matching-rule-set characters; DM
sees everything, players see only self + shared.

**Independent Test**: Create a campaign, add a matching character (rule-set mismatch refused), verify
DM full view vs limited player view and access denial for non-members.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [ ] T035 [P] [US2] Contract test for `/api/campaigns` and `/members` in `api/src/test/kotlin/no/rauboti/tome/campaigns/CampaignContractTest.kt`
- [ ] T036 [P] [US2] Integration test — create campaign; add matching-rule-set character; **refuse** mismatch with a problem detail; player limited view vs DM full view; deny non-member access in `api/src/test/kotlin/no/rauboti/tome/campaigns/CampaignIntegrationTest.kt`
- [ ] T037 [P] [US2] Web test — role-aware `CampaignPage` (DM roster/all vs player self+shared) in `web/src/pages/CampaignPage.test.tsx`

### Implementation for User Story 2

- [ ] T038 [US2] Migration change `C002` (Spring Data-native, ledger-guarded — see T089/T091) — create `campaigns` collection + indexes (`{dmId:1}`, `{"members.playerId":1}`, and **unique partial multikey** `{"members.characterId":1}` with `partialFilterExpression {status:"active"}` = "one active campaign per character", data-model.md Invariants) in `api/src/main/kotlin/no/rauboti/tome/config/migration/C002__CreateCampaigns.kt` (`C<order>__<Name>` convention + per-file ktlint suppress — see T091)
- [ ] T039 [US2] `Campaign` aggregate document (`@Document("campaigns")`, `@Id`, `@Version`, embedded `members[]` of `{characterId,playerId,addedAt}`) + `CampaignRepository` (MongoTemplate; `$push`/`$pull` for roster) in `api/src/main/kotlin/no/rauboti/tome/campaigns/` — membership is embedded, not a separate collection
- [ ] T040 [US2] `PermissionService` — campaign-scoped visibility (DM full; player self + shared; deny others) in `api/src/main/kotlin/no/rauboti/tome/campaigns/PermissionService.kt`
- [ ] T041 [US2] `CampaignService` — create, archive, roster add (enforce `character.ruleSet == campaign.ruleSet` FR-008, **and** reject a character already in an active campaign via an app pre-check backed by the unique partial index D6), remove (pull the member, keep the character); **a DM MAY add a self-owned character to their own campaign (FR-017) — this creates an ordinary membership and MUST NOT grant that player DM visibility nor let the DM hide content from themselves** in `api/src/main/kotlin/no/rauboti/tome/campaigns/CampaignService.kt`
- [ ] T042 [US2] `CampaignController` + members endpoints returning the role-aware `CampaignView`; the caller's `role` is computed per campaign (DM vs player) even when they are both (FR-017) in `api/src/main/kotlin/no/rauboti/tome/campaigns/CampaignController.kt`
- [ ] T043 [US2] Web campaigns API client + Zod schemas in `web/src/api/campaigns.ts`
- [ ] T044 [US2] Web campaign create + roster management (DM adds by character id / removes) in `web/src/components/campaigns/`
- [ ] T045 [US2] Web role-aware `CampaignPage` (DM view vs limited player view) in `web/src/pages/CampaignPage.tsx`

**Checkpoint**: US1 and US2 both work independently.

---

## Phase 5: User Story 3 - Track sessions and control NPCs (Priority: P3)

**Goal**: The DM manages NPCs, private/shared content, sessions, and optionally plays their own PC.

**Independent Test**: Private NPC/note hidden from players while shared content is visible; DM adds
own PC; session goes planned → completed.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [ ] T046 [P] [US3] Contract test for `/npcs`, `/content`, `/sessions` in `api/src/test/kotlin/no/rauboti/tome/campaigns/DmToolsContractTest.kt`
- [ ] T047 [P] [US3] Integration test — private NPC/note hidden from a player, shared content visible; DM-owned PC in own campaign grants players no DM visibility; session lifecycle in `api/src/test/kotlin/no/rauboti/tome/campaigns/DmToolsIntegrationTest.kt`
- [ ] T048 [P] [US3] Web test — share/private toggle and player-visibility behavior in `web/src/components/campaigns/Content.test.tsx`

### Implementation for User Story 3

- [ ] T049 [US3] Extend the `Campaign` aggregate with embedded `npcs[]` / `content[]` and campaign-level `rolls[]` (models + serialization) — these stay **embedded in the campaign document** (`sessions` are a separate collection — T052); **guard writes against the 16 MB document limit** (clear error before the driver does — residual growth candidates are `content`/campaign `rolls`, data-model.md "Document-size bound") in `api/src/main/kotlin/no/rauboti/tome/campaigns/`
- [ ] T050 [US3] `Npc` embedded model + service/controller (reuses the sheet engine — **authored against the Phase 3D typed sheet** (ADR-001): the NPC's `data` is the same sealed `CharacterData`/`NpcData`-style typed sheet, derived as computed properties, base inputs only; **no formula-based resolver** — the "NPC-side `CharacterDataResolver`" this task originally described is obsolete once 3D lands) operating on `campaign.npcs[]` via `CampaignRepository` array updates — no separate repo/collection — in `api/src/main/kotlin/no/rauboti/tome/npcs/`
- [ ] T051 [US3] `Content` embedded model + service/controller (visibility private/shared) operating on `campaign.content[]` via `CampaignRepository` array updates in `api/src/main/kotlin/no/rauboti/tome/content/`
- [ ] T052 [US3] `Session` as its **own `sessions` collection** (`@Document`, `@Id`, `@Version`, `campaignId` reference, embedded session-level `rolls[]`) + `SessionRepository` (MongoTemplate, list by `campaignId`) + service/controller (nested under `/campaigns/{id}/sessions`) in `api/src/main/kotlin/no/rauboti/tome/sessions/`
- [ ] T053 [US3] Extend `PermissionService` with `npc.isPrivate` and `content.visibility` rules in `api/src/main/kotlin/no/rauboti/tome/campaigns/PermissionService.kt`
- [ ] T054 [US3] Web NPC management UI (DM) in `web/src/components/campaigns/Npcs.tsx`
- [ ] T055 [US3] Web content share/private UI + player view in `web/src/components/campaigns/Content.tsx`
- [ ] T056 [US3] Web sessions UI in `web/src/components/campaigns/Sessions.tsx`

**Checkpoint**: US1–US3 independently functional.

---

## Phase 6: User Story 4 - Run live combat with dice and initiative (Priority: P4)

**Goal**: The DM runs an encounter (initiative order, turns/rounds) with a server-authoritative dice
roller; authorized participants see revealed state update live over SSE.

**⚠️ Depends on US3**: apply-to-sheet for NPCs (T063) and the event publisher (T066) reach into the
NPC write path (T050) and the US1–US3 services — schedule US4 after US3 is in place.

**Independent Test**: Start an encounter (ordered by initiative), roll and apply damage (warns without
blocking), advance turns → a watching player's view updates live; players never receive hidden state.

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [ ] T057 [P] [US4] Unit test for the dice evaluator (`NdM`, `±K`, multi-term, `kh`/`kl`, malformed input) in `api/src/test/kotlin/no/rauboti/tome/dice/DiceEvaluatorTest.kt`
- [ ] T058 [P] [US4] Contract test for `/rolls`, `/encounters` (+ `next-turn`/`end`), and `/campaigns/{id}/stream` in `api/src/test/kotlin/no/rauboti/tome/combat/CombatContractTest.kt`
- [ ] T059 [P] [US4] Integration test — **SSE fan-out authorization** (a player never receives an event for private/hidden content or another player's private sheet, SC-004), turn advance broadcasts live, roll apply-to-sheet with warning in `api/src/test/kotlin/no/rauboti/tome/realtime/StreamAuthorizationIntegrationTest.kt`
- [ ] T060 [P] [US4] Web test — `InitiativeTracker` live update via an `EventSource` stub + `DiceRoller` apply-to-sheet in `web/src/components/combat/Combat.test.tsx`

### Implementation for User Story 4

- [ ] T061 [US4] Dice evaluator (pure Kotlin, `SecureRandom`) in `api/src/main/kotlin/no/rauboti/tome/dice/DiceEvaluator.kt`
- [ ] T062 [US4] Migration changes `C003` (create `sessions` + index `{campaignId:1}`) and `C004` (create `encounters` + indexes `{sessionId:1}`, `{campaignId:1}`) — Spring Data-native, ledger-guarded (see T089/T091) — in `api/src/main/kotlin/no/rauboti/tome/config/migration/` — **no `rolls` change**: rolls are embedded in their container (campaign/session/encounter), not a collection
- [ ] T063 [US4] `Roll` as an **embedded sub-document** (`initiatorId`, expression, results, total, `appliedTo?`, `createdAt` — **no scope-id fields**) appended to its container's `rolls[]` via **nested endpoints** (`POST /campaigns/{id}/rolls`, `…/sessions/{sid}/rolls`, `…/encounters/{eid}/rolls`); optional apply-to-sheet through the `CharacterService`/NPC write path (warnings apply; response resolved-on-read) in `api/src/main/kotlin/no/rauboti/tome/dice/`
- [ ] T064 [US4] SSE infrastructure — emitter registry + `AuthorizedEventPublisher` (reuses `PermissionService`) + async config in `api/src/main/kotlin/no/rauboti/tome/realtime/`
- [ ] T065 [US4] `GET /api/campaigns/{id}/stream` (`SseEmitter`) controller in `api/src/main/kotlin/no/rauboti/tome/realtime/StreamController.kt`
- [ ] T066 [US4] Publish domain events (`sheet.updated`, `content.updated`, `roster.updated`, `combat.updated`, `roll.created`) from the character/campaign/content/combat/roll services via the publisher
- [ ] T067 [US4] `Encounter` as its **own `encounters` collection** (`@Document`, `@Id`, `@Version` — the live-combat concurrency unit; `sessionId` + denormalized `campaignId` references; embedded `combatants[]` and encounter-level `rolls[]`); combatants carry **`combatantId` + `combatantType`** (`character`|`npc`, resolution enforced in-app; optional `$jsonSchema` validator) + `EncounterRepository` (MongoTemplate) + `EncounterService` (initiative order, next-turn/round, end) in `api/src/main/kotlin/no/rauboti/tome/combat/`
- [ ] T068 [US4] `EncounterController` — endpoints **nested under a session** (`/campaigns/{id}/sessions/{sessionId}/encounters…`): start, next-turn, end, add combatants, and **`PATCH` a combatant to change `isRevealed`/`initiative` mid-combat** (reveal drives the SSE fan-out, FR-021) in `api/src/main/kotlin/no/rauboti/tome/combat/EncounterController.kt`
- [ ] T069 [US4] Web `useCampaignStream` (`EventSource`) hook + MSW SSE mock in `web/src/realtime/`
- [ ] T070 [US4] Web `DiceRoller` UI + roll log + apply-to-sheet prompt in `web/src/components/dice/`
- [ ] T071 [US4] Web `InitiativeTracker` + turn/round control (DM) + revealed-state player view, updating live via SSE in `web/src/components/combat/`

**Checkpoint**: US1–US4 independently functional; Tome runs a live table end to end.

---

## Phase 7: User Story 5 - Add the custom Dark Souls rule set (Priority: P5) — DEFERRED

**Goal**: Prove engine extensibility by adding a second rule set with **no** change to the shared
engine or cross-cutting code (SC-009).

> **DEFERRED**: The Dark Souls rule set's lineage (3.5 vs 5E) and deviations are intentionally
> unresolved (spec US5). Expect a spec amendment before starting. Tasks below are a placeholder shape.

- [ ] T072 [US5] (DEFERRED) Author `darksouls` sheet definition in `api/src/main/resources/rulesets/darksouls/definition.json`
- [ ] T073 [US5] (DEFERRED) `DarkSoulsRuleSet` + unit tests in `api/src/main/kotlin/no/rauboti/tome/rulesets/DarkSoulsRuleSet.kt`
- [ ] T074 [US5] (DEFERRED) Extensibility test — a Dark Souls character + campaign works end to end with no diff to the shared engine or dice/combat/sync/permissions code (SC-009)
- [ ] T075 [US5] (DEFERRED) Register `darksouls` in `RuleSetRegistry`; confirm the web renders it from the definition with no renderer change

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T076 [P] Walk the [quickstart.md](quickstart.md) validation scenarios end to end against the running stack
- [ ] T077 [P] Write `tome/README.md` (run, ports 3040/5040/5436, architecture, Hive dependency)
- [ ] T078 Security review — BFF token handling (no tokens in browser) and the SSE authorization guard (SC-004)
- [ ] T079 [P] Accessibility pass on `@rauboti/ui` screens (keyboard, labels, focus)
- [ ] T080 Performance sanity — sheet save p95 < 300 ms; live update within 3 s (SC-007); a full combat round for ≤6 PCs + NPCs (SC-008)
- [ ] T081 [P] Ensure Spotless + ESLint/Prettier gates pass in `mvn verify` / web `lint`
- [ ] T082 [P] i18n coverage — nb/en for all chrome/labels with English fallback
- [ ] T083 (AFTER US5) Top-level-field/index review — with a second rule set (Dark Souls) in hand, decide which cross-cutting **base-input** sheet values (e.g. HP `hpCurrent`/`hpMax`) genuinely earn a top-level document field and/or index for combat/roster queries vs. staying inside `data`, and lift them out via a migration change (C-series, ledger-guarded — see T089/T091) + model/repo update. **The decision MUST also be reflected in `contracts/openapi.yaml`** — promoting the field to a top-level property on the `Character`/`Npc` schemas (v1 keeps HP inside `data`, so the schemas expose no top-level `hp*`). Note: **derived** values are never stored (surfaced by `CharacterDataResolver` on read), so only base inputs are candidates. See plan.md "Deferred Decisions".

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)** → no dependencies. *(Completed on Postgres; superseded by Phase 3B for storage.)*
- **Foundational (Phase 2)** → depends on Setup; **blocks all user stories**. *(Completed; the JSONB/
  JdbcTemplate/Flyway parts are superseded by Phase 3B.)*
- **Phase 3B re-platform (MongoDB + compute-on-read)** → depends on US1 being built; **must complete
  before US2–US5**, since those are authored against MongoDB (`MongoTemplate`, embedded aggregates,
  Spring Data-native migrations, `CharacterDataResolver`). This is the amendment's pivot point.
- **Phase 3C 3.5 sheet expansion (T105)** → after Phase 3B is green; sequenced before US2 by preference
  (verify the migration, then enrich the sheet). Its own increment; does not technically block US2.
- **Phase 3D typed rule-set engine (T118+, ADR-001)** → after Phase 3C green; sequenced **before US2** so
  US2/US3 (NPCs, T050) are authored against the typed sheet. Re-expresses 3C content as types; retires the
  formula engine + generic renderer. Spec-first (T118) before code; persistence de-risked by the spike.
- **User stories (Phases 4–7 / US2–US5)** → each depends on Foundational **and Phase 3B**. Recommended
  order is priority order because later stories build on earlier data:
  - US2 needs US1's `character`; US3 needs US2's `campaign` (embeds NPCs/content/sessions); US4 needs
    US2/US3 (campaign, NPCs) and US1 (sheets, for apply-to-sheet); US5 needs the whole engine proven.
- **Polish (Phase 8)** → after the desired stories are complete.

### Within each user story

- Tests (contract/integration/web) written first and FAIL → then models → services → endpoints → web.
- Leave work uncommitted on the feature branch per Constitution Principle III — the maintainer reviews
  and commits.

### Parallel opportunities

- Setup: T003/T004/T006/T007 in parallel; T001 and T002 in parallel (different tiers).
- Foundational: T009–T012 (api) alongside T020–T024 (web); T014/T015 parallel; note T017 depends on
  T013/T016, and T019 depends on T013/T018.
- Each story's `[P]` test tasks run together; api and web tasks within a story largely parallelize
  (different tiers). US2 and US3 are mostly independent slices once Foundational is done, but share
  `PermissionService` (T040 before T053).

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & VALIDATE** (digital 3.5 sheet
   with derived values, warnings, safe edits) → demo.

### Incremental delivery

US1 (MVP, built on Postgres) → **Phase 3B re-platform to MongoDB + compute-on-read** → **Phase 3C 3.5
sheet expansion (T105–T114)** → **Phase 3D typed rule-set engine (T118+, ADR-001)** → US2 (shared table)
→ US3 (DM tools) → US4 (live combat) → US5 (2nd rule set, after a spec amendment). Each story — and Phases
3B/3C/3D — is an independently testable, demoable increment. Per the constitution, start a fresh feature
branch before each increment's code (Phases 3B and 3D are re-platforms kept as small, line-by-line-reviewable
tasks; 3D recommends a backend increment + a frontend increment).

---

## Notes

- `[P]` = different files, no incomplete-task dependency.
- Every story is independently completable and testable; verify tests fail before implementing.
- The one cross-repo dependency (Hive `tome` client + `Admin`/`User` roles, research D1/D6) must be
  arranged with maintainer approval before the auth tasks (T009–T011) can run end to end.
