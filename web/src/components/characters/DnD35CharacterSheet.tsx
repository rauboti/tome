import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Heading, SimpleGrid, Stack } from '@chakra-ui/react'
import { Combobox, Input } from '@rauboti/ui'
import {
  enrichDnD35,
  dnd35AttackBonus,
  dnd35SkillTotal,
  dnd35SpellSaveDcBase,
  dnd35SpellSlotBonus,
  dnd35SpellSlotTotal,
  DND35_ABILITY_MODS,
  DND35_FEAT_TYPES,
  DND35_SKILL_PRESET_COUNT,
} from '@/sheets/dnd35'
import { SheetTable, type SheetTableColumn } from './SheetTable'
import { SpellsTable } from './SpellsTable'
import type {
  DnD35BaseAttack,
  DnD35CharacterBaseData,
  DnD35BaseSkill,
  DnD35Spell,
  DnD35BaseSpellSlot,
} from '@/types'

type Row = Record<string, unknown>
const asRows = (rows: readonly unknown[]): Row[] => rows as unknown as Row[]

/**
 * A number input holding its own display string, committing the parsed number to the parent on each
 * change (empty → 0). Local text state avoids the controlled-number cursor fight while the parent stays
 * the source of truth for derived recomputation.
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
      required
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
 * Typed D&D 3.5 sheet editor (ADR-001). Edits the base inputs ([DnD35CharacterBaseData]) and shows the derived
 * values read-only, recomputed live via {@link enrichDnD35} — see web/README.md "The typed sheet mirror".
 * Renders the scalar groups (identity, abilities, hit points, saves, defense) plus the repeating-group
 * tables (skills with the canonical preset list, attacks, feats, gear, spellcasting stats + slots + the
 * class-filtered spell picker) via {@link SheetTable} / {@link SpellsTable}.
 */
export type DnD35CharacterSheetProps = {
  base: DnD35CharacterBaseData
  onChange: (next: DnD35CharacterBaseData) => void
}

