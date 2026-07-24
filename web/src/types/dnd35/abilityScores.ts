export type DnD35BaseAbilityScores = {
  strength: number
  dexterity: number
  constitution: number
  intelligence: number
  wisdom: number
  charisma: number
}

export type DnD35AbilityScores = DnD35BaseAbilityScores & {
  strMod: number
  dexMod: number
  conMod: number
  intMod: number
  wisMod: number
  chaMod: number
}
