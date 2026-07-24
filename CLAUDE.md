<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
`specs/001-campaign-management/plan.md`
<!-- SPECKIT END -->

Active feature: **Campaign & Character Management** (`specs/001-campaign-management/`).
Tome helps a dungeon master run a tabletop RPG campaign — digital character sheets, campaigns with a
DM view and limited player view, NPCs, live sessions, and combat with dice + initiative. v1 ships
**D&D 3.5 only** via a Hybrid rule-set engine (data-driven sheet definitions + per-ruleset logic);
Dark Souls is a later story (US5), 5E deferred. Stack: Kotlin + Spring Boot 4.1 BFF (`api/`,
`no.rauboti.tome`, `MongoTemplate` — no JPA) + Vite/React 19/Chakra + `@rauboti/ui` (`web/`), MongoDB
(single-node replica set; sheets as BSON documents holding base inputs only, derived values computed
on read; migrations Spring Data-native — index catalog + ledger on boot, no framework), auth delegated to Hive (BFF, roles Admin/User), real-time via
Server-Sent Events, bilingual nb/en. Ports 3040/5040/5436. **Persistence switched Postgres→MongoDB and
adopted compute-on-read derived values on 2026-07-22 (amendment, post-US1) — see plan.md.** See
plan.md, research.md, data-model.md, contracts/openapi.yaml, and quickstart.md in the feature
directory. Project principles: `.specify/memory/constitution.md`.

## Git workflow (non-negotiable)

Every implementation task gets a FRESH feature branch off `main`, created BEFORE any
code (an increment = ONE task; never reuse the previous task's branch). Agents never
commit, push, or merge — leave work uncommitted; Gaute reviews, commits, and merges
each task before the next. If prior work is uncommitted when a new task starts,
surface it and ask. Preflight every implement task: create the fresh branch off `main`
and **record it** (`echo <branch> > .git/tome-active-increment`), then state the current
branch, the fresh branch created, and tree state. Enforced by
`.claude/hooks/block-main-edits.js`: Write/Edit/NotebookEdit to implementation files is
denied unless the current branch is a non-`main` branch **matching the recorded active
increment** — so forgetting to branch (or reusing the previous task's branch) is a hard
block, not a silent slip. It **fails closed** if git state can't be resolved
(mid-merge/index-lock/detached HEAD), denying implementation edits until the tree settles.
`specs/`, `.specify/`, `.claude/`, `CLAUDE.md` are always exempt (editable on any branch,
even when git is mid-operation).
