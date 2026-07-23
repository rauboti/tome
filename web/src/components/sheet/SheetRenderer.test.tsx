import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { ThemeProvider } from '@rauboti/ui'
import { SheetRenderer } from './SheetRenderer'
import { server } from '@/mocks/server'
import type { SheetDefinition } from '@/api/schemas'
// Real bundles so labels resolve to their canonical D&D terms (T022).
import '@/i18n'

const definition: SheetDefinition = {
  ruleSetId: 'dnd35',
  version: '1.0.0',
  sections: [
    {
      id: 'identity',
      labelKey: 'dnd35.section.identity',
      fields: [
        { id: 'name', labelKey: 'dnd35.field.name', type: 'text' },
        { id: 'level', labelKey: 'dnd35.field.level', type: 'int' },
        {
          id: 'alignment',
          labelKey: 'dnd35.field.alignment',
          type: 'select',
          options: [
            { value: 'LG', labelKey: 'dnd35.alignment.LG' },
            { value: 'CE', labelKey: 'dnd35.alignment.CE' },
          ],
        },
      ],
    },
    {
      id: 'abilities',
      labelKey: 'dnd35.section.abilities',
      fields: [
        { id: 'strength', labelKey: 'dnd35.field.strength', type: 'int' },
        {
          id: 'strMod',
          labelKey: 'dnd35.field.strMod',
          type: 'derived',
          derivedFrom: 'floor((strength - 10) / 2)',
        },
      ],
    },
    {
      id: 'gear',
      labelKey: 'dnd35.section.gear',
      fields: [{ id: 'feats', labelKey: 'dnd35.field.feats', type: 'list' }],
    },
  ],
}

const renderSheet = (
  values: Record<string, unknown>,
  onChange = vi.fn(),
  readOnly = false,
) => {
  render(
    <ThemeProvider>
      <SheetRenderer
        definition={definition}
        values={values}
        onChange={onChange}
        readOnly={readOnly}
      />
    </ThemeProvider>,
  )
  return onChange
}

describe('SheetRenderer', () => {
  test('renders section headings and field widgets from the definition', () => {
    renderSheet({ name: 'Conan', strength: 18, strMod: 4, feats: [] })

    expect(
      screen.getByRole('heading', { name: 'Identity' }),
    ).toBeInTheDocument()
    // Text + int inputs carry their labels as accessible names, with their values.
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Conan')
    expect(screen.getByRole('spinbutton', { name: 'Strength' })).toHaveValue(18)
  })

  test('shows a derived field as a read-only value the user cannot edit', () => {
    renderSheet({ strength: 18, strMod: 4 })

    // Derived fields are display-only text (the engine owns the value), so a read-only textbox.
    const derived = screen.getByRole('textbox', { name: 'Str Modifier' })
    expect(derived).toHaveValue('4')
    expect(derived).toBeDisabled()
  })

  test('editing an int field fires onChange with the parsed number', async () => {
    const onChange = renderSheet({ level: 1 })

    const level = screen.getByRole('spinbutton', { name: 'Level' })
    await userEvent.type(level, '2') // "1" + "2" → "12"

    expect(onChange).toHaveBeenLastCalledWith('level', 12)
  })

  test('a select field shows the chosen label and reports the picked value', async () => {
    const onChange = renderSheet({ alignment: 'LG' })

    // The combobox input shows the current selection's label, not its raw value.
    const combo = screen.getByRole('combobox', { name: 'Alignment' })
    expect(combo).toHaveValue('Lawful Good')

    // Open the dropdown (chevron toggle) and pick another alignment.
    await userEvent.click(
      screen.getByRole('button', { name: /toggle options/i }),
    )
    await userEvent.click(
      await screen.findByRole('option', { name: 'Chaotic Evil' }),
    )

    expect(onChange).toHaveBeenLastCalledWith('alignment', 'CE')
  })

  test('the list widget adds an entry via onChange', async () => {
    const onChange = renderSheet({ feats: ['Cleave'] })

    await userEvent.click(screen.getByRole('button', { name: /add/i }))

    expect(onChange).toHaveBeenLastCalledWith('feats', ['Cleave', ''])
  })
})

