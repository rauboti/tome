import { z } from 'zod'

/** A rule set as shown in a picker (openapi `RuleSetSummary`). */
export const ruleSetSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
})
export type RuleSetSummary = z.infer<typeof ruleSetSummarySchema>
