import { z } from 'zod'

/** A soft validation finding (openapi `RuleWarning`): guidance, never a block. `field` is the
 *  offending field id, or `null` for a sheet-wide warning — `.nullish()` because the BFF serializes the
 *  Kotlin nullable as explicit `null`, which `.optional()` would reject. */
export const ruleWarningSchema = z.object({
  code: z.string(),
  field: z.string().nullish(),
  message: z.string(),
})

export type RuleWarning = z.infer<typeof ruleWarningSchema>
