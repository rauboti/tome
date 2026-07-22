import { describe, expect, test } from 'vitest'
import { deriveValues, evaluateFormula } from './derive'
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
