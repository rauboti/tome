// T112 — build dnd35 spells.json from the OGL 3.5 SRD (d20srd.org) per-class spell-list pages.
// Reproducible: fetches the pages, parses each spell entry (a <strong> wrapping a /srd/spells/ link
// under an <h3> level header), and merges into per-class level maps.
//
// T117 — a second pass then enriches each spell with its school. The class-list pages can't supply it
// (only the arcane list groups by school), so this fetches each spell's own SRD page and reads the
// `<h4>` school line under the `<h1>` title: "Enchantment (Compulsion) [Mind-Affecting]" →
// school/subschool/descriptors. ~591 extra requests, so the pass is throttled and retried.
//
// Sourced, never hand-authored: a spell whose school can't be parsed and validated against the nine
// 3.5 schools is reported and **aborts the build** rather than being guessed or left blank — the file
// is never written half-enriched.
import { writeFile } from 'node:fs/promises'

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36'
const BASE = 'https://www.d20srd.org/srd/spellLists/'
const SPELL_BASE = 'https://www.d20srd.org/srd/spells/'
const PAGES = [
  { classes: ['sorcerer', 'wizard'], file: 'sorcererWizardSpells.htm' },
  { classes: ['cleric'], file: 'clericSpells.htm' },
  { classes: ['druid'], file: 'druidSpells.htm' },
  { classes: ['bard'], file: 'bardSpells.htm' },
  { classes: ['paladin'], file: 'paladinSpells.htm' },
  { classes: ['ranger'], file: 'rangerSpells.htm' },
]

// The nine 3.5 schools — the parser validates against these, so a mis-parsed page fails loudly.
const SCHOOLS = new Set([
  'Abjuration',
  'Conjuration',
  'Divination',
  'Enchantment',
  'Evocation',
  'Illusion',
  'Necromancy',
  'Transmutation',
  'Universal',
])
// Known 3.5 descriptors — used only to *report* unexpected bracket content (the SRD sometimes puts
// prose there, e.g. summon monster III's "[see text for summon monster I]"), never to filter it.
const KNOWN_DESCRIPTORS = new Set([
  'Acid',
  'Air',
  'Chaotic',
  'Cold',
  'Darkness',
  'Death',
  'Earth',
  'Electricity',
  'Evil',
  'Fear',
  'Fire',
  'Force',
  'Good',
  'Language-Dependent',
  'Language Dependent', // the SRD prints it both ways
  'Lawful',
  'Light',
  'Mind-Affecting',
  'Sonic',
  'Water',
])
const SCHOOL_CONCURRENCY = 4 // be a polite guest on a community SRD mirror
const SCHOOL_RETRIES = 3

const stripTags = (s) => s.replace(/<[^>]+>/g, '')
const decode = (s) =>
  s
    .replace(/&rsquo;|&#8217;|&#x2019;/g, "'")
    .replace(/&ndash;|&#8211;/g, '–')
    .replace(/&mdash;|&#8212;/g, '—')
    .replace(/&amp;/g, '&')
    .replace(/&nbsp;/g, ' ')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/’/g, "'")
    .trim()
const cleanName = (raw) =>
  decode(stripTags(raw))
    .replace(/:\s*$/, '') // trailing colon
    .replace(/\s+[MFX](\s+[MFX])*$/, '') // component/marker letters (M/F/X)
    .trim()
const parseLevel = (headerText) => {
  const m = stripTags(headerText).match(/^\s*(\d+)(?:st|nd|rd|th)?-Level/i)
  return m ? Number(m[1]) : null
}

async function parsePage(file) {
  const res = await fetch(BASE + file, { headers: { 'User-Agent': UA } })
  if (!res.ok) throw new Error(`${file} → HTTP ${res.status}`)
  const html = await res.text()
  // NB: only the arcane (Sorcerer/Wizard) list groups by school (<h4>); the divine lists are
  // level-only, so school isn't available here — it comes from each spell's own page (T117, below).
  const token = /<h3[^>]*>(.*?)<\/h3>|<strong>(.*?)<\/strong>/gis
  const byLevel = {} // level -> [{id,name}]
  let level = null
  let m
  while ((m = token.exec(html)) !== null) {
    if (m[1] !== undefined) {
      const lv = parseLevel(m[1])
      if (lv !== null) { level = lv; byLevel[lv] ??= [] }
    } else if (m[2] !== undefined && level !== null) {
      const inner = m[2]
      const link = inner.match(/\/srd\/spells\/([A-Za-z0-9_]+)\.htm/)
      if (!link) continue
      const id = link[1]
      const name = cleanName(inner)
      if (id && name) byLevel[level].push({ id, name })
    }
  }
  return byLevel
}

const spells = new Map() // id -> { id, name, school, classLevels }
const perClassCounts = {}

for (const { classes, file } of PAGES) {
  const byLevel = await parsePage(file)
  const flat = Object.entries(byLevel).flatMap(([lv, arr]) => arr.map((s) => ({ ...s, level: Number(lv) })))
  for (const cls of classes) perClassCounts[cls] = flat.length
  for (const s of flat) {
    let entry = spells.get(s.id)
    if (!entry) { entry = { id: s.id, name: s.name, classLevels: {} }; spells.set(s.id, entry) }
    for (const cls of classes) entry.classLevels[cls] = s.level
  }
}

const list = [...spells.values()].sort((a, b) => a.name.localeCompare(b.name))

// ---- T117: school enrichment (one fetch per spell page) ----------------------------------------

/** Split a school line — "Conjuration (Creation) [Acid]" → school/subschool/descriptors. */
function parseSchoolLine(raw) {
  const text = decode(stripTags(raw))
  const descriptors = [...text.matchAll(/\[([^\]]+)\]/g)]
    .flatMap((m) => m[1].split(/[,;]/).map((d) => d.trim()))
    .filter(Boolean)
  const noDesc = text.replace(/\[[^\]]*\]/g, ' ')
  const sub = noDesc.match(/\(([^)]+)\)/)
  return {
    school: noDesc.replace(/\([^)]*\)/g, ' ').replace(/\s+/g, ' ').trim(),
    subschool: sub ? sub[1].trim() : null,
    descriptors,
    line: text,
  }
}

