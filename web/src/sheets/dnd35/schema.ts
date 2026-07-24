import { z } from 'zod'

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
  // Table groups pass through unchanged; typed loosely here.
  attacks: z.array(z.record(z.string(), z.unknown())).default([]),
  skills: z.array(z.record(z.string(), z.unknown())).default([]),
  feats: z.array(z.record(z.string(), z.unknown())).default([]),
  gear: z.array(z.record(z.string(), z.unknown())).default([]),
  languages: z.array(z.string()).default([]),
  notes: z.string().default(''),
  spellcasting: z.record(z.string(), z.unknown()).default({}),
})
