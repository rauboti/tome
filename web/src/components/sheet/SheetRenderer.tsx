import { Heading, HStack, IconButton, Stack } from '@chakra-ui/react'
import {
  Button,
  Card,
  Combobox,
  Grid,
  Input,
  SegmentedControl,
} from '@rauboti/ui'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getCatalogOptions, type CatalogOption } from '@/api/catalogs'
import type {
  OptionsFrom,
  SheetColumn,
  SheetDefinition,
  SheetField,
} from '@/api/schemas'
import { deriveRow, deriveValues } from './derive'

/**
 * Definition-driven sheet renderer: given a rule set's [SheetDefinition] and the current sheet
 * `values`, it lays out each section as a Card and renders a widget per field `type`
 * (int/text/bool/select/list/derived). It's rule-set-agnostic — the same component renders any rule
 * set's sheet, which is the whole point of the Hybrid engine (FR-023). Derived fields are always
 * read-only (the engine owns them, T017); `readOnly` makes the entire sheet read-only (e.g. a
 * player viewing another character). Field labels come from i18n keys in the definition.
 *
 * Note: v1 has no per-field "required" flag in the definition, so every editable field is marked
 * `required` here purely to suppress @rauboti/ui's "(optional)" label suffix — a future definition
 * enhancement (see plan.md "Deferred Decisions") would drive real requiredness.
 */
export type SheetRendererProps = {
  definition: SheetDefinition
  values: Record<string, unknown>
  onChange: (fieldId: string, value: unknown) => void
  readOnly?: boolean
}

export const SheetRenderer = ({
  definition,
  values,
  onChange,
  readOnly = false,
}: SheetRendererProps) => {
  const { t } = useTranslation()

  // Derived fields recompute live from the definition's formulas as inputs change, so a modifier
  // updates the moment you edit its ability (etc.). Display only — the server recomputes on save
  // (authoritative), so these never need persisting and any drift self-corrects on save.
  const displayValues = useMemo(
    () => ({ ...values, ...deriveValues(definition, values) }),
    [definition, values],
  )

  return (
    <Stack gap="6">
      {definition.sections.map((section) => {
        // Layout is definition-driven: `columns` fields per row on wider screens (single column on
        // mobile so nothing is cramped); a field may span several columns via `colSpan`.
        const columns = section.columns ?? 1
        return (
          <Card key={section.id}>
            <Stack gap="4">
              <Heading size="md">{t(section.labelKey)}</Heading>
              <Grid columns={{ base: 1, md: columns }} gap="4">
                {section.fields.map((field) => {
                  // A table is a full-width widget (its own inner grid of rows), so it always spans
                  // the whole section — otherwise it'd be squeezed into one cell of a multi-column
                  // section (e.g. the 4-column spellcasting section) and look cramped.
                  const span =
                    field.type === 'table'
                      ? columns
                      : Math.min(field.colSpan ?? 1, columns)
                  return (
                    <Grid.Item key={field.id} colSpan={{ base: 1, md: span }}>
                      <FieldWidget
                        field={field}
                        label={t(field.labelKey)}
                        value={displayValues[field.id]}
                        onChange={(next) => onChange(field.id, next)}
                        readOnly={readOnly}
                        sheetScope={displayValues}
                        ruleSetId={definition.ruleSetId}
                        t={t}
                      />
                    </Grid.Item>
                  )
                })}
              </Grid>
            </Stack>
          </Card>
        )
      })}
    </Stack>
  )
}

type WidgetProps = {
  field: SheetField
  label: string
  value: unknown
  onChange: (value: unknown) => void
  readOnly: boolean
  /** Sheet-level values + top-level derived — the scope a table row's per-row formulas resolve against. */
  sheetScope: Record<string, unknown>
  /** The rule set id — used to fetch a catalog-backed select's options (`optionsFrom`). */
  ruleSetId: string
  t: (key: string) => string
}

