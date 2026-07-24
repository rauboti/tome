import type {
  DnD35BaseAbilityScores,
  DnD35AbilityScores,
  DnD35BaseAttack,
  DnD35Defense,
  DnD35Saves,
  DnD35CharacterData,
  DnD35CharacterBaseData,
  DnD35BaseSkill,
  DnD35BaseSpellSlot,
} from '@/types'

/** 3.5 ability modifier: floor((score − 10) / 2) — matches the Kotlin `Math.floorDiv`. */
const abilityMod = (score: number): number => Math.floor((score - 10) / 2)

/**
 * Client mirror of the server's `enrich()`: build the enriched [DnD35CharacterData] from base inputs. The scalar
 * groups (abilities, defense, saves, initiative, grapple) are recomputed here; table and spellcasting
 * groups pass through unchanged (their per-row derived come from the server).
 */
export const enrichDnD35 = (base: DnD35CharacterBaseData): DnD35CharacterData => {
  const a = base.abilities
  const abilities: DnD35AbilityScores = {
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

/** Resolve an ability-mod id (e.g. `"strMod"`) against a set of scores; unknown ref → 0. */
const abilityModByRef = (scores: DnD35BaseAbilityScores, ref: string): number =>
  ({
    strMod: abilityMod(scores.strength),
    dexMod: abilityMod(scores.dexterity),
    conMod: abilityMod(scores.constitution),
    intMod: abilityMod(scores.intelligence),
    wisMod: abilityMod(scores.wisdom),
    chaMod: abilityMod(scores.charisma),
  })[ref] ?? 0

// ---- per-row derived (client mirror of the server; base + one row → the read-only value) ----
/** A skill row's total: ranks + its key-ability mod + misc. */
export const dnd35SkillTotal = (base: DnD35CharacterBaseData, row: DnD35BaseSkill): number =>
  row.ranks + abilityModByRef(base.abilities, row.keyAbility) + row.misc
/** A weapon's attack bonus: BAB + its ability mod + misc. */
export const dnd35AttackBonus = (base: DnD35CharacterBaseData, row: DnD35BaseAttack): number =>
  base.baseAttackBonus + abilityModByRef(base.abilities, row.ability) + row.misc
/** The spell save-DC base: 10 + the casting-ability mod; a level-N spell's DC = this + N. */
export const dnd35SpellSaveDcBase = (base: DnD35CharacterBaseData): number =>
  10 + abilityModByRef(base.abilities, base.spellcasting.spellKeyAbility)
/** Bonus spells for a level: `min(level,1) * max(0, floor((keyMod - level)/4) + 1)` — 0 at level 0. */
export const dnd35SpellSlotBonus = (base: DnD35CharacterBaseData, row: DnD35BaseSpellSlot): number =>
  Math.min(row.spellLevel, 1) *
  Math.max(0, Math.floor((abilityModByRef(base.abilities, base.spellcasting.spellKeyAbility) - row.spellLevel) / 4) + 1)
/** Total slots for a level: entered slots/day + bonus spells. */
export const dnd35SpellSlotTotal = (base: DnD35CharacterBaseData, row: DnD35BaseSpellSlot): number =>
  row.slotsPerDay + dnd35SpellSlotBonus(base, row)
