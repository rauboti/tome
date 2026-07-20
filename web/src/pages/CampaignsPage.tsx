import { PageHeader } from '@rauboti/ui'
import { useTranslation } from 'react-i18next'

/** Placeholder (T020): the campaign list + role-aware campaign view land with US2 (T044–T045). */
export const CampaignsPage = () => {
  const { t } = useTranslation()
  return <PageHeader title={t('nav.campaigns')} />
}
