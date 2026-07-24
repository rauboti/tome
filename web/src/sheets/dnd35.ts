import { z } from 'zod'

/**
 * Typed D&D 3.5 sheet (ADR-001, T126) — the web mirror of the Kotlin `DnD35CharacterBaseData` /
 * `DnD35CharacterData` split:
 *  - `DnD35SheetInput` — base inputs (what a request sends / the draft the editor holds);
 *  - `DnD35Sheet` — the enriched sheet a response returns (base + derived), validated by
 *    [dnd35SheetSchema];
 *  - [enrichDnD35] — the client mirror of the server's `enrich()`, so derived values update **live**
 *    while editing (the base inputs are the source of truth; derived are recomputed, never sent).
 *
 * Hand-authored to mirror the openapi `Sheet`/`SheetInput` (no codegen toolchain wired; the openapi
 * remains the contract and these types must track it — a future `openapi-typescript` step could
 * generate them). Every base field is modelled so an edit round-trip never drops un-rendered groups.
 */

// ---- base value objects ----
export type DnD35AbilityScores = {
  strength: number
  dexterity: number
  constitution: number
  intelligence: number
  wisdom: number
  charisma: number
}
export type DnD35HitPoints = { max: number; current: number }
export type DnD35DefenseInputs = {
  armorBonus: number
  shieldBonus: number
  naturalArmor: number
  deflection: number
  dodge: number
  sizeMod: number
}
export type DnD35SaveInputs = { fortBase: number; refBase: number; willBase: number }
export type DnD35AttackRowInput = {
  weapon: string
  ability: string
  misc: number
  damage: string
  critical: string
  range: string
  notes: string
}
export type DnD35SkillRowInput = {
  skill: string
  keyAbility: string
  ranks: number
  classSkill: boolean
  misc: number
}
export type DnD35SpellSlotRowInput = {
  spellLevel: number
  slotsPerDay: number
  known: number
  prepared: number
}
export type DnD35FeatRow = { name: string; type: string; description: string }
export type DnD35GearRow = { item: string; quantity: number; weight: number; notes: string }
export type DnD35SpellRow = { spell: string; level: number; prepared: number; notes: string }
export type DnD35SpellcastingInputs = {
  casterClass: string
  casterLevel: number
  spellKeyAbility: string
  spellSlots: DnD35SpellSlotRowInput[]
  spells: DnD35SpellRow[]
}

/** The stored/edited base inputs (mirrors `DnD35CharacterBaseData`). */
export type DnD35SheetInput = {
  ruleSetId: 'dnd35'
  name: string
  player: string
  race: string
  characterClass: string
  alignment: string
  deity: string
  size: string
  level: number
  experience: number
  abilities: DnD35AbilityScores
  hitPoints: DnD35HitPoints
  defense: DnD35DefenseInputs
  saves: DnD35SaveInputs
  baseAttackBonus: number
  grappleSizeMod: number
  attacks: DnD35AttackRowInput[]
  skills: DnD35SkillRowInput[]
  feats: DnD35FeatRow[]
  gear: DnD35GearRow[]
  languages: string[]
  notes: string
  spellcasting: DnD35SpellcastingInputs
}

// ---- enriched value objects (base + derived) ----
export type DnD35Abilities = DnD35AbilityScores & {
  strMod: number
  dexMod: number
  conMod: number
  intMod: number
  wisMod: number
  chaMod: number
}
export type DnD35Defense = DnD35DefenseInputs & {
  armorClass: number
  touchAC: number
  flatFootedAC: number
}
export type DnD35Saves = DnD35SaveInputs & { fortitude: number; reflex: number; will: number }

/** The enriched sheet a response carries (mirrors `DnD35CharacterData`). */
export type DnD35Sheet = Omit<DnD35SheetInput, 'abilities' | 'defense' | 'saves'> & {
  abilities: DnD35Abilities
  defense: DnD35Defense
  saves: DnD35Saves
  initiative: number
  grapple: number
  totalWeight: number
}

// ---- Zod (validates the enriched response; core groups strict, base fields present) ----
const abilityScoresBase = {
  strength: z.number(),
  dexterity: z.number(),
  constitution: z.number(),
  intelligence: z.number(),
  wisdom: z.number(),
  charisma: z.number(),
}
const defenseBase = {
  armorBonus: z.number(),
  shieldBonus: z.number(),
  naturalArmor: z.number(),
  deflection: z.number(),
  dodge: z.number(),
  sizeMod: z.number(),
}
const saveBase = { fortBase: z.number(), refBase: z.number(), willBase: z.number() }

export const dnd35SheetSchema = z.object({
  ruleSetId: z.literal('dnd35'),
  name: z.string(),
  player: z.string().default(''),
  race: z.string().default(''),
  characterClass: z.string().default(''),
  alignment: z.string().default(''),
  deity: z.string().default(''),
  size: z.string().default(''),
  level: z.number().default(1),
  experience: z.number().default(0),
  abilities: z.object({
    ...abilityScoresBase,
    strMod: z.number(),
    dexMod: z.number(),
    conMod: z.number(),
    intMod: z.number(),
    wisMod: z.number(),
    chaMod: z.number(),
  }),
  hitPoints: z.object({ max: z.number(), current: z.number() }),
  defense: z.object({
    ...defenseBase,
    armorClass: z.number(),
    touchAC: z.number(),
    flatFootedAC: z.number(),
  }),
  saves: z.object({
    ...saveBase,
    fortitude: z.number(),
    reflex: z.number(),
    will: z.number(),
  }),
  baseAttackBonus: z.number().default(0),
  grappleSizeMod: z.number().default(0),
  initiative: z.number(),
  grapple: z.number(),
  totalWeight: z.number(),
  // Table groups are carried through unchanged (rendered by a later content port); typed loosely here.
  attacks: z.array(z.record(z.string(), z.unknown())).default([]),
  skills: z.array(z.record(z.string(), z.unknown())).default([]),
  feats: z.array(z.record(z.string(), z.unknown())).default([]),
  gear: z.array(z.record(z.string(), z.unknown())).default([]),
  languages: z.array(z.string()).default([]),
  notes: z.string().default(''),
  spellcasting: z.record(z.string(), z.unknown()).default({}),
})

