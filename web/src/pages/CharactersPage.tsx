import { PageHeader } from '@rauboti/ui'
import { useTranslation } from 'react-i18next'

/** Placeholder (T020): the character list + create dialog land with US1 (T033). */
export const CharactersPage = () => {
  const { t } = useTranslation()
  return <PageHeader title={t('nav.characters')} />
}
