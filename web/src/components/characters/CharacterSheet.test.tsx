import { describe, expect, test } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { ThemeProvider } from '@rauboti/ui'
import { CharacterSheet } from './CharacterSheet'
import { server } from '@/mocks/server'
// Real i18n bundles so field labels resolve to their canonical D&D terms (T022).
import '@/i18n'

/**
 * Web test for the character sheet edit screen (T027; confirmed for compute-on-read in T102). Drives
 * the screen exactly as the app does: it loads a character and its rule-set definition over the
 * (MSW-mocked) BFF, renders the definition-driven {@link SheetRenderer}, and saves with optimistic
 * concurrency. Under compute-on-read (D8) the server is authoritative for derived values — the mocked
 * responses carry the **resolved** sheet (base inputs + recomputed derived, e.g. `strMod`), and the
 * client re-derives locally only for instant feedback, persisting base inputs only.
 * Asserts the US1 sheet behaviours:
 *  - the loaded sheet renders, with server-computed **derived** values shown read-only;
 *  - a derived value recomputes **live** as its input changes, before any save;
 *  - editing + Save issues a PUT carrying the **version that was read** (SC-006) and **base inputs
 *    only** — no derived fields (D8);
 *  - a save that returns soft **warnings** surfaces them without being treated as an error (FR-005);
 *  - a **409** version conflict is surfaced to the user rather than silently dropped.
 *
 * The rule-set definition comes from the default MSW handler (`GET /api/rule-sets/dnd35`); each test
 * layers the character read/write handlers with `server.use(...)`.
 */

/** A stored character (openapi `Character`), matching the fields in the mock dnd35 definition. */
const character = {
  id: 'char-1',
  name: 'Conan',
  ruleSetId: 'dnd35',
  ownerId: 'ada-lovelace',
  hpCurrent: 30,
  hpMax: 30,
  data: {
    name: 'Conan',
    level: 5,
    alignment: 'CE',
    strength: 18,
    strMod: 4,
    feats: ['Cleave'],
    notes: '',
  },
  warnings: [] as Array<{ code: string; field?: string; message: string }>,
  version: 2,
}

const renderSheet = () =>
  render(
    <ThemeProvider>
      <MemoryRouter>
        <CharacterSheet characterId="char-1" />
      </MemoryRouter>
    </ThemeProvider>,
  )

describe('CharacterSheet', () => {
  test('renders the loaded sheet with server-computed derived values shown read-only', async () => {
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
    )
    renderSheet()

    // Editable fields carry their values once the character + definition have loaded.
    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue(
      'Conan',
    )
    expect(screen.getByRole('spinbutton', { name: 'Strength' })).toHaveValue(18)

    // The derived Str Modifier is display-only (the engine owns it, T017).
    const strMod = screen.getByRole('textbox', { name: 'Str Modifier' })
    expect(strMod).toHaveValue('4')
    expect(strMod).toBeDisabled()
  })

  test('recomputes a derived value live as its input changes, before any save', async () => {
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
    )
    renderSheet()

    // Str Modifier starts at floor((18 - 10) / 2) = 4 from the loaded strength.
    expect(
      await screen.findByRole('textbox', { name: 'Str Modifier' }),
    ).toHaveValue('4')

    // Editing Strength updates the modifier immediately — no save / round-trip.
    const strength = screen.getByRole('spinbutton', { name: 'Strength' })
    await userEvent.clear(strength)
    await userEvent.type(strength, '20')

    expect(screen.getByRole('textbox', { name: 'Str Modifier' })).toHaveValue(
      '5',
    )
  })

  test('editing a field and saving sends base inputs only (no derived) with the version that was read', async () => {
    let putBody: { data?: Record<string, unknown>; version?: number } | null =
      null
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
      http.put('/api/characters/char-1', async ({ request }) => {
        putBody = (await request.json()) as typeof putBody
        return HttpResponse.json({
          ...character,
          data: { ...character.data, ...putBody?.data },
          version: character.version + 1,
        })
      }),
    )
    renderSheet()

    const strength = await screen.findByRole('spinbutton', { name: 'Strength' })
    await userEvent.clear(strength)
    await userEvent.type(strength, '16')
    await userEvent.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(putBody).not.toBeNull())
    // Optimistic concurrency: the read version (2) travels with the write.
    expect(putBody!.version).toBe(2)
    expect(putBody!.data?.strength).toBe(16)
    // Compute-on-read (D8): the sheet loaded a server-resolved `strMod: 4`, but derived values are
    // recomputed on read — never persisted — so the write carries base inputs only, no `strMod`.
    expect(putBody!.data).not.toHaveProperty('strMod')
  })

  test('surfaces soft warnings returned by a save without treating them as an error', async () => {
    const message = 'Ability score for strength is below the minimum of 1.'
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
      http.put('/api/characters/char-1', () =>
        HttpResponse.json({
          ...character,
          data: { ...character.data, strength: 0, strMod: -5 },
          version: character.version + 1,
          warnings: [
            { code: 'ability.below-minimum', field: 'strength', message },
          ],
        }),
      ),
    )
    renderSheet()

    await screen.findByRole('spinbutton', { name: 'Strength' })
    await userEvent.click(screen.getByRole('button', { name: /save/i }))

    // The soft warning is shown to the user; the save still succeeded.
    expect(await screen.findByText(message)).toBeInTheDocument()
  })

  test('surfaces a version conflict when the save returns 409', async () => {
    const detail =
      'This character was changed by someone else. Reload and try again.'
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
      http.put('/api/characters/char-1', () =>
        HttpResponse.json(
          { title: 'Conflict', status: 409, detail },
          {
            status: 409,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )
    renderSheet()

    await screen.findByRole('spinbutton', { name: 'Strength' })
    await userEvent.click(screen.getByRole('button', { name: /save/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(
      /changed by someone else|conflict|out of date/i,
    )
  })
})
