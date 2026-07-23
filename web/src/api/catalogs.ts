import { z } from 'zod'
import { apiRequest } from './client'

/**
 * Client for catalog-backed selects (T113): a field's `optionsFrom` picker fetches its choices from
 * `GET /api/rule-sets/{ruleSetId}/catalogs/{catalog}?filter={value}`. The catalog content is data
 * (e.g. SRD spell names), so `label` is a literal display string (not an i18n key); `meta` carries
 * optional per-option data (e.g. a spell's level for the filtered class).
 */
export const catalogOptionSchema = z.object({
  value: z.string(),
  label: z.string(),
  meta: z.record(z.string(), z.unknown()).nullish(),
})
export type CatalogOption = z.infer<typeof catalogOptionSchema>

export const getCatalogOptions = (
  ruleSetId: string,
  catalog: string,
  filter: string,
  signal?: AbortSignal,
): Promise<CatalogOption[]> =>
  apiRequest(
    `/rule-sets/${encodeURIComponent(ruleSetId)}/catalogs/${encodeURIComponent(catalog)}?filter=${encodeURIComponent(filter)}`,
    z.array(catalogOptionSchema),
    { signal },
  )