const FieldWidget = ({
  field,
  label,
  value,
  onChange,
  readOnly,
  sheetScope,
  ruleSetId,
  t,
}: WidgetProps) => {
  // Derived values are computed server-side (T017) — always display-only.
  if (field.type === 'derived') {
    return (
      <Input label={label} required value={asText(value)} readOnly disabled />
    )
  }

  if (field.type === 'table') {
    return (
      <TableWidget
        field={field}
        label={label}
        value={value}
        onChange={onChange}
        readOnly={readOnly}
        sheetScope={sheetScope}
        ruleSetId={ruleSetId}
        t={t}
      />
    )
  }

  switch (field.type) {
    case 'text':
      return (
        <Input
          label={label}
          required
          value={asText(value)}
          readOnly={readOnly}
          onChange={(e) => onChange(e.currentTarget.value)}
        />
      )

    case 'int':
      return (
        <Input
          label={label}
          required
          type="number"
          value={asText(value)}
          readOnly={readOnly}
          onChange={(e) => {
            const raw = e.currentTarget.value
            onChange(raw === '' ? null : Number(raw))
          }}
        />
      )

    case 'bool':
      return (
        <SegmentedControl
          label={label}
          required
          value={value === true ? 'true' : 'false'}
          items={[
            { value: 'true', label: t('common.yes') },
            { value: 'false', label: t('common.no') },
          ]}
          onValueChange={
            readOnly ? undefined : (next) => onChange(next === 'true')
          }
        />
      )

    case 'select': {
      const options = field.options ?? []
      // Read-only: show the chosen option's label (not its raw value) in a disabled field,
      // mirroring how a derived field renders — the Combobox has no read-only mode.
      if (readOnly) {
        const selected = options.find((option) => option.value === value)
        return (
          <Input
            label={label}
            required
            value={selected ? t(selected.labelKey) : ''}
            readOnly
            disabled
          />
        )
      }
      return (
        <Combobox
          label={label}
          required
          items={options.map((option) => ({
            value: option.value,
            label: t(option.labelKey),
          }))}
          value={
            value === null || value === undefined || value === ''
              ? []
              : [String(value)]
          }
          onValueChange={(values) => onChange(values[0] ?? null)}
        />
      )
    }

    case 'list':
      return (
        <ListWidget
          label={label}
          items={asStringList(value)}
          readOnly={readOnly}
          onChange={onChange}
          t={t}
        />
      )

    default:
      return null
  }
}

const ListWidget = ({
  label,
  items,
  readOnly,
  onChange,
  t,
}: {
  label: string
  items: string[]
  readOnly: boolean
  onChange: (value: unknown) => void
  t: (key: string) => string
}) => {
  const replaceAt = (index: number, next: string) =>
    onChange(items.map((item, i) => (i === index ? next : item)))
  const removeAt = (index: number) =>
    onChange(items.filter((_, i) => i !== index))

  return (
    <Stack gap="2">
      {items.map((item, index) => (
        <HStack key={index}>
          <Input
            label={`${label} ${index + 1}`}
            hideLabel
            required
            value={item}
            readOnly={readOnly}
            onChange={(e) => replaceAt(index, e.currentTarget.value)}
          />
          {!readOnly && (
            <IconButton
              aria-label={t('common.remove')}
              variant="outline"
              onClick={() => removeAt(index)}
            >
              ×
            </IconButton>
          )}
        </HStack>
      ))}
      {!readOnly && (
        <Button
          variant="outline"
          alignSelf="flex-start"
          onClick={() => onChange([...items, ''])}
        >
          {`${t('common.add')} — ${label}`}
        </Button>
      )}
    </Stack>
  )
}

/**
 * A repeating-group `table` field (T105). Rows come from the sheet value, or are seeded from the
 * definition's `presetRows` (canonical content) until the user first edits/materializes the table.
 * Each cell renders by its column type; **derived** columns are read-only and recomputed live per row
 * ([deriveRow]) against the row overlaid on the sheet scope; a **preset** cell (a value the definition
 * pinned in a preset row) is read-only too. Preset rows can't be removed; appended rows can.
 */
