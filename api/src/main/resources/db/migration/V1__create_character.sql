-- Player character: the paper-sheet replacement (US1, data-model.md). The sheet values live in
-- `data` (jsonb, shaped by the rule set's definition); a couple of cross-cutting values are promoted
-- to columns for lists/rosters (`name`, `rule_set_id`, `user_id`). HP currently stays inside `data`
-- (the dnd35 definition already carries hpMax/hpCurrent) — which values earn a promoted column is
-- deferred until a second rule set exists to generalize from (see the review task after US5).
-- `version` drives optimistic concurrency: a write must carry the version it read, and the service
-- bumps it (SET version = version + 1 WHERE id = ? AND version = ?); a stale write matches no row
-- and becomes a 409 rather than silently overwriting a concurrent edit (SC-006). Forward-only.
--
-- Table is named `characters` (plural): `character` is a reserved word in Postgres, so the singular
-- would need quoting everywhere. The Kotlin model is `Character`, repo maps to this table.
create table characters (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null,
    rule_set_id text        not null,
    name        text        not null,
    data        jsonb       not null default '{}'::jsonb,
    version     integer     not null default 0,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    -- Non-blank display name, promoted from the sheet (1–100 chars).
    constraint characters_name_check check (char_length(btrim(name)) between 1 and 100)
);

-- GET /api/characters lists the caller's own characters (user_id = the owning user's Hive subject,
-- a UUID).
create index idx_characters_user on characters (user_id);
