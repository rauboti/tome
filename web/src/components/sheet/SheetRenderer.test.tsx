import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@rauboti/ui'
import { SheetRenderer } from './SheetRenderer'
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

  test('a select renders its options and reports the chosen value', async () => {
    const onChange = renderSheet({ alignment: 'LG' })

    const select = screen.getByRole('combobox', { name: 'Alignment' })
    expect(
      screen.getByRole('option', { name: 'Chaotic Evil' }),
    ).toBeInTheDocument()
    await userEvent.selectOptions(select, 'CE')

    expect(onChange).toHaveBeenLastCalledWith('alignment', 'CE')
  })

  test('the list widget adds an entry via onChange', async () => {
    const onChange = renderSheet({ feats: ['Cleave'] })

    await userEvent.click(screen.getByRole('button', { name: /add/i }))

    expect(onChange).toHaveBeenLastCalledWith('feats', ['Cleave', ''])
  })
})
