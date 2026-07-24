export type DnD35BaseDefense = {
  armorBonus: number
  shieldBonus: number
  naturalArmor: number
  deflection: number
  dodge: number
  sizeMod: number
}

export type DnD35Defense = DnD35BaseDefense & {
  armorClass: number
  touchAC: number
  flatFootedAC: number
}
