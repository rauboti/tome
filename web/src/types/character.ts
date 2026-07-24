import { z } from 'zod'
import { characterSummarySchema } from '@/api/types/characterSummary.ts'
import { ruleWarningSchema } from './ruleWarning.ts'
import { sheetSchema, type Sheet } from '@/api/types/types.ts'

/** A full character (openapi `Character`): summary plus owner, the enriched sheet `data`, soft
 *  `warnings`, and the `version` echoed back on the next write (optimistic concurrency).
 *  HP lives inside `data.hitPoints` — no promoted top-level HP. */
export const characterSchema = characterSummarySchema.extend({
  ownerId: z.string(),
  data: sheetSchema,
  warnings: z.array(ruleWarningSchema),
  version: z.number().int(),
})
export type Character = Omit<z.infer<typeof characterSchema>, 'data'> & { data: Sheet }
