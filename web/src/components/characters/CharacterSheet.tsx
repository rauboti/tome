import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Stack, Text } from '@chakra-ui/react'
import { Button, Callout, PageHeader } from '@rauboti/ui'
import { ApiError } from '@/api/client'
import {
  getCharacter,
  updateCharacter,
  type Character,
  type RuleWarning,
  type SheetValues,
} from '@/api/characters'
import { getRuleSetDefinition, type SheetDefinition } from '@/api/schemas'
import { SheetRenderer } from '@/components/sheet/SheetRenderer'

/**
 * The character sheet edit screen (US1, T034). Loads the character and its rule-set definition over
 * the BFF, renders the definition-driven {@link SheetRenderer}, and saves the whole sheet with
 * optimistic concurrency:
 *  - derived values are owned by the engine, so the sheet shows the server's computed values and
 *    only re-derives on save (the response carries the recomputed `data`);
 *  - soft `warnings` from a write are surfaced (never blocking, FR-005);
 *  - a 409 means someone else saved first (SC-006) — shown as a conflict notice rather than
 *    silently dropping the edit.
 *
 * Takes a plain `characterId` (the route wrapper supplies it from the URL), so it renders in a test
 * without a router.
 */
export type CharacterSheetProps = {
  characterId: string
}

export const CharacterSheet = ({ characterId }: CharacterSheetProps) => {
  const { t } = useTranslation()
  const [character, setCharacter] = useState<Character | null>(null)
  const [definition, setDefinition] = useState<SheetDefinition | null>(null)
  const [values, setValues] = useState<SheetValues>({})
  const [version, setVersion] = useState(0)
  const [warnings, setWarnings] = useState<RuleWarning[]>([])
  const [loadFailed, setLoadFailed] = useState(false)
  const [saving, setSaving] = useState(false)
  const [conflict, setConflict] = useState<string | null>(null)
  const [saveFailed, setSaveFailed] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getCharacter(characterId, controller.signal)
      .then((loaded) => {
        if (controller.signal.aborted) return undefined
        setCharacter(loaded)
        setValues(loaded.data)
        setVersion(loaded.version)
        setWarnings(loaded.warnings)
        return getRuleSetDefinition(loaded.ruleSetId, controller.signal)
      })
      .then((def) => {
        if (def && !controller.signal.aborted) setDefinition(def)
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadFailed(true)
      })
    return () => controller.abort()
  }, [characterId])

  const handleFieldChange = (fieldId: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [fieldId]: value }))
  }

  const handleSave = async () => {
    setSaving(true)
    setConflict(null)
    setSaveFailed(false)
    try {
      // Keep the promoted name in sync with the sheet's name field (blank → keep the current name).
      const sheetName =
        typeof values.name === 'string' && values.name.trim() !== ''
          ? values.name
          : undefined
      const updated = await updateCharacter(characterId, {
        name: sheetName,
        data: values,
        version,
      })
      setCharacter(updated)
      setValues(updated.data)
      setVersion(updated.version)
      setWarnings(updated.warnings)
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setConflict(error.problem?.detail ?? t('characters.sheet.conflict'))
      } else {
        setSaveFailed(true)
      }
    } finally {
      setSaving(false)
    }
  }

  if (loadFailed) {
    return <Callout status="error">{t('characters.sheet.loadError')}</Callout>
  }

  if (character === null || definition === null) {
    return <Text color="text.muted">{t('common.loading')}</Text>
  }

  return (
    <Stack gap="6">
      <PageHeader
        title={character.name}
        actions={
          <Button onClick={() => void handleSave()} loading={saving}>
            {t('common.save')}
          </Button>
        }
      />

      {conflict !== null && <Callout status="error">{conflict}</Callout>}
      {saveFailed && (
        <Callout status="error">{t('characters.sheet.saveError')}</Callout>
      )}
      {warnings.length > 0 && (
        <Callout status="warning">
          <Stack gap="1">
            {warnings.map((warning, index) => (
              <Text key={`${warning.code}-${index}`}>{warning.message}</Text>
            ))}
          </Stack>
        </Callout>
      )}

      <SheetRenderer
        definition={definition}
        values={values}
        onChange={handleFieldChange}
      />
    </Stack>
  )
}
