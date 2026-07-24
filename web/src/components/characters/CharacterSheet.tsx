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
} from '@/api/characters'
import { toDnD35Base, type DnD35SheetInput } from '@/sheets/dnd35'
import { DnD35CharacterSheet } from './DnD35CharacterSheet'

/**
 * The character sheet edit screen (US1; retyped for the base/enriched engine, ADR-001/T126). Loads
 * the character over the BFF, holds the **base inputs** as an editable draft, and renders the typed
 * {@link DnD35CharacterSheet} (which shows derived values recomputed live). Saves with optimistic
 * concurrency:
 *  - derived values are computed on read (D8): the editor recomputes them locally for instant feedback
 *    and sends **base inputs only**; the server's enriched response is authoritative on load and save;
 *  - soft `warnings` from a write are surfaced (never blocking, FR-005);
 *  - a 409 means someone else saved first (SC-006) — shown as a conflict notice.
 *
 * Takes a plain `characterId` (the route wrapper supplies it), so it renders in a test without a router.
 */
export type CharacterSheetProps = {
  characterId: string
}

export const CharacterSheet = ({ characterId }: CharacterSheetProps) => {
  const { t } = useTranslation()
  const [character, setCharacter] = useState<Character | null>(null)
  const [base, setBase] = useState<DnD35SheetInput | null>(null)
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
        if (controller.signal.aborted) return
        setCharacter(loaded)
        setBase(toDnD35Base(loaded.data))
        setVersion(loaded.version)
        setWarnings(loaded.warnings)
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadFailed(true)
      })
    return () => controller.abort()
  }, [characterId])

  const handleSave = async () => {
    if (base === null) return
    setSaving(true)
    setConflict(null)
    setSaveFailed(false)
    try {
      const sheetName = base.name.trim() !== '' ? base.name : undefined
      // Send base inputs only — the server enriches (derived) on read; nothing derived is persisted (D8).
      const updated = await updateCharacter(characterId, {
        name: sheetName,
        data: base,
        version,
      })
      setCharacter(updated)
      setBase(toDnD35Base(updated.data))
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

  if (character === null || base === null) {
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

      <DnD35CharacterSheet base={base} onChange={setBase} />
    </Stack>
  )
}
