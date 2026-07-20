import { Heading, HStack, IconButton, Stack } from '@chakra-ui/react'
import { Button, Card, Input, SegmentedControl, Select } from '@rauboti/ui'
import { useTranslation } from 'react-i18next'
import type { SheetDefinition, SheetField } from '@/api/schemas'

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

  return (
    <Stack gap="6">
      {definition.sections.map((section) => (
        <Card key={section.id}>
          <Stack gap="4">
            <Heading size="md">{t(section.labelKey)}</Heading>
            {section.fields.map((field) => (
              <FieldWidget
                key={field.id}
                field={field}
                label={t(field.labelKey)}
                value={values[field.id]}
                onChange={(next) => onChange(field.id, next)}
                readOnly={readOnly}
                t={t}
              />
            ))}
          </Stack>
        </Card>
      ))}
    </Stack>
  )
}

type WidgetProps = {
  field: SheetField
  label: string
  value: unknown
  onChange: (value: unknown) => void
  readOnly: boolean
  t: (key: string) => string
}

const FieldWidget = ({
  field,
  label,
  value,
  onChange,
  readOnly,
  t,
}: WidgetProps) => {
  // Derived values are computed server-side (T017) — always display-only.
  if (field.type === 'derived') {
    return (
      <Input label={label} required value={asText(value)} readOnly disabled />
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
          value={value === true ? 'true' : value === false ? 'false' : ''}
          items={[
            { value: 'true', label: t('common.yes') },
            { value: 'false', label: t('common.no') },
          ]}
          onValueChange={
            readOnly ? undefined : (next) => onChange(next === 'true')
          }
        />
      )

    case 'select':
      return (
        <Select
          label={label}
          required
          value={asText(value)}
          disabled={readOnly}
          onChange={(e) => onChange(e.currentTarget.value || null)}
        >
          <option value="">—</option>
          {(field.options ?? []).map((option) => (
            <option key={option.value} value={option.value}>
              {t(option.labelKey)}
            </option>
          ))}
        </Select>
      )

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

/** Render any scalar sheet value as input text; null/undefined → empty. */
const asText = (value: unknown): string =>
  value === null || value === undefined ? '' : String(value)

/** Coerce a sheet value to a string list for the list widget (non-lists → empty). */
const asStringList = (value: unknown): string[] =>
  Array.isArray(value) ? value.map((item) => String(item)) : []
