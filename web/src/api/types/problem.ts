import { z } from 'zod'

/** RFC-7807 problem details (openapi `Problem`). Every field is optional so an unexpected error
 *  body still parses. */
export const problemSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  instance: z.string().optional(),
})
export type Problem = z.infer<typeof problemSchema>