/**
 * Fetch one spell's page and read its school line: the first `<h4>` after the `<h1>` title. Some pages
 * carry a footnote between the two, so the match tolerates intervening markup — the school-name
 * validation in the caller is what proves the right `<h4>` was found.
 */
async function fetchSchool(id) {
  let lastError = 'unknown'
  for (let attempt = 1; attempt <= SCHOOL_RETRIES; attempt++) {
    try {
      const res = await fetch(`${SPELL_BASE}${id}.htm`, { headers: { 'User-Agent': UA } })
      if (!res.ok) {
        lastError = `HTTP ${res.status}`
      } else {
        const m = (await res.text()).match(/<h1[^>]*>(.*?)<\/h1>[\s\S]*?<h4[^>]*>(.*?)<\/h4>/i)
        if (!m) lastError = 'no <h1>…<h4> school line'
        else return { ...parseSchoolLine(m[2]), pageName: decode(stripTags(m[1])) }
      }
    } catch (e) {
      lastError = e.message
    }
    if (attempt < SCHOOL_RETRIES) await new Promise((r) => setTimeout(r, 400 * attempt))
  }
  return { error: lastError }
}

/** Run `worker` over `items` with at most `limit` in flight. */
async function pooled(items, limit, worker) {
  let next = 0
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) await worker(items[next++])
  })
  await Promise.all(runners)
}

const failures = []
const nameMismatches = []
const oddDescriptors = []
let done = 0

await pooled(list, SCHOOL_CONCURRENCY, async (entry) => {
  const got = await fetchSchool(entry.id)
  if (got.error || !SCHOOLS.has(got.school)) {
    failures.push(`${entry.id} — ${got.error ?? `unrecognised school "${got.school}" (line: "${got.line}")`}`)
  } else {
    entry.school = got.school
    if (got.subschool) entry.subschool = got.subschool
    if (got.descriptors.length) entry.descriptors = got.descriptors
    const norm = (s) => s.toLowerCase().replace(/[^a-z]/g, '')
    if (norm(got.pageName) !== norm(entry.name)) nameMismatches.push(`${entry.id}: list "${entry.name}" vs page "${got.pageName}"`)
    for (const d of got.descriptors) if (!KNOWN_DESCRIPTORS.has(d)) oddDescriptors.push(`${entry.id}: [${d}]`)
  }
  if (++done % 100 === 0) console.log(`  …schools: ${done}/${list.length}`)
})

if (failures.length) {
  console.error(`\n${failures.length} spell(s) had no verifiable school — refusing to write a half-enriched catalog:`)
  failures.forEach((f) => console.error('  ✗', f))
  process.exit(1)
}

// Re-key so `school`/`subschool`/`descriptors` sit next to `name`, before the class levels.
const enriched = list.map(({ id, name, school, subschool, descriptors, classLevels }) => ({
  id,
  name,
  school,
  ...(subschool ? { subschool } : {}),
  ...(descriptors ? { descriptors } : {}),
  classLevels,
}))

const outPath = 'D:/Applications/Private/platform/tome/api/src/main/resources/rulesets/dnd35/spells.json'
await writeFile(outPath, JSON.stringify({ source: 'd20srd.org (OGL 3.5 SRD)', spells: enriched }, null, 2) + '\n', 'utf8')

console.log('per-class spell counts:', perClassCounts)
console.log('total unique spells:', enriched.length)
const bySchool = {}
for (const s of enriched) bySchool[s.school] = (bySchool[s.school] ?? 0) + 1
console.log('by school:', bySchool)
if (nameMismatches.length) {
  console.log(`\nname differs between list page and spell page (${nameMismatches.length}, review — not fatal):`)
  nameMismatches.forEach((n) => console.log('  ?', n))
}
if (oddDescriptors.length) {
  console.log(`\nbracket content outside the known descriptor set (${oddDescriptors.length}, kept verbatim as the SRD prints it):`)
  oddDescriptors.forEach((d) => console.log('  ?', d))
}
const show = (id) => {
  const s = enriched.find((e) => e.id === id)
  console.log(' ', id, '→', s ? JSON.stringify({ name: s.name, school: s.school, subschool: s.subschool, descriptors: s.descriptors, classLevels: s.classLevels }) : 'MISSING')
}
console.log('\nspot checks:')
;['fireball', 'cureLightWounds', 'magicMissile', 'wish', 'bless', 'entangle', 'protectionFromChaos', 'bearsEndurance'].forEach(show)
