import type {
  DnD35BaseAbilityScores,
  DnD35AbilityScores,
} from './abilityScores'
import type { DnD35BaseDefense, DnD35Defense } from './defense'
import type { DnD35BaseSaves, DnD35Saves } from './saves'
import type {
  DnD35BaseAttack,
  DnD35Feat,
  DnD35Gear,
  DnD35HitPoints,
  DnD35BaseSkill,
  DnD35BaseSpellcasting,
} from './types'

/** The stored/edited base inputs (mirrors `DnD35CharacterBaseData`). */
export type DnD35CharacterBaseData = {
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
  abilities: DnD35BaseAbilityScores
  hitPoints: DnD35HitPoints
  defense: DnD35BaseDefense
  saves: DnD35BaseSaves
  baseAttackBonus: number
  grappleSizeMod: number
  attacks: DnD35BaseAttack[]
  skills: DnD35BaseSkill[]
  feats: DnD35Feat[]
  gear: DnD35Gear[]
  languages: string[]
  notes: string
  spellcasting: DnD35BaseSpellcasting
}

// ---- enriched value objects (base + derived) ----
/** The enriched sheet a response carries (mirrors `DnD35CharacterData`). */
export type DnD35CharacterData = Omit<
  DnD35CharacterBaseData,
  'abilities' | 'defense' | 'saves'
> & {
  abilities: DnD35AbilityScores
  defense: DnD35Defense
  saves: DnD35Saves
  initiative: number
  grapple: number
  totalWeight: number
}
