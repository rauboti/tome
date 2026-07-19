# Feature Specification: Campaign & Character Management

**Feature Branch**: `001-campaign-management`

**Created**: 2026-07-19

**Status**: Draft

**Input**: User description: "Tome helps a dungeon master track a role playing campaign, during and between sessions. It holds the data about the campaign and participating players to the point where it can replace the physical paper character sheets — both for players (a limited view) and for the dungeon master (who controls NPCs and optionally a player character of their own). It supports several rule sets (D&D 3.5, D&D 5E, and a custom Dark Souls adaptation), and a character created for one rule set can only join a campaign of the same rule set."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Keep a character sheet digitally (Priority: P1)

A tabletop role-playing participant creates a character for a chosen rule set and maintains its full
sheet — abilities, stats, hit points, inventory, skills, features — in Tome instead of on paper.
They can view and update the sheet as the character changes (takes damage, gains equipment, levels
up) and return to the current state on any device.

**Why this priority**: The core promise of Tome is replacing the paper character sheet. A single
accurate, editable digital sheet delivers standalone value even before campaigns exist — one person
can track one character. Everything else builds on the character-sheet foundation.

**Independent Test**: Create a character under a specific rule set, populate its sheet, edit several
values (e.g. reduce hit points, add an item), reload, and confirm the sheet persists exactly as
left. Fully testable with a single user and no campaign.

**Acceptance Scenarios**:

1. **Given** a signed-in user, **When** they create a character and select a rule set, **Then** the
   character sheet presents the fields defined by that rule set and is saved to their account.
2. **Given** an existing character sheet, **When** the owner edits a value and saves, **Then** the
   change persists and is visible on the next load.
3. **Given** a rule set that defines derived values (e.g. an ability modifier from an ability score),
   **When** the underlying value changes, **Then** the derived value updates consistently with that
   rule set's rules.
4. **Given** a user with multiple characters across different rule sets, **When** they open their
   character list, **Then** each character shows its name and rule set and opens its own sheet.

---

### User Story 2 - Run a campaign and build its roster (Priority: P2)

A dungeon master (DM) creates a campaign bound to one rule set. Players who already have accounts and
have created characters of that rule set are added to the campaign by the DM. The DM sees the full
campaign roster and every participating character; each player sees only their own character sheet
plus information the DM has chosen to share with the group.

**Why this priority**: A campaign turns individual sheets into a shared table. It introduces the two
distinct audiences the product is defined by — the all-seeing DM and the limited-view player — and
enforces the rule that a character can only belong to a campaign of its own rule set. It depends on
P1.

**Independent Test**: A DM creates a campaign of the campaign's rule set, a player joins with a
matching-rule-set character (succeeds); verify the DM sees that character while a second player cannot
see the first player's private sheet. (The mismatch-refusal path becomes fully exercisable once a
second rule set exists — see User Story 5; within v1's single rule set it is covered by validation
tests rather than an end-to-end join attempt.)

**Acceptance Scenarios**:

1. **Given** a signed-in DM, **When** they create a campaign and choose a rule set, **Then** the
   campaign is created bound to that rule set with the creator as its DM.
2. **Given** a campaign of rule set X, **When** the DM adds a player's character of rule set X,
   **Then** the character is added to the campaign roster and its owner participates as a player.
3. **Given** a campaign of rule set X, **When** the DM attempts to add a character of a different rule
   set, **Then** the system refuses the addition and explains the rule-set mismatch.
4. **Given** a campaign with several players, **When** a player opens the campaign, **Then** they see
   their own character sheet and shared campaign information but not another player's private sheet.
5. **Given** a campaign with several players, **When** the DM opens the campaign, **Then** they see
   the full roster and every participating character sheet.

---

### User Story 3 - Track sessions and control NPCs (Priority: P3)

During play and between sessions, the DM manages the campaign's non-player characters (NPCs) and
records what happens. The DM can create and maintain NPC sheets under the campaign's rule set, keep
private notes not visible to players, share select information with the table, and optionally run a
player character of their own alongside the NPCs. Session records let the DM prepare before a session
and pick up campaign state afterward.

