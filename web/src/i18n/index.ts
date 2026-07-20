import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import nb from './nb.json'

/**
 * The bilingual foundation (FR-015, research D7): two bundles with identical key sets (asserted by
 * `i18n.test.ts`), active language driven by the user's Hive locale via [applyLocale], deterministic
 * English fallback. Chrome (nav, auth, common) is translated; D&D 3.5 game terminology under
 * `dnd35.*` stays canonical (English) in both bundles per the spec assumption — only its keys are
 * shared, not re-translated.
 */

export const SUPPORTED_LOCALES = ['nb', 'en'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

/** Narrow a `Me.locale` value to a language Tome offers; anything else (incl. null/undefined and
 *  region-tagged variants) is the deterministic English fallback (research D7). */
export const toSupportedLocale = (
  locale: string | null | undefined,
): SupportedLocale =>
  SUPPORTED_LOCALES.includes(locale as SupportedLocale)
    ? (locale as SupportedLocale)
    : 'en'

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    nb: { translation: nb },
  },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: {
    // React already escapes rendered strings.
    escapeValue: false,
  },
})

/**
 * Switch the active UI language from a `Me.locale` value: `nb`/`en` applied as-is, anything else →
 * English. Called by the SessionProvider on session bootstrap (research D7).
 */
export const applyLocale = async (
  locale: string | null | undefined,
): Promise<void> => {
  await i18n.changeLanguage(toSupportedLocale(locale))
}

export default i18n
