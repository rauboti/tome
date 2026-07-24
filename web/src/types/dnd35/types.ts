export type DnD35BaseAttack = {
  weapon: string
  ability: string
  misc: number
  damage: string
  critical: string
  range: string
  notes: string
}

export type DnD35Feat = { name: string; type: string; description: string }

export type DnD35Gear = {
  item: string
  quantity: number
  weight: number
  notes: string
}

export type DnD35HitPoints = { max: number; current: number }

export type DnD35Spell = {
  spell: string
  level: number
  prepared: number
  notes: string
}

export type DnD35BaseSkill = {
  skill: string
  keyAbility: string
  ranks: number
  classSkill: boolean
  misc: number
}
export type DnD35BaseSpellSlot = {
  spellLevel: number
  slotsPerDay: number
  known: number
  prepared: number
}
export type DnD35BaseSpellcasting = {
  casterClass: string
  casterLevel: number
  spellKeyAbility: string
  spellSlots: DnD35BaseSpellSlot[]
  spells: DnD35Spell[]
}