**Why this priority**: This completes the DM's toolkit — the reason a DM adopts Tome for a whole
campaign rather than a single sheet. It depends on P2's campaign structure and P1's sheet model, so
it comes last while still being independently demonstrable.

**Independent Test**: Within an existing campaign, the DM creates two NPCs and a private note,
optionally adds their own player character, records a session, and confirms players cannot see the
NPCs' hidden data or the private note while shared information is visible to them.

**Acceptance Scenarios**:

1. **Given** a campaign, **When** the DM creates an NPC, **Then** the NPC uses the campaign's rule set
   and is controlled solely by the DM.
2. **Given** an NPC or note marked private, **When** a player views the campaign, **Then** that
   private content is not visible to the player.
3. **Given** information the DM marks as shared, **When** players view the campaign, **Then** the
   shared information is visible to all players in the campaign.
4. **Given** a DM who wants to also play, **When** they add a player character to their own campaign,
   **Then** they control that character in addition to the campaign's NPCs.
5. **Given** an ongoing campaign, **When** the DM records or reopens a session, **Then** the session's
   recorded state is available for preparation before and continuation after play.

---

### User Story 4 - Run live combat with dice and initiative (Priority: P4)

During a session, the DM runs an encounter using an in-app initiative tracker and dice roller. Tome
orders the participating characters and NPCs by initiative, tracks whose turn it is and the round,
and lets the DM advance turns. Rolls are made in-app and their results can be applied to sheets (e.g.
damage to hit points), with Tome warning on rule violations but letting the DM decide. Players at the
table see the combat state the DM reveals, updating live as the DM runs the encounter.

**Why this priority**: Live combat is the most involved "during a session" capability and turns Tome
into a virtual tabletop. It depends on P3's NPCs/sessions, P2's campaign/roster, and P1's sheets, so
it is sequenced last while remaining an independently demonstrable slice.

**Independent Test**: In an existing campaign with characters and NPCs, the DM starts an encounter,
Tome orders combatants by initiative, the DM rolls damage in-app and applies it to an NPC's hit
points (seeing a warning if the value goes out of range), advances turns through a round, and a
watching player sees the revealed combat state update live.

**Acceptance Scenarios**:

1. **Given** a campaign with characters and NPCs, **When** the DM starts an encounter, **Then** Tome
   lists the combatants ordered by initiative and marks whose turn it is.
2. **Given** an active encounter, **When** the DM advances the turn, **Then** the current turn and
   round update and are reflected live to authorized viewers.
3. **Given** an active encounter, **When** the DM rolls in-app and applies the result to a sheet value,
   **Then** the value updates and a warning is shown if the change violates the rule set, without
   blocking the DM.
4. **Given** an active encounter, **When** the DM reveals combat state to the table, **Then** players
   see the revealed state update in real time while hidden state stays hidden.

---

### User Story 5 - Add the custom Dark Souls rule set (Priority: P5)

Once the platform works end to end with D&D 3.5, a second rule set — the custom Dark Souls adaptation
— is added to prove and exercise the shared engine's extensibility. It is introduced by supplying its
sheet definition and rule-set logic, without altering the shared engine or the dice/combat/sync/
permissions capabilities. Players can then create Dark Souls characters and join Dark Souls campaigns,
with the same cross-ruleset join rule (a Dark Souls character cannot join a 3.5 campaign, and vice
versa).

**Why this priority**: This is the first real test of the Hybrid model (FR-001) and is deliberately
sequenced after the core product is proven with a single rule set. Its detailed design — including
whether it derives from D&D 3.5 or 5E and where it deviates — is intentionally deferred and will be
explored (and this spec amended if needed) when this story is picked up.

**Independent Test**: With the 3.5-based product working, add the Dark Souls rule set's definition and
logic only; verify a user can create a Dark Souls character and run a Dark Souls campaign through live
play, that no shared-engine or cross-cutting code changed, and that cross-ruleset joins are refused.

**Acceptance Scenarios**:

1. **Given** the shared sheet engine, **When** the Dark Souls rule set's definition and logic are
   supplied, **Then** Dark Souls characters and campaigns become available with no change to the
   shared engine or the dice/combat/sync/permissions capabilities.
