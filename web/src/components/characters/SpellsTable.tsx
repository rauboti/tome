import { useEffect, useState } from 'react'
import { Box, Flex, Grid, Heading, Stack, Text } from '@chakra-ui/react'
import { Button, Input } from '@rauboti/ui'
import { getCatalogOptions, type CatalogOption } from '@/api/catalogs'
import { NumberCell } from './SheetTable'
import type { DnD35SpellRow } from '@/sheets/dnd35'

/**
 * The spellcasting spells table (ADR-001/T129) — the one catalog-backed section. Each row's `spell`
 * is a **class-filtered** picker: it fetches options from `GET /rule-sets/{id}/catalogs/spells?filter=
 * {casterClass}` (survives the typed pivot, T113/T114); picking a spell auto-fills `level` from the
 * option's `meta.level` for that class. Rows are user-added. Kept separate from the generic
 * {@link SheetTable} because of the async, class-filtered fetch.
 */
export type SpellsTableProps = {
  title: string
  ruleSetId: string
  casterClass: string
  rows: ReadonlyArray<DnD35SpellRow>
  onChange: (rows: DnD35SpellRow[]) => void
}

const CatalogSpellSelect = ({
  label,
  ruleSetId,
  filter,
  value,
  onPick,
}: {
  label: string
  ruleSetId: string
  filter: string
  value: string
  onPick: (value: string, meta: Record<string, unknown> | null) => void
}) => {
  const [fetched, setFetched] = useState<CatalogOption[]>([])
  const hasFilter = filter.trim() !== ''
  useEffect(() => {
    if (!hasFilter) return
    const controller = new AbortController()
    getCatalogOptions(ruleSetId, 'spells', filter, controller.signal)
      .then((opts) => {
        if (!controller.signal.aborted) setFetched(opts)
      })
      .catch(() => {
        // Leave the last options; a blank filter hides them via `options` below.
      })
    return () => controller.abort()
  }, [ruleSetId, filter, hasFilter])
  // Derived (no setState in the effect body): no filter → no choices.
  const options = hasFilter ? fetched : []

  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => {
        const picked = options.find((o) => o.value === e.currentTarget.value)
        onPick(e.currentTarget.value, picked?.meta ?? null)
      }}
    >
      <option value="" />
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  )
}

export const SpellsTable = ({ title, ruleSetId, casterClass, rows, onChange }: SpellsTableProps) => {
  const patch = (index: number, next: DnD35SpellRow) => onChange(rows.map((r, i) => (i === index ? next : r)))
  const blank = (): DnD35SpellRow => ({ spell: '', level: 0, prepared: 0, notes: '' })

  return (
    <Stack gap="3">
      <Heading size="md">{title}</Heading>
      {casterClass.trim() === '' && (
        <Text fontSize="sm" color="text.muted">
          Set a caster class above to pick spells from its list.
        </Text>
      )}
      <Grid templateColumns="2fr 1fr 1fr 2fr auto" gap="2" alignItems="center">
        {['Spell', 'Level', 'Prepared', 'Notes'].map((h) => (
          <Text key={h} fontSize="xs" fontWeight="semibold" color="text.muted">
            {h}
          </Text>
        ))}
        <Box />
        {rows.map((row, i) => (
          <Box key={i} display="contents">
            <Box>
              <CatalogSpellSelect
                label={`${title} Spell ${i + 1}`}
                ruleSetId={ruleSetId}
                filter={casterClass}
                value={row.spell}
                onPick={(value, meta) =>
                  patch(i, {
                    ...row,
                    spell: value,
                    level: typeof meta?.level === 'number' ? (meta.level as number) : row.level,
                  })
                }
              />
            </Box>
            <Box>
              <NumberCell label={`${title} Level ${i + 1}`} value={row.level} onCommit={(n) => patch(i, { ...row, level: n })} />
            </Box>
            <Box>
              <NumberCell label={`${title} Prepared ${i + 1}`} value={row.prepared} onCommit={(n) => patch(i, { ...row, prepared: n })} />
            </Box>
            <Box>
              <Input
                label={`${title} Notes ${i + 1}`}
                aria-label={`${title} Notes ${i + 1}`}
                hideLabel
                value={row.notes}
                onChange={(e) => patch(i, { ...row, notes: e.currentTarget.value })}
              />
            </Box>
            <Box>
              <Button variant="ghost" size="sm" aria-label={`Remove ${title} ${i + 1}`} onClick={() => onChange(rows.filter((_, j) => j !== i))}>
                ✕
              </Button>
            </Box>
          </Box>
        ))}
      </Grid>
      <Flex>
        <Button variant="outline" size="sm" onClick={() => onChange([...rows, blank()])}>
          Add spell
        </Button>
      </Flex>
    </Stack>
  )
}
