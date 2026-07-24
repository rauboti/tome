import { z } from 'zod'
import { apiRequest } from './client'
import {catalogOptionSchema, type CatalogOption } from '@/api/types'

/**
 * Client for catalog-backed selects: a field's `optionsFrom` picker fetches choices from
 * `GET /api/rule-sets/{ruleSetId}/catalogs/{catalog}?filter={value}`. Content is data (e.g. SRD spell
 * names), so `label` is a literal display string, not an i18n key; `meta` carries optional per-option
 * data (e.g. a spell's level for the filtered class).
 */

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