export const DnD35CharacterSheet = ({ base, onChange }: DnD35CharacterSheetProps) => {
  const { t } = useTranslation()
  const sheet = enrichDnD35(base)
  const fieldLabel = (id: string) => t(`dnd35.field.${id}`)
  const sectionLabel = (id: string) => t(`dnd35.section.${id}`)

  // Alignment/size dropdown options, labels from the (bilingual) i18n maps.
  const alignmentOptions = ['LG', 'NG', 'CG', 'LN', 'TN', 'CN', 'LE', 'NE', 'CE'].map((v) => ({
    value: v,
    label: t(`dnd35.alignment.${v}`),
  }))
  const sizeOptions = ['small', 'medium', 'large'].map((v) => ({ value: v, label: t(`dnd35.size.${v}`) }))

  const textField = (labelId: string, value: string, set: (v: string) => void) => (
    <Input
      label={fieldLabel(labelId)}
      aria-label={fieldLabel(labelId)}
      required
      value={value}
      onChange={(e) => set(e.currentTarget.value)}
    />
  )
  const numberField = (id: string, value: number, set: (n: number) => void) => (
    <NumberField label={fieldLabel(id)} value={value} onCommit={set} />
  )
  const derivedField = (id: string, value: number) => (
    <Input
      label={fieldLabel(id)}
      aria-label={fieldLabel(id)}
      required
      value={String(value)}
      readOnly
      disabled
    />
  )

  const setAbility = (key: keyof DnD35CharacterBaseData['abilities']) => (n: number) =>
    onChange({ ...base, abilities: { ...base.abilities, [key]: n } })
  const setSave = (key: keyof DnD35CharacterBaseData['saves']) => (n: number) =>
    onChange({ ...base, saves: { ...base.saves, [key]: n } })
  const setDefense = (key: keyof DnD35CharacterBaseData['defense']) => (n: number) =>
    onChange({ ...base, defense: { ...base.defense, [key]: n } })
  const setHp = (key: keyof DnD35CharacterBaseData['hitPoints']) => (n: number) =>
    onChange({ ...base, hitPoints: { ...base.hitPoints, [key]: n } })
  const setSpellcasting = (partial: Partial<DnD35CharacterBaseData['spellcasting']>) =>
    onChange({ ...base, spellcasting: { ...base.spellcasting, ...partial } })

  const comboField = (
    label: string,
    value: string,
    options: ReadonlyArray<{ value: string; label: string }>,
    set: (v: string) => void,
  ) => (
    <Combobox
      label={label}
      required
      items={[...options]}
      value={value === '' ? [] : [value]}
      onValueChange={(vals) => set(vals[0] ?? '')}
    />
  )

  const skillColumns: SheetTableColumn[] = [
    { id: 'skill', label: 'Skill', kind: 'text', presetLocked: true, span: 2 },
    { id: 'keyAbility', label: 'Key Ability', kind: 'select', options: DND35_ABILITY_MODS, presetLocked: true },
    { id: 'ranks', label: 'Ranks', kind: 'number' },
    { id: 'classSkill', label: 'Class Skill', kind: 'bool' },
    { id: 'misc', label: 'Misc', kind: 'number' },
    { id: 'total', label: 'Total', kind: 'derived', derive: (row) => dnd35SkillTotal(base, row as unknown as DnD35BaseSkill) },
  ]
  const attackColumns: SheetTableColumn[] = [
    { id: 'weapon', label: 'Weapon', kind: 'text', span: 2 },
    { id: 'ability', label: 'Ability', kind: 'select', options: DND35_ABILITY_MODS },
    { id: 'misc', label: 'Misc', kind: 'number' },
    { id: 'attackBonus', label: 'Attack', kind: 'derived', derive: (row) => dnd35AttackBonus(base, row as unknown as DnD35BaseAttack) },
    { id: 'damage', label: 'Damage', kind: 'text' },
    { id: 'critical', label: 'Crit', kind: 'text' },
    { id: 'range', label: 'Range', kind: 'text' },
    { id: 'notes', label: 'Notes', kind: 'text', span: 2 },
  ]
  const featColumns: SheetTableColumn[] = [
    { id: 'name', label: 'Feat', kind: 'text', span: 2 },
    { id: 'type', label: 'Type', kind: 'select', options: DND35_FEAT_TYPES },
    { id: 'description', label: 'Description', kind: 'text', span: 3 },
  ]
  const gearColumns: SheetTableColumn[] = [
    { id: 'item', label: 'Item', kind: 'text', span: 2 },
    { id: 'quantity', label: 'Qty', kind: 'number' },
    { id: 'weight', label: 'Weight', kind: 'number' },
    { id: 'notes', label: 'Notes', kind: 'text', span: 2 },
  ]
  const slotColumns: SheetTableColumn[] = [
    { id: 'spellLevel', label: 'Spell Level', kind: 'text', presetLocked: true },
    { id: 'slotsPerDay', label: 'Slots/Day', kind: 'number' },
    { id: 'bonusSpells', label: 'Bonus', kind: 'derived', derive: (row) => dnd35SpellSlotBonus(base, row as unknown as DnD35BaseSpellSlot) },
    { id: 'total', label: 'Total', kind: 'derived', derive: (row) => dnd35SpellSlotTotal(base, row as unknown as DnD35BaseSpellSlot) },
    { id: 'known', label: 'Known', kind: 'number' },
    { id: 'prepared', label: 'Prepared', kind: 'number' },
  ]

  return (
    <Stack gap="6">
      <Stack gap="3">
        <Heading size="md">{sectionLabel('identity')}</Heading>
        <SimpleGrid columns={{ base: 1, md: 4 }} gap="4">
          {textField('name', base.name, (v) => onChange({ ...base, name: v }))}
          <Input
            label={fieldLabel('player')}
            aria-label={fieldLabel('player')}
            required
            readOnly
            disabled
            value={base.player.name}
          />
          {textField('race', base.race, (v) => onChange({ ...base, race: v }))}
          {textField('class', base.characterClass, (v) => onChange({ ...base, characterClass: v }))}
          {comboField(fieldLabel('alignment'), base.alignment, alignmentOptions, (v) => onChange({ ...base, alignment: v }))}
          {textField('deity', base.deity, (v) => onChange({ ...base, deity: v }))}
          {numberField('experience', base.experience, (n) => onChange({ ...base, experience: n }))}
          {numberField('level', base.level, (n) => onChange({ ...base, level: n }))}
          {comboField(fieldLabel('size'), base.size, sizeOptions, (v) => onChange({ ...base, size: v }))}
        </SimpleGrid>
      </Stack>

      <Stack gap="3">
        <Heading size="md">{sectionLabel('abilities')}</Heading>
        <SimpleGrid columns={{ base: 2, md: 6 }} gap="4">
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
        <SimpleGrid columns={{ base: 2, md: 6 }} gap="4">
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
        <SimpleGrid columns={{ base: 2, md: 6 }} gap="4">
          {numberField('fortBase', base.saves.fortBase, setSave('fortBase'))}
          {derivedField('fortitude', sheet.saves.fortitude)}
          {numberField('refBase', base.saves.refBase, setSave('refBase'))}
          {derivedField('reflex', sheet.saves.reflex)}
          {numberField('willBase', base.saves.willBase, setSave('willBase'))}
          {derivedField('will', sheet.saves.will)}
        </SimpleGrid>
      </Stack>

      <SheetTable
        title="Skills"
        columns={skillColumns}
        rows={asRows(base.skills)}
        presetCount={DND35_SKILL_PRESET_COUNT}
        onChange={(rows) => onChange({ ...base, skills: rows as unknown as DnD35BaseSkill[] })}
        newRow={() => ({ skill: '', keyAbility: 'strMod', ranks: 0, classSkill: false, misc: 0 })}
        addLabel="Add skill"
      />

      <SheetTable
        title="Attacks"
        columns={attackColumns}
        rows={asRows(base.attacks)}
        onChange={(rows) => onChange({ ...base, attacks: rows as unknown as DnD35BaseAttack[] })}
        newRow={() => ({ weapon: '', ability: 'strMod', misc: 0, damage: '', critical: '', range: '', notes: '' })}
        addLabel="Add attack"
      />

      <SheetTable
        title="Feats"
        columns={featColumns}
        rows={asRows(base.feats)}
        onChange={(rows) => onChange({ ...base, feats: rows as unknown as DnD35CharacterBaseData['feats'] })}
        newRow={() => ({ name: '', type: 'general', description: '' })}
        addLabel="Add feat"
      />

      <Stack gap="3">
        <SheetTable
          title="Gear"
          columns={gearColumns}
          rows={asRows(base.gear)}
          onChange={(rows) => onChange({ ...base, gear: rows as unknown as DnD35CharacterBaseData['gear'] })}
          newRow={() => ({ item: '', quantity: 1, weight: 0, notes: '' })}
          addLabel="Add gear"
        />
        <SimpleGrid columns={{ base: 2, md: 4 }} gap="4">
          <Input label="Total Weight" aria-label="Total Weight" required value={String(sheet.totalWeight)} readOnly disabled />
        </SimpleGrid>
      </Stack>

      <Stack gap="3">
        <Heading size="md">{sectionLabel('spellcasting')}</Heading>
        <SimpleGrid columns={{ base: 2, md: 4 }} gap="4">
          <Input
            label="Caster Class"
            aria-label="Caster Class"
            required
            value={base.spellcasting.casterClass}
            onChange={(e) => setSpellcasting({ casterClass: e.currentTarget.value })}
          />
          <NumberField label="Caster Level" value={base.spellcasting.casterLevel} onCommit={(n) => setSpellcasting({ casterLevel: n })} />
          {comboField('Casting Ability', base.spellcasting.spellKeyAbility, DND35_ABILITY_MODS, (v) => setSpellcasting({ spellKeyAbility: v }))}
          <Input label="Spell Save DC" aria-label="Spell Save DC" required value={String(dnd35SpellSaveDcBase(base))} readOnly disabled />
        </SimpleGrid>
      </Stack>

      <SheetTable
        title="Spell Slots"
        columns={slotColumns}
        rows={asRows(base.spellcasting.spellSlots)}
        presetCount={base.spellcasting.spellSlots.length}
        onChange={(rows) => setSpellcasting({ spellSlots: rows as unknown as DnD35BaseSpellSlot[] })}
      />

      <SpellsTable
        title="Spells"
        ruleSetId={base.ruleSetId}
        casterClass={base.spellcasting.casterClass}
        rows={base.spellcasting.spells}
        onChange={(rows: DnD35Spell[]) => setSpellcasting({ spells: rows })}
      />
    </Stack>
  )
}
