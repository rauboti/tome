import type { DnD35CharacterBaseData, DnD35CharacterData } from '@/types'

/** The ability-mod ids, for select columns that pick a governing ability (label = short form). */
export const DND35_ABILITY_MODS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'strMod', label: 'Str' },
  { value: 'dexMod', label: 'Dex' },
  { value: 'conMod', label: 'Con' },
  { value: 'intMod', label: 'Int' },
  { value: 'wisMod', label: 'Wis' },
  { value: 'chaMod', label: 'Cha' },
]

/** Feat categories (select options for the feats table). */
export const DND35_FEAT_TYPES: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'general', label: 'General' },
  { value: 'combat', label: 'Combat' },
  { value: 'metamagic', label: 'Metamagic' },
  { value: 'itemCreation', label: 'Item Creation' },
]

/** The canonical 3.5 SRD skill list (name + governing ability mod), seeded on a fresh sheet. */
export const DND35_SKILL_PRESETS: ReadonlyArray<{ skill: string; keyAbility: string }> = [
  { skill: 'Appraise', keyAbility: 'intMod' },
  { skill: 'Balance', keyAbility: 'dexMod' },
  { skill: 'Bluff', keyAbility: 'chaMod' },
  { skill: 'Climb', keyAbility: 'strMod' },
  { skill: 'Concentration', keyAbility: 'conMod' },
  { skill: 'Decipher Script', keyAbility: 'intMod' },
  { skill: 'Diplomacy', keyAbility: 'chaMod' },
  { skill: 'Disable Device', keyAbility: 'intMod' },
  { skill: 'Disguise', keyAbility: 'chaMod' },
  { skill: 'Escape Artist', keyAbility: 'dexMod' },
  { skill: 'Forgery', keyAbility: 'intMod' },
  { skill: 'Gather Information', keyAbility: 'chaMod' },
  { skill: 'Handle Animal', keyAbility: 'chaMod' },
  { skill: 'Heal', keyAbility: 'wisMod' },
  { skill: 'Hide', keyAbility: 'dexMod' },
  { skill: 'Intimidate', keyAbility: 'chaMod' },
  { skill: 'Jump', keyAbility: 'strMod' },
  { skill: 'Listen', keyAbility: 'wisMod' },
  { skill: 'Move Silently', keyAbility: 'dexMod' },
  { skill: 'Open Lock', keyAbility: 'dexMod' },
  { skill: 'Ride', keyAbility: 'dexMod' },
  { skill: 'Search', keyAbility: 'intMod' },
  { skill: 'Sense Motive', keyAbility: 'wisMod' },
  { skill: 'Sleight of Hand', keyAbility: 'dexMod' },
  { skill: 'Spellcraft', keyAbility: 'intMod' },
  { skill: 'Spot', keyAbility: 'wisMod' },
  { skill: 'Survival', keyAbility: 'wisMod' },
  { skill: 'Swim', keyAbility: 'strMod' },
  { skill: 'Tumble', keyAbility: 'dexMod' },
  { skill: 'Use Magic Device', keyAbility: 'chaMod' },
  { skill: 'Use Rope', keyAbility: 'dexMod' },
]

/** How many leading skill rows are the canonical presets (skill + key ability fixed, not removable). */
export const DND35_SKILL_PRESET_COUNT = DND35_SKILL_PRESETS.length

/** A fresh D&D 3.5 base sheet — canonical skills + spell-slot levels seeded, everything else default. */
export const defaultDnD35SheetInput = (name = ''): DnD35CharacterBaseData => ({
  ruleSetId: 'dnd35',
  name,
  player: { id: '', name: '' },
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
  skills: DND35_SKILL_PRESETS.map((p) => ({
    skill: p.skill,
    keyAbility: p.keyAbility,
    ranks: 0,
    classSkill: false,
    misc: 0,
  })),
  feats: [],
  gear: [],
  languages: [],
  notes: '',
  spellcasting: {
    casterClass: '',
    casterLevel: 0,
    spellKeyAbility: '',
    spellSlots: Array.from({ length: 10 }, (_, spellLevel) => ({
      spellLevel,
      slotsPerDay: 0,
      known: 0,
      prepared: 0,
    })),
    spells: [],
  },
})

/** Strip the derived values from an enriched sheet, yielding the base inputs to edit/send. */
export const toDnD35Base = (sheet: DnD35CharacterData): DnD35CharacterBaseData => ({
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
