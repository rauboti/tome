import { describe, expect, test } from 'vitest'
import { baseInputs, deriveRow, deriveValues, evaluateFormula } from './derive'
import type { SheetDefinition } from '@/api/schemas'

describe('evaluateFormula', () => {
  test('evaluates the 3.5 ability-modifier formula with floor and negatives', () => {
    expect(
      evaluateFormula('floor((strength - 10) / 2)', { strength: 18 }),
    ).toBe(4)
    expect(evaluateFormula('floor((strength - 10) / 2)', { strength: 7 })).toBe(
      -2,
    ) // floor(-1.5)
  })

  test('adds identifiers and respects precedence + parentheses', () => {
    expect(
      evaluateFormula('fortBase + conMod', { fortBase: 2, conMod: 1 }),
    ).toBe(3)
    expect(evaluateFormula('2 + 3 * 4', {})).toBe(14)
    expect(evaluateFormula('(2 + 3) * 4', {})).toBe(20)
  })

  test('an unknown identifier reads as 0 (matches the server default)', () => {
    expect(evaluateFormula('floor((strength - 10) / 2)', {})).toBe(-5)
    expect(evaluateFormula('dexMod', {})).toBe(0)
  })

  test('malformed or unsafe input returns null (never throws, never evals)', () => {
    expect(
      evaluateFormula('floor((strength - 10) / 2', { strength: 10 }),
    ).toBeNull() // unbalanced
    expect(evaluateFormula('strength +', { strength: 1 })).toBeNull()
    expect(evaluateFormula('1 % 2', {})).toBeNull() // unsupported operator
    expect(evaluateFormula('nope(1)', {})).toBeNull() // unknown function
  })
})

/** A compact dnd35-shaped definition exercising a derived chain: initiative → dexMod → dexterity. */
const definition: SheetDefinition = {
  ruleSetId: 'dnd35',
  version: '1.0.0',
  sections: [
    {
      id: 'abilities',
      labelKey: 'dnd35.section.abilities',
      fields: [
        { id: 'dexterity', labelKey: 'dnd35.field.dexterity', type: 'int' },
        {
          id: 'dexMod',
          labelKey: 'dnd35.field.dexMod',
          type: 'derived',
          derivedFrom: 'floor((dexterity - 10) / 2)',
        },
        {
          id: 'constitution',
          labelKey: 'dnd35.field.constitution',
          type: 'int',
        },
        {
          id: 'conMod',
          labelKey: 'dnd35.field.conMod',
          type: 'derived',
          derivedFrom: 'floor((constitution - 10) / 2)',
        },
      ],
    },
    {
      id: 'combat',
      labelKey: 'dnd35.section.combat',
      fields: [
        {
          id: 'initiative',
          labelKey: 'dnd35.field.initiative',
          type: 'derived',
          derivedFrom: 'dexMod',
        },
        { id: 'fortBase', labelKey: 'dnd35.field.fortBase', type: 'int' },
        {
          id: 'fortitude',
          labelKey: 'dnd35.field.fortitude',
          type: 'derived',
          derivedFrom: 'fortBase + conMod',
        },
      ],
    },
  ],
}

describe('deriveValues', () => {
  test('computes every derived field, resolving derived-on-derived chains', () => {
    const derived = deriveValues(definition, {
      dexterity: 16,
      constitution: 13,
      fortBase: 2,
    })
    expect(derived).toEqual({
      dexMod: 3, // floor((16-10)/2)
      conMod: 1, // floor((13-10)/2)
      initiative: 3, // = dexMod (derived-on-derived)
      fortitude: 3, // fortBase 2 + conMod 1
    })
  })

  test('treats missing inputs as 0', () => {
    const derived = deriveValues(definition, {})
    expect(derived.dexMod).toBe(-5)
    expect(derived.initiative).toBe(-5)
    expect(derived.fortitude).toBe(-5) // 0 + conMod(-5)
  })
})

