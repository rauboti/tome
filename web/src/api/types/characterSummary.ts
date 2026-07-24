import { z } from 'zod'

/** A character as shown in a list (openapi `CharacterSummary`). */
export const characterSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  ruleSetId: z.string(),
})
export type CharacterSummary = z.infer<typeof characterSummarySchema>
