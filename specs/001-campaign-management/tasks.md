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

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- Paths follow the platform two-tier layout from plan.md: `api/` (Kotlin/Spring Boot,
  `no.rauboti.tome`, JdbcTemplate) and `web/` (Vite/React 19/`@rauboti/ui`).

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
- [ ] T022 [P] Web i18n setup (i18next init, `nb.json`/`en.json`, English fallback) in `web/src/i18n/`
- [ ] T023 [P] Definition-driven `SheetRenderer` + field widgets (int/text/bool/select/list/derived) with a Vitest render test in `web/src/components/sheet/`
- [ ] T024 [P] Typed API client base + Zod schemas for auth & rule-sets in `web/src/api/`

**Checkpoint**: Auth, persistence, the sheet engine, and the web shell are ready.

---

## Phase 3: User Story 1 - Keep a character sheet digitally (Priority: P1) 🎯 MVP

**Goal**: A user creates a 3.5 character and maintains its full sheet digitally, with derived values,
soft warnings, and safe concurrent edits.

**Independent Test**: Create a character, fill/edit the sheet, reload → persists exactly; a rule
violation warns but still saves; a stale write returns 409.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [ ] T025 [P] [US1] Contract test for `/api/characters` (POST/GET/PUT/DELETE) against openapi in `api/src/test/kotlin/no/rauboti/tome/characters/CharacterContractTest.kt`
- [ ] T026 [P] [US1] Integration test (Testcontainers) — create→edit→reload persistence, derived values recomputed on write, soft warning returned without blocking, `409` on stale `version` in `api/src/test/kotlin/no/rauboti/tome/characters/CharacterIntegrationTest.kt`
- [ ] T027 [P] [US1] Web test — `SheetRenderer` edit flow, derived-value display, warning banner, version-conflict handling (Vitest/RTL/MSW) in `web/src/components/characters/CharacterSheet.test.tsx`

### Implementation for User Story 1

- [ ] T028 [US1] Migration `V1__create_character.sql` (jsonb `data`, promoted `name`/`rule_set_id`/`owner_id`/`hp_current`/`hp_max`, `version`, timestamps) in `api/src/main/resources/db/migration/`
- [ ] T029 [US1] `Character` model + `CharacterRepository` (JdbcTemplate, jsonb via T014) in `api/src/main/kotlin/no/rauboti/tome/characters/`
- [ ] T030 [US1] `CharacterService` — create/get/update/delete with `RuleSet.validate` + `computeDerived` and optimistic concurrency in `api/src/main/kotlin/no/rauboti/tome/characters/CharacterService.kt`
- [ ] T031 [US1] `CharacterController` REST endpoints in `api/src/main/kotlin/no/rauboti/tome/characters/CharacterController.kt`
- [ ] T032 [US1] Web characters API client + Zod schemas in `web/src/api/characters.ts`
- [ ] T033 [US1] Web `CharactersPage` (list + create dialog) in `web/src/pages/CharactersPage.tsx` and `web/src/components/characters/`
- [ ] T034 [US1] Web character sheet edit screen using `SheetRenderer` (save/version handling, warnings) in `web/src/components/characters/CharacterSheet.tsx`

**Checkpoint**: US1 is a fully functional, independently testable MVP.

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

- [ ] T038 [US2] Migration `V2__create_campaign_membership.sql` (campaign; membership with `UNIQUE(campaign_id,character_id)` and `UNIQUE(character_id)`) in `api/src/main/resources/db/migration/`
- [ ] T039 [US2] `Campaign` + `Membership` models + repositories (JdbcTemplate) in `api/src/main/kotlin/no/rauboti/tome/campaigns/`
- [ ] T040 [US2] `PermissionService` — campaign-scoped visibility (DM full; player self + shared; deny others) in `api/src/main/kotlin/no/rauboti/tome/campaigns/PermissionService.kt`
- [ ] T041 [US2] `CampaignService` — create, archive, roster add (enforce `character.ruleSet == campaign.ruleSet`, FR-008), remove (keep character); **a DM MAY add a self-owned character to their own campaign (FR-017) — this creates an ordinary membership and MUST NOT grant that player DM visibility nor let the DM hide content from themselves** in `api/src/main/kotlin/no/rauboti/tome/campaigns/CampaignService.kt`
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

- [ ] T049 [US3] Migrations `V3__create_npc.sql`, `V4__create_content.sql`, `V5__create_session.sql` in `api/src/main/resources/db/migration/`
- [ ] T050 [US3] `Npc` model/repo/service/controller (reuses the sheet engine) in `api/src/main/kotlin/no/rauboti/tome/npcs/`
- [ ] T051 [US3] `Content` model/repo/service/controller (visibility private/shared) in `api/src/main/kotlin/no/rauboti/tome/content/`
- [ ] T052 [US3] `Session` model/repo/service/controller in `api/src/main/kotlin/no/rauboti/tome/sessions/`
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
- [ ] T062 [US4] Migrations `V6__create_encounter_combatant.sql`, `V7__create_roll.sql` in `api/src/main/resources/db/migration/`
- [ ] T063 [US4] Roll recording + optional apply-to-sheet (through the `CharacterService`/NPC write path so warnings apply) + `POST /api/rolls` controller in `api/src/main/kotlin/no/rauboti/tome/dice/`
- [ ] T064 [US4] SSE infrastructure — emitter registry + `AuthorizedEventPublisher` (reuses `PermissionService`) + async config in `api/src/main/kotlin/no/rauboti/tome/realtime/`
- [ ] T065 [US4] `GET /api/campaigns/{id}/stream` (`SseEmitter`) controller in `api/src/main/kotlin/no/rauboti/tome/realtime/StreamController.kt`
- [ ] T066 [US4] Publish domain events (`sheet.updated`, `content.updated`, `roster.updated`, `combat.updated`, `roll.created`) from the character/campaign/content/combat/roll services via the publisher
- [ ] T067 [US4] `Encounter`/`Combatant` models/repos + `EncounterService` (initiative order, next-turn/round, end) in `api/src/main/kotlin/no/rauboti/tome/combat/`
- [ ] T068 [US4] `EncounterController` — start, next-turn, end, add combatants, and **`PATCH` a combatant to change `isRevealed`/`initiative` mid-combat** (reveal drives the SSE fan-out, FR-021) in `api/src/main/kotlin/no/rauboti/tome/combat/EncounterController.kt`
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

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)** → no dependencies.
- **Foundational (Phase 2)** → depends on Setup; **blocks all user stories**.
- **User stories (Phases 3–7)** → each depends on Foundational. Recommended order is priority order
  (US1 → US2 → US3 → US4 → US5) because later stories build on earlier data:
  - US2 needs US1's `character`; US3 needs US2's `campaign`; US4 needs US2/US3 (campaign, NPCs) and
    US1 (sheets, for apply-to-sheet); US5 needs the whole engine proven.
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

US1 (MVP) → US2 (shared table) → US3 (DM tools) → US4 (live combat) → US5 (2nd rule set, after a spec
amendment). Each story is an independently testable, demoable increment. Per the constitution, start a
fresh feature branch before each increment's code.

---

## Notes

- `[P]` = different files, no incomplete-task dependency.
- Every story is independently completable and testable; verify tests fail before implementing.
- The one cross-repo dependency (Hive `tome` client + `Admin`/`User` roles, research D1/D6) must be
  arranged with maintainer approval before the auth tasks (T009–T011) can run end to end.
