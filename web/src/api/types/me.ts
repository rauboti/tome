import { z } from 'zod'

/** `GET /api/auth/me` (openapi). Roles/locale are permissive strings the app only displays; the
 *  admin/user gate is server-side. Locale renders English when null or unsupported.
 *  `displayName`/`locale` are `.nullish()`, not `.optional()`: the BFF serializes an absent claim
 *  as explicit `null`, which `.optional()` would reject. */
export const meSchema = z.object({
  userId: z.string(),
  displayName: z.string().nullish(),
  roles: z.array(z.string()),
  locale: z.string().nullish(),
})
export type Me = z.infer<typeof meSchema>
