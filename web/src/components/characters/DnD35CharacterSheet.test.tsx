import { useState } from 'react'
import { describe, expect, test } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { ThemeProvider } from '@rauboti/ui'
import { DnD35CharacterSheet } from './DnD35CharacterSheet'
import { defaultDnD35SheetInput, type DnD35SheetInput } from '@/sheets/dnd35'
import { server } from '@/mocks/server'
import '@/i18n'

/**
 * T129 — the typed 3.5 content tables. Drives the component through a stateful harness (base + onChange)
 * so edits recompute derived live, exactly as the sheet screen does. Covers: the canonical skills are
 * seeded and a skill total recomputes from ranks; a weapon's attack bonus is derived; and the
 * class-filtered spell picker fetches from the catalog and auto-fills the spell's level on pick.
 */
const Harness = ({ initial }: { initial: DnD35SheetInput }) => {
  const [base, setBase] = useState(initial)
  return <DnD35CharacterSheet base={base} onChange={setBase} />
}

const renderSheet = (initial: DnD35SheetInput) =>
  render(
    <ThemeProvider>
      <Harness initial={initial} />
    </ThemeProvider>,
  )

describe('DnD35CharacterSheet — typed content tables (T129)', () => {
  test('seeds the canonical skills and recomputes a skill total from its ranks + key-ability mod', async () => {
    const base = defaultDnD35SheetInput('Aria')
    base.abilities = { ...base.abilities, intelligence: 14 } // intMod +2; Appraise (row 1) is Int-based
    renderSheet(base)

    // Canonical skills are present (Appraise is the first preset row).
    const ranks1 = await screen.findByRole('spinbutton', { name: 'Skills Ranks 1' })
    await userEvent.clear(ranks1)
    await userEvent.type(ranks1, '5')

    // Total = ranks 5 + intMod 2 + misc 0 = 7, shown read-only.
    const total1 = screen.getByRole('textbox', { name: 'Skills Total 1' })
    expect(total1).toHaveValue('7')
    expect(total1).toBeDisabled()
  })

  test('derives a weapon attack bonus from BAB + ability mod + misc', () => {
    const base = defaultDnD35SheetInput()
    base.abilities = { ...base.abilities, strength: 18 } // strMod +4
    base.baseAttackBonus = 6
    base.attacks = [{ weapon: 'Greatsword', ability: 'strMod', misc: 1, damage: '', critical: '', range: '', notes: '' }]
    renderSheet(base)

    const attack = screen.getByRole('textbox', { name: 'Attacks Attack 1' })
    expect(attack).toHaveValue('11') // 6 + 4 + 1
    expect(attack).toBeDisabled()
  })

  test('the class-filtered spell picker fetches options and auto-fills the level on pick', async () => {
    server.use(
      http.get('/api/rule-sets/dnd35/catalogs/spells', ({ request }) => {
        const filter = new URL(request.url).searchParams.get('filter')
        expect(filter).toBe('wizard')
        return HttpResponse.json([{ value: 'fireball', label: 'Fireball', meta: { level: 3 } }])
      }),
    )
    const base = defaultDnD35SheetInput()
    base.spellcasting = {
      ...base.spellcasting,
      casterClass: 'wizard',
      spells: [{ spell: '', level: 0, prepared: 0, notes: '' }],
    }
    renderSheet(base)

    // Options arrive from the catalog (keyed off casterClass=wizard).
    await screen.findByRole('option', { name: 'Fireball' })
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Spells Spell 1' }), 'fireball')

    // Picking the spell filled its level (3 for wizard) from the option meta.
    expect(screen.getByRole('spinbutton', { name: 'Spells Level 1' })).toHaveValue(3)
  })
})
