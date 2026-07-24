import { useState } from 'react'
import { Box, Flex, Grid, Heading, Stack, Text } from '@chakra-ui/react'
import { Button, Input } from '@rauboti/ui'

/**
 * A typed sheet table (ADR-001/T129) — the reusable editor for the D&D 3.5 repeating-group sections
 * (skills, attacks, feats, gear, spell slots, spells). Column headers render once; each cell is an
 * accessible control named `${column} ${rowIndex + 1}` (the convention the retired generic renderer
 * used, so cell-level queries stay stable — this also folds in the T115 "condensed layout" concern).
 *
 * Rows are the base-input row objects; derived columns are read-only and computed by the caller
 * (`column.derive`). Leading `presetCount` rows are canonical seed rows: their `presetLocked` cells are
 * read-only and those rows can't be removed; the user may append/remove further rows via `newRow`.
 */
export type SheetTableColumn = {
  id: string
  label: string
  kind: 'text' | 'number' | 'bool' | 'select' | 'derived'
  options?: ReadonlyArray<{ value: string; label: string }>
  /** For `derived` columns: compute the read-only value from the row + its index. */
  derive?: (row: Record<string, unknown>, index: number) => number
  /** For preset rows (index < presetCount): render this cell read-only. */
  presetLocked?: boolean
  /** Relative column width (grid fraction); default 1. */
  span?: number
}

export type SheetTableProps = {
  title: string
  columns: SheetTableColumn[]
  rows: ReadonlyArray<Record<string, unknown>>
  onChange: (rows: Record<string, unknown>[]) => void
  presetCount?: number
  newRow?: () => Record<string, unknown>
  addLabel?: string
}

const num = (v: unknown): number => (typeof v === 'number' ? v : Number(v ?? 0) || 0)
const str = (v: unknown): string => (v == null ? '' : String(v))

/** Number cell with local text state so clear()+type() stays clean (see NumberField in the sheet). */
export const NumberCell = ({
  label,
  value,
  onCommit,
}: {
  label: string
  value: number
  onCommit: (n: number) => void
}) => {
  const [text, setText] = useState(() => String(value))
  // Resync when the value changes from OUTSIDE (e.g. a catalog pick fills this cell) — but not from our
  // own commit (value === committed then), so active typing keeps its cursor.
  const [committed, setCommitted] = useState(value)
  if (value !== committed) {
    setCommitted(value)
    setText(String(value))
  }
  return (
    <Input
      label={label}
      aria-label={label}
      hideLabel
      type="number"
      value={text}
      onChange={(e) => {
        const raw = e.currentTarget.value
        const n = raw === '' ? 0 : Number(raw)
        setText(raw)
        setCommitted(n)
        onCommit(n)
      }}
    />
  )
}

export const SheetTable = ({
  title,
  columns,
  rows,
  onChange,
  presetCount = 0,
  newRow,
  addLabel,
}: SheetTableProps) => {
  const template = columns.map((c) => `${c.span ?? 1}fr`).join(' ') + (newRow ? ' auto' : '')
  const patch = (index: number, next: Record<string, unknown>) =>
    onChange(rows.map((r, i) => (i === index ? next : r)))
  const removeRow = (index: number) => onChange(rows.filter((_, i) => i !== index))

  const cell = (column: SheetTableColumn, row: Record<string, unknown>, rowIndex: number) => {
    // Table-scoped accessible name so cells stay unique across tables that share a column label
    // (e.g. both Skills and Spell Slots have a "Total"). Row-indexed per the retired renderer's convention.
    const name = `${title} ${column.label} ${rowIndex + 1}`
    const locked = rowIndex < presetCount && column.presetLocked
    const set = (value: unknown) => patch(rowIndex, { ...row, [column.id]: value })

    if (column.kind === 'derived') {
      return <Input label={name} aria-label={name} hideLabel value={String(column.derive?.(row, rowIndex) ?? 0)} readOnly disabled />
    }
    if (column.kind === 'number') {
      return <NumberCell label={name} value={num(row[column.id])} onCommit={set} />
    }
    if (column.kind === 'bool') {
      return (
        <input
          type="checkbox"
          aria-label={name}
          checked={row[column.id] === true}
          onChange={(e) => set(e.currentTarget.checked)}
        />
      )
    }
    if (column.kind === 'select') {
      return (
        <select
          aria-label={name}
          disabled={locked}
          value={str(row[column.id])}
          onChange={(e) => set(e.currentTarget.value)}
        >
          <option value="" />
          {(column.options ?? []).map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      )
    }
    // text
    return (
      <Input
        label={name}
        aria-label={name}
        hideLabel
        value={str(row[column.id])}
        readOnly={locked}
        disabled={locked}
        onChange={(e) => set(e.currentTarget.value)}
      />
    )
  }

  return (
    <Stack gap="3">
      <Heading size="md">{title}</Heading>
      <Grid templateColumns={template} gap="2" alignItems="center">
        {columns.map((c) => (
          <Text key={c.id} fontSize="xs" fontWeight="semibold" color="text.muted">
            {c.label}
          </Text>
        ))}
        {newRow && <Box />}
        {rows.map((row, rowIndex) => (
          <Box key={rowIndex} display="contents">
            {columns.map((c) => (
              <Box key={c.id}>{cell(c, row, rowIndex)}</Box>
            ))}
            {newRow && (
              <Box>
                {rowIndex >= presetCount && (
                  <Button variant="ghost" size="sm" onClick={() => removeRow(rowIndex)} aria-label={`Remove ${title} ${rowIndex + 1}`}>
                    ✕
                  </Button>
                )}
              </Box>
            )}
          </Box>
        ))}
      </Grid>
      {newRow && (
        <Flex>
          <Button variant="outline" size="sm" onClick={() => onChange([...rows, newRow()])}>
            {addLabel ?? 'Add row'}
          </Button>
        </Flex>
      )}
    </Stack>
  )
}