2. **Given** a Dark Souls campaign, **When** a player joins with a Dark Souls character, **Then** the
   join succeeds; **When** they attempt to join with a 3.5 character, **Then** it is refused.

> **Note (deferred detail)**: The Dark Souls rule set's base lineage (3.5 vs 5E) and its specific
> deviations are open and will be specified when this story is planned; expect an amendment to this
> spec at that point.

---

### Edge Cases

- A user attempts to add a character to a campaign whose rule set differs from the character's rule
  set → the join is refused with a clear explanation (core constraint).
- A player leaves or is removed from a campaign → their character is detached from the campaign; the
  character itself remains owned by the player.
- The DM deletes or archives a campaign that has active players and characters → players' characters
  survive; only campaign membership ends.
- Two people edit the same sheet at the same time (e.g. DM and player on a shared character) →
  concurrent edits must not silently overwrite each other's data.
- A character built for a rule set whose definition is later updated → existing sheets remain valid
  and readable.
- A player tries to view or edit a character they do not own and that is not shared with them → the
  access is denied.
- A DM who also plays a character must still not be able to hide content from themselves, and players
  must not gain DM visibility because the DM also holds a player character.

## Requirements *(mandatory)*

### Functional Requirements

**Rule sets**

- **FR-001**: System MUST support multiple rule sets through a shared sheet engine: each rule set's
  sheet structure (its sections and fields) is expressed as a definition the engine reads, while
  rule-set-specific derived-value and validation logic is provided per rule set. Cross-cutting
  capabilities (character/sheet storage, permissions, real-time sync, dice, combat/initiative) MUST
  operate over any rule set's sheet without being reimplemented per rule set.
- **FR-002**: System MUST record the rule set of every character, NPC, and campaign, and MUST treat
  the rule set as fixed for the life of that character or campaign.
- **FR-023**: v1 MUST ship with D&D 3.5 as the only bundled rule set. The system MUST allow further
  rule sets to be added later — by supplying a sheet definition plus the rule set's validation/derived
  logic — without changing the shared sheet engine or the cross-cutting capabilities in FR-001.

**Characters & sheets**

- **FR-003**: Users MUST be able to create a character for a chosen rule set and maintain its full
  sheet (the fields that rule set defines, e.g. abilities, hit points, skills, inventory, features).
- **FR-004**: System MUST persist every saved change to a character sheet and present the current
  state on subsequent access from any device.
- **FR-005**: System MUST compute and keep consistent the derived values a rule set defines from other
  sheet values (e.g. an ability modifier from an ability score), and MUST warn the editor when a
  change violates that rule set's rules (e.g. an illegal level-up choice, or spending more of a
  limited resource than available) while allowing the dungeon master to override the warning and
  proceed. Tome guides; it does not overrule the DM.
- **FR-006**: Users MUST be able to own multiple characters across different rule sets and see them in
  a single list identifying each character's name and rule set.

**Campaigns & membership**

- **FR-007**: Users MUST be able to create a campaign bound to exactly one rule set, becoming that
  campaign's dungeon master.
- **FR-008**: System MUST enforce that every character added to a campaign shares the campaign's rule
  set, refusing the addition with a clear reason on mismatch.
- **FR-009**: In v1 the DM MUST be able to add existing characters (created by their owners) to the
  campaign roster and remove them, without deleting the underlying characters; player self-service
  joining (e.g. invite links) is deferred (see Assumptions).
- **FR-024**: Access to Tome MUST require a platform (Hive) account carrying a Tome role — **Admin** or
  **User** — provisioned by a Hive administrator; users without a Tome role MUST NOT be able to use
  Tome. (The per-campaign DM/player distinction is separate from these platform-wide roles.)
- **FR-010**: System MUST allow a campaign to be archived or closed while preserving each member's
  characters.

**Views & permissions**

- **FR-011**: System MUST present a limited player view showing a player their own character sheet and
  information the DM has shared, and MUST NOT expose another player's private sheet or the DM's hidden
  content to that player.
- **FR-012**: System MUST present a full DM view of the campaign, including the complete roster, every
  participating character, all NPCs, and private DM content.
