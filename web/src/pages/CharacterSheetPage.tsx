import { useParams } from 'react-router'
import { CharacterSheet } from '@/components/characters/CharacterSheet'

/**
 * Route wrapper for the character sheet (`/characters/:characterId`, US1): reads the id from the URL
 * and renders the edit screen. Kept thin so [CharacterSheet] itself takes a plain `characterId` prop
 * and stays testable without a router.
 */
export const CharacterSheetPage = () => {
  const { characterId } = useParams()
  if (characterId === undefined) return null
  return <CharacterSheet characterId={characterId} />
}