/** A table field with a canonical preset row and a per-row derived total via `ref` (T105). Label keys
 *  are intentionally not in the i18n bundles, so they resolve to themselves — handy for querying. */
const tableDefinition: SheetDefinition = {
  ruleSetId: 'dnd35',
  version: '1.0.0',
  sections: [
    {
      id: 'abilities',
      labelKey: 'secAbilities',
      fields: [
        { id: 'strength', labelKey: 'colStrength', type: 'int' },
        {
          id: 'strMod',
          labelKey: 'colStrMod',
          type: 'derived',
          derivedFrom: 'floor((strength - 10) / 2)',
        },
      ],
    },
    {
      id: 'skills',
      labelKey: 'secSkills',
      fields: [
        {
          id: 'skills',
          labelKey: 'tblSkills',
          type: 'table',
          presetRows: [{ skill: 'Climb', keyAbility: 'strMod' }],
          columns: [
            { id: 'skill', labelKey: 'colSkill', type: 'text' },
            { id: 'keyAbility', labelKey: 'colKeyAbility', type: 'text' },
            { id: 'ranks', labelKey: 'colRanks', type: 'int' },
            {
              id: 'total',
              labelKey: 'colTotal',
              type: 'derived',
              derivedFrom: 'ranks + ref(keyAbility)',
            },
          ],
        },
      ],
    },
  ],
}

const renderTable = (values: Record<string, unknown>, onChange = vi.fn()) => {
  render(
    <ThemeProvider>
      <SheetRenderer
        definition={tableDefinition}
        values={values}
        onChange={onChange}
        readOnly={false}
      />
    </ThemeProvider>,
  )
  return onChange
}

describe('SheetRenderer — table field (T105)', () => {
  test('seeds a preset row, locks preset cells, and computes the per-row total live via ref', () => {
    // strength 18 → strMod 4 (sheet-level); the Climb row is seeded from presetRows.
    renderTable({ strength: 18, skills: [] })

    // Preset cells are read-only (disabled) and carry their pinned values.
    const skill = screen.getByRole('textbox', { name: 'colSkill 1' })
    expect(skill).toHaveValue('Climb')
    expect(skill).toBeDisabled()
    expect(
      screen.getByRole('textbox', { name: 'colKeyAbility 1' }),
    ).toBeDisabled()

    // Ranks is editable; total = ranks(0) + ref(keyAbility→strMod=4) = 4, read-only.
    expect(screen.getByRole('spinbutton', { name: 'colRanks 1' })).toBeEnabled()
    const total = screen.getByRole('textbox', { name: 'colTotal 1' })
    expect(total).toHaveValue('4')
    expect(total).toBeDisabled()
  })

  test('editing a mutable cell materializes the row via onChange with base inputs only', async () => {
    const onChange = renderTable({ strength: 18, skills: [] })

    await userEvent.type(
      screen.getByRole('spinbutton', { name: 'colRanks 1' }),
      '5',
    )

    // The seeded preset row materializes with the edited rank; the derived `total` is never sent.
    expect(onChange).toHaveBeenLastCalledWith('skills', [
      { skill: 'Climb', keyAbility: 'strMod', ranks: 5 },
    ])
  })

  test('appends a new (fully editable) row via onChange', async () => {
    const onChange = renderTable({
      strength: 18,
      skills: [{ skill: 'Climb', keyAbility: 'strMod', ranks: 8 }],
    })

    await userEvent.click(screen.getByRole('button', { name: /add/i }))

    expect(onChange).toHaveBeenLastCalledWith('skills', [
      { skill: 'Climb', keyAbility: 'strMod', ranks: 8 },
      {},
    ])
  })
})

