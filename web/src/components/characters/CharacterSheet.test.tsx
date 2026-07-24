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
 * Web test for the typed character sheet editor (T121, red→green with T126). Drives the screen as the
 * app does: it loads a character over the (MSW-mocked) BFF and renders the **typed**
 * {@link DnD35CharacterSheet}, then saves with optimistic concurrency. Under the base/enriched split
 * (ADR-001) the response `data` is the **enriched** sheet (grouped base + derived, e.g.
 * `abilities.strMod`); the editor recomputes derived locally for instant feedback and sends **base
 * inputs only**. Asserts the US1 sheet behaviours:
 *  - the loaded sheet renders, with derived values shown read-only;
 *  - a derived value recomputes **live** as its input changes, before any save;
 *  - editing + Save issues a PUT carrying the read `version` (SC-006) and **base inputs only** — no
 *    derived (D8);
 *  - soft **warnings** from a save surface without being treated as an error (FR-005);
 *  - a **409** version conflict is surfaced rather than silently dropped.
 */

/** A stored character (openapi `Character`) with the enriched D&D 3.5 sheet in `data`. */
const character = {
  id: 'char-1',
  name: 'Conan',
  ruleSetId: 'dnd35',
  ownerId: 'ada-lovelace',
  data: {
    ruleSetId: 'dnd35',
    name: 'Conan',
    player: '',
    race: '',
    characterClass: '',
    alignment: '',
    deity: '',
    size: '',
    level: 5,
    experience: 0,
    abilities: {
      strength: 18,
      dexterity: 12,
      constitution: 14,
      intelligence: 10,
      wisdom: 8,
      charisma: 10,
      strMod: 4,
      dexMod: 1,
      conMod: 2,
      intMod: 0,
      wisMod: -1,
      chaMod: 0,
    },
    hitPoints: { max: 30, current: 30 },
    defense: {
      armorBonus: 0,
      shieldBonus: 0,
      naturalArmor: 0,
      deflection: 0,
      dodge: 0,
      sizeMod: 0,
      armorClass: 11,
      touchAC: 11,
      flatFootedAC: 10,
    },
    saves: {
      fortBase: 2,
      refBase: 0,
      willBase: 1,
      fortitude: 4,
      reflex: 1,
      will: 0,
    },
    baseAttackBonus: 5,
    grappleSizeMod: 0,
    initiative: 1,
    grapple: 9,
    totalWeight: 0,
    attacks: [],
    skills: [],
    feats: [],
    gear: [],
    languages: [],
    notes: '',
    spellcasting: {
      casterClass: '',
      casterLevel: 0,
      spellKeyAbility: '',
      spellSlots: [],
      spells: [],
    },
  },
  warnings: [] as Array<{ code: string; field?: string; message: string }>,
  version: 2,
}

/** Rebuild a valid enriched response from a base edit — recomputes the touched ability's modifier. */
const withStrength = (strength: number) => ({
  ...character,
  data: {
    ...character.data,
    abilities: {
      ...character.data.abilities,
      strength,
      strMod: Math.floor((strength - 10) / 2),
    },
  },
  version: character.version + 1,
})

const renderSheet = () =>
  render(
    <ThemeProvider>
      <MemoryRouter>
        <CharacterSheet characterId="char-1" />
      </MemoryRouter>
    </ThemeProvider>,
  )

describe('CharacterSheet (typed DnD35)', () => {
  test('renders the loaded sheet with derived values shown read-only', async () => {
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
    )
    renderSheet()

    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue(
      'Conan',
    )
    expect(screen.getByRole('spinbutton', { name: 'Strength' })).toHaveValue(18)

    // The derived Str Modifier is display-only (the engine owns it).
    const strMod = screen.getByRole('textbox', { name: 'Str Modifier' })
    expect(strMod).toHaveValue('4')
    expect(strMod).toBeDisabled()
  })

  test('recomputes a derived value live as its input changes, before any save', async () => {
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
    )
    renderSheet()

    expect(
      await screen.findByRole('textbox', { name: 'Str Modifier' }),
    ).toHaveValue('4')

    const strength = screen.getByRole('spinbutton', { name: 'Strength' })
    await userEvent.clear(strength)
    await userEvent.type(strength, '20')

    expect(screen.getByRole('textbox', { name: 'Str Modifier' })).toHaveValue(
      '5',
    )
  })

  test('editing a field and saving sends base inputs only (no derived) with the version that was read', async () => {
    let putBody:
      | { data?: Record<string, unknown>; version?: number }
      | null = null
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
      http.put('/api/characters/char-1', async ({ request }) => {
        putBody = (await request.json()) as typeof putBody
        return HttpResponse.json(withStrength(16))
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
    const abilities = putBody!.data?.abilities as Record<string, unknown>
    expect(abilities.strength).toBe(16)
    // Compute-on-read (D8): base inputs only — no derived modifiers sent.
    expect(abilities).not.toHaveProperty('strMod')
    expect(putBody!.data).not.toHaveProperty('initiative')
    expect(putBody!.data).not.toHaveProperty('grapple')
  })

  test('surfaces soft warnings returned by a save without treating them as an error', async () => {
    const message = 'Ability score for strength is below the minimum of 1.'
    server.use(
      http.get('/api/characters/char-1', () => HttpResponse.json(character)),
      http.put('/api/characters/char-1', () =>
        HttpResponse.json({
          ...withStrength(0),
          warnings: [
            { code: 'ability.below-minimum', field: 'strength', message },
          ],
        }),
      ),
    )
    renderSheet()

    await screen.findByRole('spinbutton', { name: 'Strength' })
    await userEvent.click(screen.getByRole('button', { name: /save/i }))

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