const TableWidget = ({
  field,
  label,
  value,
  onChange,
  readOnly,
  sheetScope,
  ruleSetId,
  t,
}: WidgetProps) => {
  const columns = field.columns ?? []
  const presetRows = (field.presetRows ?? []) as Array<Record<string, unknown>>
  const stored = Array.isArray(value)
    ? (value as Array<Record<string, unknown>>)
    : []
  const rows = stored.length > 0 ? stored : presetRows

  const setRows = (next: Array<Record<string, unknown>>) => onChange(next)
  const updateCell = (rowIndex: number, columnId: string, cell: unknown) =>
    setRows(
      rows.map((row, i) =>
        i === rowIndex ? { ...row, [columnId]: cell } : row,
      ),
    )
  const removeRow = (rowIndex: number) =>
    setRows(rows.filter((_, i) => i !== rowIndex))
  const updateRow = (rowIndex: number, patch: Record<string, unknown>) =>
    setRows(rows.map((row, i) => (i === rowIndex ? { ...row, ...patch } : row)))

  // A cell the definition pinned in a preset row (e.g. a skill's fixed key ability) is read-only.
  const isPresetCell = (rowIndex: number, columnId: string): boolean => {
    const pinned = presetRows[rowIndex]?.[columnId]
    return pinned !== undefined && pinned !== null && pinned !== ''
  }

  return (
    <Stack gap="3">
      {rows.map((row, rowIndex) => {
        const displayRow = { ...row, ...deriveRow(columns, row, sheetScope) }
        const removable = !readOnly && rowIndex >= presetRows.length
        return (
          <Card key={rowIndex}>
            <Stack gap="2">
              <Grid columns={{ base: 1, md: columns.length }} gap="3">
                {columns.map((column) => (
                  <Grid.Item key={column.id}>
                    <CellInput
                      column={column}
                      label={`${t(column.labelKey)} ${rowIndex + 1}`}
                      value={displayRow[column.id]}
                      readOnly={
                        readOnly ||
                        column.type === 'derived' ||
                        isPresetCell(rowIndex, column.id)
                      }
                      onChange={(cell) => updateCell(rowIndex, column.id, cell)}
                      // Picking a catalog option also fills sibling columns from its meta (e.g. a
                      // spell's level → a `level` column), so the row captures more than the id.
                      onPick={(value, meta) =>
                        updateRow(rowIndex, {
                          [column.id]: value,
                          ...pickMeta(meta, columns, column.id),
                        })
                      }
                      ruleSetId={ruleSetId}
                      // A catalog-backed column filters by another field's value (sheet-level).
                      filterValue={
                        column.optionsFrom
                          ? sheetScope[column.optionsFrom.filterBy]
                          : undefined
                      }
                      t={t}
                    />
                  </Grid.Item>
                ))}
              </Grid>
              {removable && (
                <Button
                  variant="outline"
                  alignSelf="flex-start"
                  onClick={() => removeRow(rowIndex)}
                >
                  {t('common.remove')}
                </Button>
              )}
            </Stack>
          </Card>
        )
      })}
      {!readOnly && (
        <Button
          variant="outline"
          alignSelf="flex-start"
          onClick={() => setRows([...rows, {}])}
        >
          {`${t('common.add')} — ${label}`}
        </Button>
      )}
    </Stack>
  )
}

/**
 * Options for a catalog-backed select (T113): fetches from the catalog endpoint keyed off the current
 * `filterValue` (e.g. the caster class); a blank filter or no `optionsFrom` yields none. Results are
 * cached per (ruleSet, catalog, filter) so the many rows of a table don't each re-fetch.
 */
const catalogCache = new Map<string, CatalogOption[]>()

const useCatalogOptions = (
  ruleSetId: string,
  optionsFrom: OptionsFrom | null | undefined,
  filterValue: unknown,
): CatalogOption[] => {
  const filter =
    typeof filterValue === 'string'
      ? filterValue
      : filterValue === null || filterValue === undefined
        ? ''
        : String(filterValue)
  const key = optionsFrom ? `${ruleSetId}/${optionsFrom.catalog}/${filter}` : ''
  // The async fetch result, tagged with the key it was fetched for (so a stale result from a previous
  // filter is ignored). The empty and cached cases are derived during render, not set in the effect.
  const [fetched, setFetched] = useState<{
    key: string
    options: CatalogOption[]
  } | null>(null)

  useEffect(() => {
    if (!optionsFrom || filter === '' || catalogCache.has(key)) return
    const controller = new AbortController()
    getCatalogOptions(ruleSetId, optionsFrom.catalog, filter, controller.signal)
      .then((opts) => {
        if (controller.signal.aborted) return
        catalogCache.set(key, opts)
        setFetched({ key, options: opts })
      })
      .catch(() => {
        if (!controller.signal.aborted) setFetched({ key, options: [] })
      })
    return () => controller.abort()
  }, [ruleSetId, optionsFrom, key, filter])

  if (!optionsFrom || filter === '') return []
  return catalogCache.get(key) ?? (fetched?.key === key ? fetched.options : [])
}

