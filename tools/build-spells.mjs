// T112 — build dnd35 spells.json from the OGL 3.5 SRD (d20srd.org) per-class spell-list pages.
// Reproducible: fetches the pages, parses each spell entry (a <strong> wrapping a /srd/spells/ link
// under an <h3> level header, with the <h4> school), and merges into per-class level maps.
import { writeFile } from 'node:fs/promises'

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36'
const BASE = 'https://www.d20srd.org/srd/spellLists/'
const PAGES = [
  { classes: ['sorcerer', 'wizard'], file: 'sorcererWizardSpells.htm' },
  { classes: ['cleric'], file: 'clericSpells.htm' },
  { classes: ['druid'], file: 'druidSpells.htm' },
  { classes: ['bard'], file: 'bardSpells.htm' },
  { classes: ['paladin'], file: 'paladinSpells.htm' },
  { classes: ['ranger'], file: 'rangerSpells.htm' },
]

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
  // level-only, so school isn't reliably available here — we don't record it (see notes).
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
const outPath = 'D:/Applications/Private/platform/tome/api/src/main/resources/rulesets/dnd35/spells.json'
await writeFile(outPath, JSON.stringify({ source: 'd20srd.org (OGL 3.5 SRD)', spells: list }, null, 2) + '\n', 'utf8')

console.log('per-class spell counts:', perClassCounts)
console.log('total unique spells:', list.length)
const show = (id) => { const s = spells.get(id); console.log(' ', id, '→', s ? JSON.stringify({ name: s.name, classLevels: s.classLevels }) : 'MISSING') }
console.log('spot checks:')
;['fireball', 'cureLightWounds', 'magicMissile', 'wish', 'bless', 'entangle', 'protectionFromChaos', 'bearsEndurance'].forEach(show)
