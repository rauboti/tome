# Tome Web (SPA)

The Tome frontend: a Vite + React 19 single-page app. It talks only to the Tome BFF under `/api` and
**never sees a Hive token** — the browser holds only a session cookie.

- **React 19 + TypeScript**, **Chakra UI v3** + [`@rauboti/ui`](https://github.com/rauboti) for the
  shell and primitives.
- **Zod** for runtime-validated API responses, **React Router 7**, **react-i18next** (bilingual nb/en).
- **Yarn 4** (Node ≥ 22), **Vitest** + React Testing Library + **MSW** for tests.

## Structure

```
src/
├── api/          Typed API clients (one per resource) + the shared fetch wrapper
│   ├── client.ts   apiRequest<T>: session cookie, Zod validation, 401/403 handling, ApiError
│   ├── schemas.ts  shared Zod schemas
│   ├── characters.ts / catalogs.ts
├── auth/         SessionContext, RequireAuth guard, Login / NoAccess screens
├── components/
│   ├── layout/     app shell / navbar (@rauboti/ui)
│   └── characters/ character list, create dialog, sheet view + tables
├── sheets/       dnd35.ts — the typed D&D 3.5 sheet (client mirror of the server types)
├── pages/        route-level screens (Characters, CharacterSheet, Campaigns)
├── i18n/         i18next setup + nb.json / en.json
├── mocks/        MSW handlers + fixtures (used by tests)
├── routes.tsx    route table
└── main.tsx      app entry
```

## Auth flow

The SPA assumes a **BFF session cookie** — it never handles OAuth directly:

- `client.ts` sends every request with `credentials: 'include'`.
- **401** (no session) → the wrapper redirects the browser to `/auth/login`, which the api forwards to
  Hive. The session-bootstrap probe opts out (`redirectOnUnauthorized: false`) so it can render a login
  screen instead.
- **403** (signed in, but no Tome role) → a global handler drops the app to the "no access" screen, no
  matter which data call surfaced it (FR-024). Registered via `setOnForbidden`.
- Non-2xx responses reject with an `ApiError` carrying the HTTP status and any RFC-7807 `problem+json`
  body.

## The typed sheet mirror

`sheets/dnd35.ts` is the **client mirror of the Kotlin `DnD35CharacterBaseData` / `DnD35CharacterData`
split**: base inputs (what an edit sends) vs. the enriched sheet (base + derived) a response returns.
`enrichDnD35` mirrors the server's `enrich()` so derived values update **live** while editing — but the
base inputs are the source of truth and derived values are never sent back. The types are hand-authored
to track the OpenAPI `Sheet`/`SheetInput` contract (no codegen wired yet).

## Develop, test, build

```bash
yarn dev            # Vite dev server on 5173, proxies /api to the api on 5040
yarn test           # Vitest
yarn lint           # ESLint
yarn format         # Prettier --write
yarn build          # tsc -b && vite build
```

For `yarn dev` to reach a backend, run the api first (from the repo root):

```bash
docker compose up tome-db tome-api
```

Building the web **Docker image** needs a GitHub Packages token for the private `@rauboti/ui` scope —
see `RAUBOTI_PACKAGE_TOKEN` in [`.env.example`](../.env.example) and `.yarnrc.yml`.

## Conventions

- **Every API response is validated with Zod** at the boundary (`apiRequest(path, schema)`); use
  `z.undefined()` for 204s.
- **Pure helpers stay pure and unit-tested** (sheet enrichment, formatting) — keep side effects in the
  api layer and components.
- UI is composed from **`@rauboti/ui` / Chakra** primitives before hand-rolling.
- User-facing strings go through **i18n** (`nb.json` / `en.json`); canonical game terminology may stay
  untranslated per the spec.
