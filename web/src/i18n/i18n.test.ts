import { describe, expect, it } from 'vitest'
import i18n, { applyLocale } from '@/i18n'
import en from '@/i18n/en.json'
import nb from '@/i18n/nb.json'

/**
 * The bilingual foundation (FR-015, research D7): the active language follows the user's Hive
 * locale with a deterministic English fallback (null/undefined or anything but nb/en → en), and the
 * two bundles must stay structurally identical so no string can exist in one language only.
 */

/** Every leaf key of a (nested) bundle, dot-joined — `dnd35.field.name`, `auth.noAccess.title`. */
const keysOf = (bundle: Record<string, unknown>, prefix = ''): string[] =>
  Object.entries(bundle).flatMap(([key, value]) =>
    value !== null && typeof value === 'object'
      ? keysOf(value as Record<string, unknown>, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  )

describe('applyLocale', () => {
  it('falls back to English when the locale is null (never chosen)', async () => {
    await applyLocale(null)
    expect(i18n.language).toBe('en')
  })

  it('falls back to English on an unsupported locale', async () => {
    await applyLocale('de')
    expect(i18n.language).toBe('en')
    // Strictly nb/en — a region-tagged Norwegian is not one of Tome's offered languages.
    await applyLocale('nb-NO')
    expect(i18n.language).toBe('en')
  })

  it('applies nb and en and localizes chrome strings accordingly', async () => {
    await applyLocale('nb')
    expect(i18n.language).toBe('nb')
    expect(i18n.t('nav.campaigns')).toBe('Kampanjer')

    await applyLocale('en')
    expect(i18n.language).toBe('en')
    expect(i18n.t('nav.campaigns')).toBe('Campaigns')
  })
})

describe('bundles', () => {
  it('nb and en have identical key sets (FR-015 — no one-language strings)', () => {
    expect(keysOf(nb).sort()).toEqual(keysOf(en).sort())
  })

  it('has no empty strings in either bundle', () => {
    const leafValues = (bundle: Record<string, unknown>): unknown[] =>
      Object.values(bundle).flatMap((value) =>
        value !== null && typeof value === 'object'
          ? leafValues(value as Record<string, unknown>)
          : [value],
      )
    for (const bundle of [nb, en]) {
      for (const value of leafValues(bundle)) {
        expect(typeof value).toBe('string')
        expect((value as string).length).toBeGreaterThan(0)
      }
    }
  })
})
