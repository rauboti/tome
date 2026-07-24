import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Callout, Combobox, Dialog, Input } from '@rauboti/ui'
import { createCharacter } from '@/api/characters'
import type { Character, RuleSetSummary } from '@/types'
import { defaultDnD35SheetInput } from '@/sheets/dnd35'
import { useSession } from '@/auth/SessionContext'

/**
 * The "New character" dialog (US1). Pick a rule set (v1 offers only D&D 3.5, but the picker is driven
 * by the server list so a future rule set is additive) and a name, then `POST /api/characters`. On
 * success it hands the created character back via [onCreated]; a failed create shows a soft error and
 * keeps the form open. Owner is the signed-in user, set server-side from the session.
 */
export type CreateCharacterDialogProps = {
  ruleSets: RuleSetSummary[]
  /** Called with the newly-created character after a successful create. */
  onCreated: (character: Character) => void
}

export const CreateCharacterDialog = ({
  ruleSets,
  onCreated,
}: CreateCharacterDialogProps) => {
  const { t } = useTranslation()
  const { user } = useSession()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [ruleSetId, setRuleSetId] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  // Fall back to the first available rule set until the user picks one explicitly.
  const selectedRuleSet = ruleSetId || ruleSets[0]?.id || ''

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmed = name.trim()
    if (trimmed === '' || selectedRuleSet === '') return

    setSubmitting(true)
    setFailed(false)
    try {
      // v1 ships D&D 3.5 only; its `ruleSetId` rides inside the typed base (ADR-001).
      const data = defaultDnD35SheetInput(trimmed)
      // The player is the signed-in owner — stamp id + display name now (fixed for the character's life).
      if (user) data.player = { id: user.userId, name: user.displayName ?? '' }
      const created = await createCharacter({ name: trimmed, data })
      setOpen(false)
      setName('')
      setRuleSetId('')
      onCreated(created)
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) setFailed(false)
      }}
      trigger={<Button>{t('characters.new')}</Button>}
      title={t('characters.new')}
      asForm
      onSubmit={handleSubmit}
      footer={
        <Button type="submit" loading={submitting}>
          {t('characters.form.create')}
        </Button>
      }
    >
      {failed && (
        <Callout status="error">{t('characters.form.createError')}</Callout>
      )}
      <Input
        label={t('characters.form.name')}
        required
        value={name}
        onChange={(event) => setName(event.currentTarget.value)}
      />
      <Combobox
        label={t('characters.form.ruleSet')}
        required
        items={ruleSets.map((ruleSet) => ({
          value: ruleSet.id,
          label: ruleSet.name,
        }))}
        value={selectedRuleSet === '' ? [] : [selectedRuleSet]}
        onValueChange={(values) => setRuleSetId(values[0] ?? '')}
      />
    </Dialog>
  )
}
