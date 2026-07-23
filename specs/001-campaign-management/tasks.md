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
- [X] T091 Migration change `C001` (Spring Data-native) — create `characters` collection + ensure index `{ userId: 1 }` via `MongoTemplate`/`IndexOperations`, idempotent and recorded in the `_migrations` ledger, in `api/src/main/kotlin/no/rauboti/tome/config/migration/C001__CreateCharacters.kt` (a plain change unit invoked by the T089 `MigrationRunner`, not a framework changelog). **Lands with a focused `MigrationRunnerTest` on the `IntegrationTest` base** asserting the change applies once and a second run is a no-op (ledger guard) + the `{userId:1}` index exists. **Impl notes / naming convention:** migration classes use `C<order>__<Name>` (e.g. `C001__CreateCharacters`) — Flyway/Flamingock-style for readability, which needs a one-line `@file:Suppress("ktlint:standard:class-naming")` per migration file (the shared repo-root `.editorconfig` can't be relaxed per the platform ADR). **Execution order comes from the `id` field** (ledger key `C001`), NOT class-name parsing, so the name is purely descriptive. `ensureIndex` (not deprecated in Spring Data 5.1) on the `userId` field (data-model + `Character.userId`). **Verified:** APIs via `javap`, Spotless/ktlint clean; the test is **not yet executed** (module red until T093–T098) — it runs green at T100/T103 alongside the re-enabled Character tests.
- [X] T092 Retire JSONB glue — delete `api/src/main/kotlin/no/rauboti/tome/common/JsonbSupport.kt` **and its unit test `api/src/test/kotlin/no/rauboti/tome/common/JsonbSupportTest.kt`** (BSON is native — the test's subject is gone) and remove `api/src/main/resources/db/migration/V1__create_character.sql` + the now-empty `db/migration/` dir. **Impl note:** also removed the dir's `.gitkeep` and the now-empty parent `resources/db/` tree; no config references the removed SQL/seed (Flyway config already dropped in T087). Residual `JsonbSupport` references remain in `CharacterRepository.kt` (rewritten T094) and a stale comment in `RuleSet.kt` (T104 sweep) — module stays red until T093–T098, as expected.

### Character slice on MongoDB + compute-on-read

- [X] T093 [US1] Rewrite `Character` as a Mongo document — `@Document("characters")`, `@Id` UUID, `@Version` int, `data` = base inputs only (drop the promoted-column shape) in `api/src/main/kotlin/no/rauboti/tome/characters/Character.kt`. **Impl notes:** kept top-level `id/userId/ruleSetId/name/data/version/createdAt/updatedAt` (data-model §characters). `@Version` typed **`Int?` (nullable)** — spec says "int", nullable so the T094 repo can distinguish a new doc (`null`) from an existing one (Spring assigns `0` on insert, increments on save). `createdAt`/`updatedAt` stay plain service-set `Instant`s (no Spring auditing added — matches the prior manual approach; revisit if desired). **Verified:** annotation packages `javap`-checked (`org.springframework.data.annotation.Id`/`Version`, `@Document.collection`), Spotless/ktlint clean; module still red until the repo/service/controller are rewritten (T094–T098).
- [X] T094 [US1] Rewrite `CharacterRepository` — `MongoTemplate` insert/findById/findByUserId/save/delete; optimistic concurrency via `@Version` (no hand-rolled `WHERE version = ?`) in `.../characters/CharacterRepository.kt`. **Impl notes:** entity-based methods (`insert(Character)`/`save(Character)`) — `save()` issues the `@Version` versioned update and throws `OptimisticLockingFailureException` on a stale version (service maps → 409, T096/T098); no `WHERE version`. Unlike the old JDBC repo (DB-generated id/version/timestamps via `RETURNING`), **id + createdAt/updatedAt are now assigned by the service** (T096) and `@Version` starts at 0 on insert. `findByUserId` sorts `createdAt` desc; `deleteById` uses `remove(Query(where("id").is(id)))` → `deletedCount > 0`. **Verified:** `MongoTemplate`/`DeleteResult`/`Sort` APIs via `javap`, Spotless/ktlint clean. Module still red — `CharacterService`/`Controller` use the old repo API + Character shape until T096/T097.
- [ ] T095 [P] [US1] Add the character resolve-on-read helper — `CharacterDataResolver.resolve(data, ruleSet)` = base inputs + `RuleSet.computeDerived`, the single home of character compute-on-read (D8), in `api/src/main/kotlin/no/rauboti/tome/characters/CharacterDataResolver.kt`. **Entity-scoped by design** — NPC and other entities get analogous resolvers later; extract a shared core only if duplication warrants. **Lands with a focused unit test** `api/src/test/kotlin/no/rauboti/tome/characters/CharacterDataResolverTest.kt` (pure, no container) asserting resolve = base inputs + `RuleSet.computeDerived`, inputs preserved, derived recomputed — new code + its test together. **Impl notes:** `@Component`, `resolve(data, ruleSet) = data + ruleSet.computeDerived(data)` — recomputed values win the merge, so stale stored derived (if any leaked in) is overwritten on read; robust whether a rule set's `computeDerived` returns only-derived or base+derived (DnD35's returns base+derived). Test uses a **stub `RuleSet`** to isolate the resolver's merge/preserve/recompute contract (DnD35 formulas stay covered by `DnD35RuleSetTest`). Spotless/ktlint clean; the pure test can't execute until the module compiles (T096/T097) — runs then.
- [ ] T096 [US1] Rewrite `CharacterService` — on write: `validate` + **strip fields the definition marks `derived`** before persisting; on read/echo: return the resolved sheet via `CharacterDataResolver`; map `OptimisticLockingFailureException` → 409 in `.../characters/CharacterService.kt`
- [ ] T097 [US1] Update `CharacterController` — GET and the POST/PUT echo return the resolved sheet (base + derived) in `.../characters/CharacterController.kt`
- [ ] T098 [US1] Map Spring Data `OptimisticLockingFailureException` → RFC-7807 `409` (replacing/adapting the `StaleVersionException` mapping) in `api/src/main/kotlin/no/rauboti/tome/common/`

### Frontend alignment (compute-on-read)

- [ ] T099 [P] [US1] Align the web sheet with compute-on-read in `web/src/components/characters/CharacterSheet.tsx` + `web/src/api/characters.ts` — derive locally on change for instant feedback, treat the server's resolved response as authoritative on load/save, don't send derived fields, don't assume stored data carries them

### Re-enable / rewrite tests incrementally

- [ ] T100 [US1] Re-enable + rewrite `CharacterIntegrationTest` (stays on the `IntegrationTest` base, now Mongo-backed via T090; here rewrite the body + drop `@Disabled`) — create→edit→reload persists base inputs; response carries resolved derived values; **assert the raw stored document has no derived fields**; soft warning without blocking; `409` on stale `@Version`
- [ ] T101 [US1] Re-enable + rewrite `CharacterContractTest` (stays on the `IntegrationTest` base, now Mongo-backed; rewrite body + drop `@Disabled`) against the Mongo-backed context (openapi shapes unchanged)
- [ ] T102 [P] [US1] Confirm/adjust the web `CharacterSheet.test.tsx` for compute-on-read (server-authoritative resolved sheet; MSW responses carry derived values)

### Green + cleanup

- [ ] T103 Confirm no `@Disabled` quarantine annotations remain (dropped in T100/T101) and no Postgres-bound test artifacts survive; `./mvnw verify` (unit + Mongo integration + Spotless) and web `yarn test` both green
- [ ] T104 **Final safety sweep** — grep the api module for any *residual* Postgres/Flyway/JdbcTemplate/JSONB references (imports, config, comments; the bulk removal already happened in T090/T092) and clear stragglers; confirm `docker compose up --build` boots the Mongo stack and US1 works end to end

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

- [ ] T105 Expand the **D&D 3.5 sheet** to a fuller character sheet — deepen `api/src/main/resources/rulesets/dnd35/definition.json` + `DnD35RuleSet.computeDerived`/`validate` (and the web `SheetRenderer` input widgets) beyond the current skeleton (identity, 6 abilities+mods, combat, 3 saves, freeform skills/feats/gear/languages/notes). Candidates: **structured skills** (ranks / class-skill / ability / misc → total), **structured feats**, a **weapons/attacks** table, an **AC breakdown** (armor+shield+dex+size+natural+deflection+dodge → total), **encumbrance/grapple/CMB**, and **spellcasting** if in v1 scope. Keep the base-inputs-only + derived-on-read split (D8): entered values live in `data`, computed values are added by `computeDerived` on read, **never stored**. **No change to `Character`/storage/`CharacterRepository`** — the data-driven Hybrid design (research D3) means this is a definition + rule-set + renderer change only. **Spec first:** run `/speckit-clarify` on sheet depth (structured vs freeform per section; how far the derived AC/attack/spell math goes for v1) before implementing; any base-input value promoted to a top-level document field cross-refs **T083** + `contracts/openapi.yaml`. Captured 2026-07-23 during T093 (the untyped `data` map made clear the sheet lives here, not on the document).

**Checkpoint**: the 3.5 sheet is as rich as v1 wants; US1 still green; US2 can begin.

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
- [ ] T050 [US3] `Npc` embedded model + service/controller (reuses the sheet engine + an NPC-side resolver analogous to `CharacterDataResolver` — compute-on-read, base inputs only; extract a shared core if it duplicates the character one) operating on `campaign.npcs[]` via `CampaignRepository` array updates — no separate repo/collection — in `api/src/main/kotlin/no/rauboti/tome/npcs/`
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
sheet expansion (T105)** → US2 (shared table) → US3 (DM tools) → US4 (live combat) → US5 (2nd rule set,
after a spec amendment). Each story — and Phases 3B/3C — is an independently testable, demoable increment. Per the constitution, start a fresh
feature branch before each increment's code (Phase 3B is one increment; its tasks are kept small for
line-by-line review).

---

## Notes

- `[P]` = different files, no incomplete-task dependency.
- Every story is independently completable and testable; verify tests fail before implementing.
- The one cross-repo dependency (Hive `tome` client + `Admin`/`User` roles, research D1/D6) must be
  arranged with maintainer approval before the auth tasks (T009–T011) can run end to end.
