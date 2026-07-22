import { useEffect, useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Stack, Text } from '@chakra-ui/react'
import { Callout, EmptyState, List, PageHeader } from '@rauboti/ui'
import {
  listCharacters,
  type Character,
  type CharacterSummary,
} from '@/api/characters'
import { listRuleSets, type RuleSetSummary } from '@/api/schemas'
import { CreateCharacterDialog } from '@/components/characters/CreateCharacterDialog'

/**
 * The characters landing page (US1, T033): the caller's own characters plus a "New character"
 * dialog. Loads the list and the rule sets (for the create picker) on mount via the typed client;
 * a load failure shows a soft error, an empty list shows a prompt, and each row links to the
 * character's sheet (`/characters/:id`, T034). Creating a character prepends it and routes to its
 * sheet. Owner scoping is server-side — the list only ever returns the caller's characters.
 */
export const CharactersPage = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [characters, setCharacters] = useState<CharacterSummary[] | null>(null)
  const [ruleSets, setRuleSets] = useState<RuleSetSummary[]>([])
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      listCharacters(controller.signal),
      listRuleSets(controller.signal),
    ])
      .then(([chars, rs]) => {
        if (controller.signal.aborted) return
        setCharacters(chars)
        setRuleSets(rs)
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true)
      })
    return () => controller.abort()
  }, [])

  const handleCreated = (created: Character) => {
    setCharacters((prev) => [
      { id: created.id, name: created.name, ruleSetId: created.ruleSetId },
      ...(prev ?? []),
    ])
    navigate(`/characters/${created.id}`)
  }

  return (
    <Stack gap="6">
      <PageHeader
        title={t('nav.characters')}
        actions={
          <CreateCharacterDialog
            ruleSets={ruleSets}
            onCreated={handleCreated}
          />
        }
      />

      {failed && <Callout status="error">{t('characters.loadError')}</Callout>}

      {!failed && characters === null && (
        <Text color="text.muted">{t('common.loading')}</Text>
      )}

      {!failed && characters?.length === 0 && (
        <EmptyState>{t('characters.empty')}</EmptyState>
      )}

      {!failed && characters !== null && characters.length > 0 && (
        <List>
          {characters.map((character) => (
            <List.LinkItem key={character.id}>
              <RouterLink to={`/characters/${character.id}`}>
                {character.name}
              </RouterLink>
            </List.LinkItem>
          ))}
        </List>
      )}
    </Stack>
  )
}