/** A table with a catalog-backed select column (T113): the `spell` picker's options come from the
 *  catalog endpoint, filtered by the sheet-level `casterClass`. */
const catalogDefinition: SheetDefinition = {
  ruleSetId: 'dnd35',
  version: '1.0.0',
  sections: [
    {
      id: 'spellcasting',
      labelKey: 'secSpellcasting',
      fields: [
        { id: 'casterClass', labelKey: 'colCasterClass', type: 'text' },
        {
          id: 'spells',
          labelKey: 'tblSpells',
          type: 'table',
          columns: [
            {
              id: 'spell',
              labelKey: 'colSpell',
              type: 'select',
              optionsFrom: { catalog: 'spells', filterBy: 'casterClass' },
            },
          ],
        },
      ],
    },
  ],
}

describe('SheetRenderer — catalog-backed select (T113)', () => {
  test('fetches the class-filtered options and reports the picked spell id', async () => {
    server.use(
      http.get('/api/rule-sets/dnd35/catalogs/spells', ({ request }) => {
        const filter = new URL(request.url).searchParams.get('filter')
        return HttpResponse.json(
          filter === 'wizard'
            ? [
                { value: 'fireball', label: 'Fireball', meta: { level: 3 } },
                {
                  value: 'magicMissile',
                  label: 'Magic Missile',
                  meta: { level: 1 },
                },
              ]
            : [],
        )
      }),
    )
    const onChange = vi.fn()
    render(
      <ThemeProvider>
        <SheetRenderer
          definition={catalogDefinition}
          values={{ casterClass: 'wizard', spells: [{}] }}
          onChange={onChange}
          readOnly={false}
        />
      </ThemeProvider>,
    )

    // Open the spell picker and pick a catalog option fetched for the wizard class.
    await userEvent.click(
      screen.getByRole('button', { name: /toggle options/i }),
    )
    await userEvent.click(
      await screen.findByRole('option', { name: 'Fireball' }),
    )

    expect(onChange).toHaveBeenLastCalledWith('spells', [{ spell: 'fireball' }])
  })

  test('picking a catalog spell fills the sibling level column from the option meta (T114)', async () => {
    server.use(
      http.get('/api/rule-sets/dnd35/catalogs/spells', ({ request }) => {
        const filter = new URL(request.url).searchParams.get('filter')
        return HttpResponse.json(
          filter === 'wizard'
            ? [{ value: 'fireball', label: 'Fireball', meta: { level: 3 } }]
            : [],
        )
      }),
    )
    const onChange = vi.fn()
    const withLevel: SheetDefinition = {
      ruleSetId: 'dnd35',
      version: '1.0.0',
      sections: [
        {
          id: 'spellcasting',
          labelKey: 'secSpellcasting',
          fields: [
            { id: 'casterClass', labelKey: 'colCasterClass', type: 'text' },
            {
              id: 'spells',
              labelKey: 'tblSpells',
              type: 'table',
              columns: [
                {
                  id: 'spell',
                  labelKey: 'colSpell',
                  type: 'select',
                  optionsFrom: { catalog: 'spells', filterBy: 'casterClass' },
                },
                { id: 'level', labelKey: 'colLevel', type: 'int' },
              ],
            },
          ],
        },
      ],
    }
    render(
      <ThemeProvider>
        <SheetRenderer
          definition={withLevel}
          values={{ casterClass: 'wizard', spells: [{}] }}
          onChange={onChange}
          readOnly={false}
        />
      </ThemeProvider>,
    )

    await userEvent.click(
      screen.getByRole('button', { name: /toggle options/i }),
    )
    await userEvent.click(
      await screen.findByRole('option', { name: 'Fireball' }),
    )

    // The pick sets both the spell id and its catalog-supplied level (meta.level).
    expect(onChange).toHaveBeenLastCalledWith('spells', [
      { spell: 'fireball', level: 3 },
    ])
  })
})