/** 3.5 ability modifier: floor((score − 10) / 2) — matches the Kotlin `Math.floorDiv`. */
const abilityMod = (score: number): number => Math.floor((score - 10) / 2)

/**
 * Client mirror of the server's `enrich()` — build the enriched [DnD35Sheet] from base inputs so the
 * editor can show derived values that update live. Keeps parity with `DnD35CharacterData` for the
 * groups the UI renders (abilities, defense, saves, initiative, grapple); table/spellcasting groups
 * are carried through unchanged (their per-row derived come from the server until the content port).
 */
export const enrichDnD35 = (base: DnD35SheetInput): DnD35Sheet => {
  const a = base.abilities
  const abilities: DnD35Abilities = {
    ...a,
    strMod: abilityMod(a.strength),
    dexMod: abilityMod(a.dexterity),
    conMod: abilityMod(a.constitution),
    intMod: abilityMod(a.intelligence),
    wisMod: abilityMod(a.wisdom),
    chaMod: abilityMod(a.charisma),
  }
  const d = base.defense
  const defense: DnD35Defense = {
    ...d,
    armorClass:
      10 + d.armorBonus + d.shieldBonus + abilities.dexMod + d.sizeMod + d.naturalArmor + d.deflection + d.dodge,
    touchAC: 10 + abilities.dexMod + d.sizeMod + d.deflection + d.dodge,
    flatFootedAC: 10 + d.armorBonus + d.shieldBonus + d.sizeMod + d.naturalArmor + d.deflection,
  }
  const s = base.saves
  const saves: DnD35Saves = {
    ...s,
    fortitude: s.fortBase + abilities.conMod,
    reflex: s.refBase + abilities.dexMod,
    will: s.willBase + abilities.wisMod,
  }
  return {
    ...base,
    abilities,
    defense,
    saves,
    initiative: abilities.dexMod,
    grapple: base.baseAttackBonus + abilities.strMod + base.grappleSizeMod,
    totalWeight: base.gear.reduce((sum, row) => sum + (row.weight ?? 0), 0),
  }
}

/** A fresh D&D 3.5 base sheet with all fields at their defaults (mirrors the Kotlin ctor defaults). */
export const defaultDnD35SheetInput = (name = ''): DnD35SheetInput => ({
  ruleSetId: 'dnd35',
  name,
  player: '',
  race: '',
  characterClass: '',
  alignment: '',
  deity: '',
  size: '',
  level: 1,
  experience: 0,
  abilities: { strength: 10, dexterity: 10, constitution: 10, intelligence: 10, wisdom: 10, charisma: 10 },
  hitPoints: { max: 0, current: 0 },
  defense: { armorBonus: 0, shieldBonus: 0, naturalArmor: 0, deflection: 0, dodge: 0, sizeMod: 0 },
  saves: { fortBase: 0, refBase: 0, willBase: 0 },
  baseAttackBonus: 0,
  grappleSizeMod: 0,
  attacks: [],
  skills: [],
  feats: [],
  gear: [],
  languages: [],
  notes: '',
  spellcasting: { casterClass: '', casterLevel: 0, spellKeyAbility: '', spellSlots: [], spells: [] },
})

/** Strip the derived values from an enriched sheet, yielding the base inputs to edit/send. */
export const toDnD35Base = (sheet: DnD35Sheet): DnD35SheetInput => ({
  ruleSetId: 'dnd35',
  name: sheet.name,
  player: sheet.player,
  race: sheet.race,
  characterClass: sheet.characterClass,
  alignment: sheet.alignment,
  deity: sheet.deity,
  size: sheet.size,
  level: sheet.level,
  experience: sheet.experience,
  abilities: {
    strength: sheet.abilities.strength,
    dexterity: sheet.abilities.dexterity,
    constitution: sheet.abilities.constitution,
    intelligence: sheet.abilities.intelligence,
    wisdom: sheet.abilities.wisdom,
    charisma: sheet.abilities.charisma,
  },
  hitPoints: sheet.hitPoints,
  defense: {
    armorBonus: sheet.defense.armorBonus,
    shieldBonus: sheet.defense.shieldBonus,
    naturalArmor: sheet.defense.naturalArmor,
    deflection: sheet.defense.deflection,
    dodge: sheet.defense.dodge,
    sizeMod: sheet.defense.sizeMod,
  },
  saves: {
    fortBase: sheet.saves.fortBase,
    refBase: sheet.saves.refBase,
    willBase: sheet.saves.willBase,
  },
  baseAttackBonus: sheet.baseAttackBonus,
  grappleSizeMod: sheet.grappleSizeMod,
  attacks: sheet.attacks,
  skills: sheet.skills,
  feats: sheet.feats,
  gear: sheet.gear,
  languages: sheet.languages,
  notes: sheet.notes,
  spellcasting: sheet.spellcasting,
})
