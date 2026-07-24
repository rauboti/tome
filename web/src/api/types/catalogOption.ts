import { z } from 'zod'

export const catalogOptionSchema = z.object({
  value: z.string(),
  label: z.string(),
  meta: z.record(z.string(), z.unknown()).nullish(),
})
export type CatalogOption = z.infer<typeof catalogOptionSchema>
