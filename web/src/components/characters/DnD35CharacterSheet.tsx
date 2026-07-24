import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Heading, SimpleGrid, Stack } from '@chakra-ui/react'
import { Input } from '@rauboti/ui'
import { enrichDnD35, type DnD35SheetInput } from '@/sheets/dnd35'

/**
 * A number input that holds its own display string, committing the parsed number to the parent on each
 * change (empty → 0). Local text state keeps `clear()`-then-`type()` clean (no controlled-number cursor
 * fight) while the parent stays the source of truth for derived recomputation.
 */
const NumberField = ({
  label,
  value,
  onCommit,
}: {
  label: string
  value: number
  onCommit: (n: number) => void
}) => {
  const [text, setText] = useState(() => String(value))
  return (
    <Input
      label={label}
      aria-label={label}
      type="number"
      value={text}
      onChange={(e) => {
        const raw = e.currentTarget.value
        setText(raw)
        onCommit(raw === '' ? 0 : Number(raw))
      }}
    />
  )
}

/**
 * Typed D&D 3.5 sheet editor (ADR-001, T126) — replaces the generic definition-driven renderer for
 * this rule set. Edits the **base inputs** ([DnD35SheetInput]) and shows the **derived** values
 * read-only, recomputed **live** via {@link enrichDnD35} (the client mirror of the server's `enrich()`)
 * so a modifier updates as its score changes, before any save.
 *
 * v1 renders the core groups (identity, abilities, hit points, saves, defense). The table-heavy 3C
 * groups (skills/attacks/feats/gear/spellcasting) are carried through the draft untouched — a save never
 * drops them — and are rendered by a later typed-content port (tracked separately).
 */
export type DnD35CharacterSheetProps = {
  base: DnD35SheetInput
  onChange: (next: DnD35SheetInput) => void
}

export const DnD35CharacterSheet = ({ base, onChange }: DnD35CharacterSheetProps) => {
  const { t } = useTranslation()
  const sheet = enrichDnD35(base)
  const fieldLabel = (id: string) => t(`dnd35.field.${id}`)
  const sectionLabel = (id: string) => t(`dnd35.section.${id}`)

  const numberField = (id: string, value: number, set: (n: number) => void) => (
    <NumberField label={fieldLabel(id)} value={value} onCommit={set} />
  )
  const derivedField = (id: string, value: number) => (
    <Input
      label={fieldLabel(id)}
      aria-label={fieldLabel(id)}
      value={String(value)}
      readOnly
      disabled
    />
  )

  const setAbility = (key: keyof DnD35SheetInput['abilities']) => (n: number) =>
    onChange({ ...base, abilities: { ...base.abilities, [key]: n } })
  const setSave = (key: keyof DnD35SheetInput['saves']) => (n: number) =>
    onChange({ ...base, saves: { ...base.saves, [key]: n } })
  const setDefense = (key: keyof DnD35SheetInput['defense']) => (n: number) =>
    onChange({ ...base, defense: { ...base.defense, [key]: n } })
  const setHp = (key: keyof DnD35SheetInput['hitPoints']) => (n: number) =>
    onChange({ ...base, hitPoints: { ...base.hitPoints, [key]: n } })

  return (
    <Stack gap="6">
      <Stack gap="3">
        <Heading size="md">{sectionLabel('identity')}</Heading>
        <SimpleGrid columns={{ base: 1, md: 2 }} gap="4">
          <Input
            label={fieldLabel('name')}
            aria-label={fieldLabel('name')}
            value={base.name}
            onChange={(e) => onChange({ ...base, name: e.currentTarget.value })}
          />
          {numberField('level', base.level, (n) => onChange({ ...base, level: n }))}
        </SimpleGrid>
      </Stack>

      <Stack gap="3">
        <Heading size="md">{sectionLabel('abilities')}</Heading>
        <SimpleGrid columns={{ base: 2, md: 4 }} gap="4">
          {numberField('strength', base.abilities.strength, setAbility('strength'))}
          {derivedField('strMod', sheet.abilities.strMod)}
          {numberField('dexterity', base.abilities.dexterity, setAbility('dexterity'))}
          {derivedField('dexMod', sheet.abilities.dexMod)}
          {numberField('constitution', base.abilities.constitution, setAbility('constitution'))}
          {derivedField('conMod', sheet.abilities.conMod)}
          {numberField('intelligence', base.abilities.intelligence, setAbility('intelligence'))}
          {derivedField('intMod', sheet.abilities.intMod)}
          {numberField('wisdom', base.abilities.wisdom, setAbility('wisdom'))}
          {derivedField('wisMod', sheet.abilities.wisMod)}
          {numberField('charisma', base.abilities.charisma, setAbility('charisma'))}
          {derivedField('chaMod', sheet.abilities.chaMod)}
        </SimpleGrid>
      </Stack>

      <Stack gap="3">
        <Heading size="md">{sectionLabel('combat')}</Heading>
        <SimpleGrid columns={{ base: 2, md: 4 }} gap="4">
          {numberField('hpMax', base.hitPoints.max, setHp('max'))}
          {numberField('hpCurrent', base.hitPoints.current, setHp('current'))}
        </SimpleGrid>
      </Stack>

      <Stack gap="3">
        <Heading size="md">{sectionLabel('defense')}</Heading>
        <SimpleGrid columns={{ base: 2, md: 4 }} gap="4">
          {numberField('armorBonus', base.defense.armorBonus, setDefense('armorBonus'))}
          {numberField('shieldBonus', base.defense.shieldBonus, setDefense('shieldBonus'))}
          {numberField('naturalArmor', base.defense.naturalArmor, setDefense('naturalArmor'))}
          {numberField('deflection', base.defense.deflection, setDefense('deflection'))}
          {numberField('dodge', base.defense.dodge, setDefense('dodge'))}
          {numberField('sizeMod', base.defense.sizeMod, setDefense('sizeMod'))}
          {derivedField('armorClass', sheet.defense.armorClass)}
          {derivedField('touchAC', sheet.defense.touchAC)}
          {derivedField('flatFootedAC', sheet.defense.flatFootedAC)}
        </SimpleGrid>
      </Stack>

      <Stack gap="3">
        <Heading size="md">{sectionLabel('saves')}</Heading>
        <SimpleGrid columns={{ base: 2, md: 3 }} gap="4">
          {numberField('fortBase', base.saves.fortBase, setSave('fortBase'))}
          {derivedField('fortitude', sheet.saves.fortitude)}
          {numberField('refBase', base.saves.refBase, setSave('refBase'))}
          {derivedField('reflex', sheet.saves.reflex)}
          {numberField('willBase', base.saves.willBase, setSave('willBase'))}
          {derivedField('will', sheet.saves.will)}
        </SimpleGrid>
      </Stack>
    </Stack>
  )
}