/** One table cell, rendered by its column type. Any read-only cell — a `derived` column, a preset
 *  (definition-pinned) cell, or a read-only sheet — renders as a uniform disabled value, mirroring how
 *  scalar derived fields display; editable cells use the type's input widget. A `select` column may
 *  draw its choices from a catalog (`optionsFrom`, fetched + filtered) instead of static `options`. */
const CellInput = ({
  column,
  label,
  value,
  readOnly,
  onChange,
  onPick,
  ruleSetId,
  filterValue,
  t,
}: {
  column: SheetColumn
  label: string
  value: unknown
  readOnly: boolean
  onChange: (value: unknown) => void
  /** For a catalog-backed select: called with the picked value + the option's meta, so the row can
   *  fill sibling columns (e.g. a spell's level). Falls back to [onChange] when absent. */
  onPick?: (value: string | null, meta: Record<string, unknown> | null) => void
  ruleSetId: string
  filterValue: unknown
  t: (key: string) => string
}) => {
  const catalogOptions = useCatalogOptions(
    ruleSetId,
    column.optionsFrom,
    filterValue,
  )
  // A select's items: literal labels from the catalog, or i18n labels from static options.
  const selectItems = column.optionsFrom
    ? catalogOptions.map((o) => ({ value: o.value, label: o.label }))
    : (column.options ?? []).map((o) => ({
        value: o.value,
        label: t(o.labelKey),
      }))

  if (readOnly || column.type === 'derived') {
    const selected =
      column.type === 'select'
        ? selectItems.find((item) => item.value === value)
        : undefined
    return (
      <Input
        label={label}
        required
        value={selected ? selected.label : asText(value)}
        readOnly
        disabled
      />
    )
  }
  switch (column.type) {
    case 'int':
      return (
        <Input
          label={label}
          required
          type="number"
          value={asText(value)}
          onChange={(e) => {
            const raw = e.currentTarget.value
            onChange(raw === '' ? null : Number(raw))
          }}
        />
      )
    case 'bool':
      return (
        <SegmentedControl
          label={label}
          required
          value={value === true ? 'true' : 'false'}
          items={[
            { value: 'true', label: t('common.yes') },
            { value: 'false', label: t('common.no') },
          ]}
          onValueChange={(next) => onChange(next === 'true')}
        />
      )
    case 'select':
      return (
        <Combobox
          label={label}
          required
          items={selectItems}
          value={
            value === null || value === undefined || value === ''
              ? []
              : [String(value)]
          }
          onValueChange={(values) => {
            const picked = values[0] ?? null
            if (column.optionsFrom && onPick) {
              const option = catalogOptions.find((o) => o.value === picked)
              onPick(picked, option?.meta ?? null)
            } else {
              onChange(picked)
            }
          }}
        />
      )
    default:
      return (
        <Input
          label={label}
          required
          value={asText(value)}
          onChange={(e) => onChange(e.currentTarget.value)}
        />
      )
  }
}

/** From a picked catalog option's `meta`, keep only the entries whose key is another column of the
 *  same table (excluding the picked column itself) — so e.g. a spell's `meta.level` fills a `level`
 *  column, but unrelated meta is ignored. */
const pickMeta = (
  meta: Record<string, unknown> | null,
  columns: SheetColumn[],
  pickedColumnId: string,
): Record<string, unknown> =>
  Object.fromEntries(
    Object.entries(meta ?? {}).filter(
      ([key]) =>
        key !== pickedColumnId && columns.some((column) => column.id === key),
    ),
  )

/** Render any scalar sheet value as input text; null/undefined → empty. */
const asText = (value: unknown): string =>
  value === null || value === undefined ? '' : String(value)

/** Coerce a sheet value to a string list for the list widget (non-lists → empty). */
const asStringList = (value: unknown): string[] =>
  Array.isArray(value) ? value.map((item) => String(item)) : []