- **FR-013**: System MUST allow the DM to mark campaign content as shared (visible to all players) or
  private (visible only to the DM).
- **FR-014**: System MUST deny any attempt to view or edit a character or campaign content the
  requester neither owns nor has been granted access to.

**Dungeon master tools**

- **FR-015**: DMs MUST be able to create and maintain NPCs under the campaign's rule set, controlled
  solely by the DM.
- **FR-016**: DMs MUST be able to keep private content (notes) attached to a campaign that is never
  visible to players.
- **FR-017**: DMs MUST be able to optionally run a player character of their own within a campaign
  they run, in addition to controlling the campaign's NPCs, without granting players DM visibility.
- **FR-018**: DMs MUST be able to record sessions so that campaign state can be prepared before a
  session and continued after it.

**Live table & play aids**

- **FR-019**: During an active session, System MUST propagate updates to content a participant is
  authorized to see (e.g. shared state, a player's own sheet, revealed combat state) to that
  participant in real time, without requiring a manual refresh.
- **FR-020**: System MUST provide an in-app dice roller that reflects the active rule set's dice
  conventions, records the outcome of a roll, and can apply an outcome to a sheet value (e.g. damage
  to hit points) subject to the guidance in FR-005.
- **FR-021**: System MUST provide a combat/initiative tracker that orders participating characters and
  NPCs by initiative, tracks whose turn it is and round progression, and lets the DM advance turns;
  players see the combat state the DM has revealed to the table in real time (per FR-019).

**Identity**

- **FR-022**: System MUST require users to be signed in to create or access characters and campaigns,
  and MUST attribute ownership of characters and campaigns to the signed-in user.

### Key Entities *(include if feature involves data)*

- **User**: A signed-in person. May be the dungeon master of some campaigns and a player in others.
  Owns characters.
- **Rule set**: A named system of rules that determines campaign/character compatibility. Defined in
  two parts (per FR-001): a **sheet definition** (data: the sections and fields) read by the shared
  engine, plus **rule-set logic** (derived values and soft validation) provided for that rule set.
  v1 bundles D&D 3.5; the custom Dark Souls adaptation and D&D 5E are added later.
- **Character**: A player character owned by a user, built for one rule set. Carries a character sheet.
  Participates in at most one campaign at a time (assumption) of the same rule set.
- **Character sheet**: The data describing a character or NPC — the fields defined by its rule set
  (stats, hit points, inventory, abilities, etc.) and their current values.
- **NPC**: A non-player character created and controlled by a DM within a campaign, using the
  campaign's rule set.
- **Campaign**: A game run by a DM, bound to one rule set, with a roster of players and their
  characters, NPCs, shared and private content, and sessions.
- **Membership**: The link between a player's character and a campaign, created by the DM adding the
  character, subject to the rule-set match.
- **Session**: A record of a unit of play within a campaign, used to prepare beforehand and continue
  campaign state afterward.
- **Content**: DM-authored information (notes and shared items) marked as either private (DM-only) or
  shared (visible to all players in the campaign). Named the `content` entity throughout the design.
- **Encounter**: An active combat within a session — an ordered set of combatants (characters and
  NPCs) with initiative, current turn, and round, run by the DM, with state revealed to the table
  selectively.
- **Roll**: An in-app dice roll made under the active rule set, with a recorded outcome that may be
  applied to a sheet value.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A participant can create a character and fill in a complete sheet for a supported rule
  set in a single sitting without needing paper, and retrieve it unchanged on a later visit.
- **SC-002**: A DM can stand up a campaign and add all players' valid characters to its roster in under
  15 minutes for a typical group of up to 6 players.
- **SC-003**: 100% of attempts to join a campaign with a mismatched rule set are refused, and 0% of
  valid same-rule-set joins are refused.
- **SC-004**: A player can never see another player's private sheet or DM-private content; verified by
  access checks denying 100% of unauthorized-view attempts.
- **SC-005**: D&D 3.5 is usable end to end in v1 — character creation, campaign participation, live
  play — and users can create any number of 3.5 characters without code changes.
- **SC-009**: A second rule set can be introduced by supplying its sheet definition and rule-set logic
  alone, with no changes to the shared sheet engine or the dice/combat/sync/permissions capabilities
  (validated when the Dark Souls rule set is added — User Story 5).
- **SC-006**: During normal play, sheet updates made by an authorized editor become available to other
  authorized viewers of that content within the same session without data loss from concurrent edits.
- **SC-007**: When the DM reveals a change during a live session, authorized players see the update
  without manually refreshing, within 3 seconds of the change.
- **SC-008**: A DM can run a full combat round for a party of up to 6 characters plus NPCs using the
  in-app initiative tracker and dice roller, applying at least one roll result to a sheet, without
  leaving Tome.

## Assumptions

- **Identity is delegated**: Users authenticate through the platform identity service, Hive (per the
  Tome constitution, Principle V); Tome does not manage its own logins or user store.
- **Role-gated access (v1)**: Access to Tome requires a Hive-assigned Tome role (**Admin** or
  **User**), provisioned by a Hive administrator when the account is created. There is no in-app
  self-service invitation in v1; the DM builds the roster by adding existing players' characters
  (FR-009, FR-024).
- **Future invitation flow**: An invitation-link capability is expected to be built in Hive first and
  later lifted to platform apps, at which point sign-in-and-create-user can be safeguarded so only
  invited people get accounts. Out of scope for Tome v1; noted to inform the roster/membership design.
- **One active campaign per character (v1)**: A character participates in at most one campaign at a
  time; multi-campaign characters are out of scope for the baseline.
- **DM owns the campaign**: The campaign creator is its sole DM for the baseline; co-DMs and handing
  off the DM role are out of scope for v1.
- **Single launch rule set**: v1 ships with **D&D 3.5 only**. The custom Dark Souls adaptation is a
  later increment (User Story 5) and D&D 5E is deferred beyond it. Rule sets are provided and
  maintained by the product, not authored by end users, in the baseline.
- **Dark Souls lineage is open**: The Dark Souls rule set will derive from either D&D 3.5 or 5E with
  deviations where sensible; the specifics are intentionally unresolved until User Story 5 is planned,
  at which point this spec may be amended.
- **Web-first**: The baseline targets web browsers on desktop and tablet; native mobile apps and
  offline play are out of scope for v1.
- **Bilingual**: The interface is available in Norwegian Bokmål and English (per platform
  conventions); rule-set-specific game terminology may remain in its canonical language.
- **Human record-keeping**: Tome records and presents campaign data; it does not act as a referee that
  overrules the DM. The DM remains the authority at the table.

## Clarifications

### Session 2026-07-19

- Q: How much game logic should Tome enforce/automate? → A: **Guided (soft validation)** — Tome
  computes derived values and warns on rule violations but lets the DM override (FR-005).
- Q: How do players see the DM's live updates during play? → A: **Real-time live table** — authorized
  updates propagate to participants live, without manual refresh (FR-019, SC-007).
- Q: Are dice rolling and combat/initiative tracking in the baseline? → A: **Yes, both** — in-app dice
  roller and combat/initiative tracker are in scope for v1 (FR-020, FR-021, User Story 4).

### Session 2026-07-20

- Q: How are a rule set's sheet structure and validation rules defined? → A: **Hybrid** — a shared
  data-driven sheet engine reads each rule set's field definitions, with rule-set-specific derived/
  validation logic in code; cross-cutting dice/combat/sync/permissions are written once over any sheet
  (FR-001, FR-023, SC-009).
- Q: Which rule sets launch in v1? → A: **D&D 3.5 only** for the first edition; the custom Dark Souls
  adaptation becomes its own later story (User Story 5, P5) and D&D 5E is deferred beyond it (FR-023).
- Q: What is the Dark Souls rule set based on? → A: **Deferred** — it will derive from 3.5 or 5E with
  deviations, to be detailed (and the spec possibly amended) when User Story 5 is planned.
- Q: How do players get into a campaign in v1? → A: **DM adds existing characters** — access is gated
  by a Hive-assigned Tome role (Admin/User); players sign in via Hive and create their own characters,
  then the DM adds matching-rule-set characters to the campaign. In-app invitation links are deferred
  to a future Hive capability (FR-009, FR-024, Assumptions).
