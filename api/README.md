# Tome API (BFF)

The Tome backend: a Kotlin + Spring Boot **Backend-for-Frontend**. It owns all campaign data in
MongoDB, brokers the Hive OAuth login, and serves the SPA under `/api`. Package root
`no.rauboti.tome`.

- **Kotlin on JDK 25**, Spring Boot **4.1** (Jackson 3 / `tools.jackson`).
- **Persistence: MongoDB via `MongoTemplate`** — no JPA, mirroring the platform's `JdbcTemplate`
  convention.
- **Build: Maven** via the `mvnw` wrapper. Formatting: Spotless driving the ktlint engine off the
  repo-root `.editorconfig`.

## Module map

```
no.rauboti.tome/
├── auth/         Hive OAuth client + BFF session (login/callback, /api/auth/me, logout, PKCE)
├── config/       Security filter chain, JWT validation, CORS, Mongo config
│   └── migration/  Spring Data-native index catalog + applied-changes ledger (no framework)
├── characters/   Character document, MongoTemplate repo, service, controller
│   └── data/       Typed sheet hierarchy: CharacterBaseData / CharacterData (+ DnD35, DarkSouls)
├── rulesets/     RuleSet strategy interface + DnD35RuleSet; identity + soft validation
├── catalogs/     Catalog-backed option lookups (e.g. the D&D 3.5 spell catalog)
└── common/       RFC-7807 error handling, shared API exceptions
```

## Auth model (BFF)

Tome is a **consumer** of Hive, not its own identity provider:

- The `/auth/login` + `/auth/callback` handshake runs the OAuth Authorization-Code + PKCE dance
  against Hive and **starts a server-side session**. The Hive token lives in that session — it never
  reaches the browser, which holds only a session cookie.
- On every `/api` request, `SessionTokenAuthenticationFilter` reads the session token, decodes it as
  a Hive-issued RS256 JWT (validated offline against Hive's JWKS), and refreshes it silently on expiry.
  The `SecurityContext` is stateless — rebuilt per request, never persisted.
- **Authorization:** `/api/**` (including `/api/auth/me`) requires a Tome app role (`admin` or `user`)
  from the token's `roles` claim. A signed-in Hive user *without* a Tome grant gets a **403** (FR-024).
  `POST /api/auth/logout` needs only a valid session; the login handshake and `/actuator/health` are
  public. Unauthenticated `/api` calls get a plain **401** (no redirect) that the SPA turns into a Hive
  login.

Two Hive base URLs matter: **external** (how the browser reaches Hive; also the expected token `iss`)
and **internal** (how the api container reaches Hive for token exchange + JWKS). See `SecurityConfig`
and [`.env.example`](../.env.example).

## Persistence & the rule-set engine

- **Sheets store base inputs only.** A stored character holds its raw, player-entered values as a
  native BSON sub-document. **Derived values are computed on read** as plain Kotlin properties
  (`CharacterBaseData.enrich()` → the enriched `CharacterData`), so a derived value cannot be stored
  and cannot drift ([ADR-001](../specs/001-campaign-management/decisions/ADR-001-typed-ruleset-sheets.md)).
- **Typed, code-first rule sets.** A sheet is a typed member of a sealed hierarchy, bound
  polymorphically on the `ruleSetId` discriminator — not an untyped map + JSON definition (the old
  data-driven `SheetData`/`SheetDefinition` and the formula engine were retired in T125). A `RuleSet`
  strategy supplies only **identity + soft validation** (`RuleWarning`s — guidance the DM can always
  override, never a hard block). v1 ships `DnD35RuleSet`.
- **Migrations are Spring Data-native.** An index catalog is ensured on boot and applied changes are
  recorded in a `_migrations` ledger (`config/migration/`, changes named `C001…`). No migration
  framework — Mongock is deprecated and Flamingock is Gradle-only.
- **MongoDB runs as a single-node replica set**, required for multi-document transactions and
  `@Version` optimistic concurrency.

## Build, test, run

```bash
./mvnw verify          # compile, test, and run the Spotless check (the CI gate)
./mvnw test            # tests only
./mvnw spotless:apply  # auto-format Kotlin
```

Run the api locally against a containerised db (from the repo root):

```bash
docker compose up tome-db tome-api
```

**Testing** — JUnit 5, MockMvc + `spring-security-test`, MockK. Integration tests run against a real
MongoDB via Testcontainers (`MongoDBContainer`, auto-started as a single-node replica set, wired with
`@ServiceConnection`). Contract tests assert conformance to `contracts/openapi.yaml`. Rule-set logic
is pure Kotlin with dense unit tests.

> **Caveat (see project memory):** `@ServiceConnection` bypasses the real connection URI, so green
> tests don't prove the deploy config is correct — boot the compose stack to verify that.

## Conventions

- **`MongoTemplate`, not JPA** — explicit queries, mirroring the sibling apps' `JdbcTemplate`
  discipline.
- The **OpenAPI contract** (`specs/001-campaign-management/contracts/openapi.yaml`) is the source of
  truth for the wire shape; the web codegens/hand-mirrors from it.
- Comment rationale references trace to the spec: `FR-xxx` (functional requirements), `Txxx` (tasks),
  `Dx` (research decisions), `ADR-xxx` (decision records).