describe('evaluateFormula — structured primitives (T105)', () => {
  test('sum totals a numeric column across a table field rows', () => {
    expect(
      evaluateFormula('sum(gear.weight)', {
        gear: [{ weight: 8 }, { weight: 50 }, { weight: 2 }],
      }),
    ).toBe(60)
  })

  test('sum reads a missing table or column as 0', () => {
    expect(evaluateFormula('sum(gear.weight)', {})).toBe(0)
    expect(evaluateFormula('sum(gear.weight)', { gear: [{ item: 'x' }] })).toBe(
      0,
    )
  })

  test('ref resolves the value of the field named by another field', () => {
    expect(
      evaluateFormula('ranks + ref(keyAbility) + misc', {
        keyAbility: 'strMod',
        strMod: 4,
        ranks: 8,
        misc: 1,
      }),
    ).toBe(13)
  })

  test('ref reads a missing or non-string reference as 0', () => {
    expect(evaluateFormula('ref(keyAbility)', {})).toBe(0)
    expect(evaluateFormula('ref(keyAbility)', { keyAbility: 5 })).toBe(0)
  })
})

/** A table-bearing definition: a skills table (per-row `total` via `ref`) and a gear table summed. */
const tableDefinition: SheetDefinition = {
  ruleSetId: 'test',
  version: '1.0.0',
  sections: [
    {
      id: 'abilities',
      labelKey: 's.abilities',
      fields: [
        { id: 'strength', labelKey: 'f.str', type: 'int' },
        {
          id: 'strMod',
          labelKey: 'f.strMod',
          type: 'derived',
          derivedFrom: 'floor((strength - 10) / 2)',
        },
      ],
    },
    {
      id: 'skills',
      labelKey: 's.skills',
      fields: [
        {
          id: 'skills',
          labelKey: 'f.skills',
          type: 'table',
          columns: [
            { id: 'skill', labelKey: 'c.skill', type: 'text' },
            { id: 'keyAbility', labelKey: 'c.keyAbility', type: 'text' },
            { id: 'ranks', labelKey: 'c.ranks', type: 'int' },
            { id: 'misc', labelKey: 'c.misc', type: 'int' },
            {
              id: 'total',
              labelKey: 'c.total',
              type: 'derived',
              derivedFrom: 'ranks + ref(keyAbility) + misc',
            },
          ],
        },
      ],
    },
    {
      id: 'gear',
      labelKey: 's.gear',
      fields: [
        {
          id: 'gear',
          labelKey: 'f.gear',
          type: 'table',
          columns: [
            { id: 'item', labelKey: 'c.item', type: 'text' },
            { id: 'weight', labelKey: 'c.weight', type: 'int' },
          ],
        },
        {
          id: 'totalWeight',
          labelKey: 'f.totalWeight',
          type: 'derived',
          derivedFrom: 'sum(gear.weight)',
        },
      ],
    },
  ],
}

describe('deriveValues / deriveRow with tables (T105)', () => {
  test('a top-level sum totals a table column from the raw values', () => {
    const derived = deriveValues(tableDefinition, {
      gear: [{ weight: 8 }, { weight: 50 }],
    })
    expect(derived.totalWeight).toBe(58)
  })

  test('deriveRow computes a per-row total, resolving ref against the sheet scope', () => {
    const columns = tableDefinition.sections[1].fields[0].columns ?? []
    const derived = deriveRow(
      columns,
      { skill: 'climb', keyAbility: 'strMod', ranks: 8, misc: 0 },
      { strMod: 4 },
    )
    expect(derived.total).toBe(12) // 8 + strMod 4 + 0
  })
})

describe('baseInputs strips derived, including per-row table cells (T105)', () => {
  test('drops top-level derived and every row s derived columns', () => {
    const base = baseInputs(tableDefinition, {
      strength: 18,
      strMod: 4,
      totalWeight: 58,
      skills: [
        { skill: 'climb', keyAbility: 'strMod', ranks: 8, misc: 0, total: 12 },
      ],
      gear: [{ item: 'sword', weight: 8 }],
    })
    expect(base).toEqual({
      strength: 18,
      skills: [{ skill: 'climb', keyAbility: 'strMod', ranks: 8, misc: 0 }],
      gear: [{ item: 'sword', weight: 8 }],
    })
  })
})
