import type { SheetColumn, SheetDefinition } from '@/api/schemas'

/**
 * Live client-side evaluation of a rule set's derived fields, from the `derivedFrom` formulas in its
 * [SheetDefinition] — so the sheet updates a modifier/save/total the moment its inputs change, with no
 * round-trip. It is a **display layer only**: the server recomputes every derived value on save
 * (`SheetCompute`/`FormulaEvaluator`), which is authoritative, so any drift between a formula here and
 * the server's logic is a transient preview that self-corrects on save — never persisted.
 *
 * The grammar here MUST stay in lockstep with the Kotlin `FormulaEvaluator` (that lockstep IS the
 * compute-on-read parity guarantee). Formulas are plain arithmetic over other field ids: `+ - * /`,
 * parentheses, unary minus, decimals, and the functions below, plus two structured-sheet primitives:
 *  - `sum(table.column)` — total a numeric column across a table field's rows;
 *  - `ref(field)` — the value of the field *named by* `field`'s value (per-row indirection, e.g. a
 *    skill row's `ref(keyAbility)` where `keyAbility` holds `"strMod"`).
 * An unknown identifier reads as `0` (matching the server default); any malformed formula is skipped.
 */

const FUNCS: Record<string, (args: number[]) => number> = {
  floor: ([x]) => Math.floor(x),
  ceil: ([x]) => Math.ceil(x),
  round: ([x]) => Math.round(x),
  abs: ([x]) => Math.abs(x),
  min: (args) => Math.min(...args),
  max: (args) => Math.max(...args),
}

/** Read a raw sheet value as a number; absent/null/non-numeric reads as 0 (matches the server). */
const asNumber = (value: unknown): number => {
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0
  if (typeof value === 'string' && value.trim() !== '') {
    const n = Number(value)
    return Number.isFinite(n) ? n : 0
  }
  return 0
}

/** Split a formula into number / identifier / operator tokens; an identifier may contain `.` so a
 *  `sum` argument like `gear.weight` is one token. Throws on any stray character. */
const tokenize = (expr: string): string[] => {
  const tokens: string[] = []
  const re = /([0-9]*\.?[0-9]+|[A-Za-z_][A-Za-z0-9_.]*|[+\-*/(),])|(\S)/g
  let match: RegExpExecArray | null
  while ((match = re.exec(expr)) !== null) {
    if (match[2] !== undefined)
      throw new Error(`unexpected character: ${match[2]}`)
    tokens.push(match[1])
  }
  return tokens
}

/**
 * Evaluate a single formula against `scope` (field id → raw value; missing ids read as 0). Numeric
 * identifiers are used directly; `ref` reads a string-named field; `sum` reads a table field (an array
 * of row objects). Returns the numeric result, or `null` if the formula is malformed / non-finite.
 */
export const evaluateFormula = (
  expr: string,
  scope: Record<string, unknown>,
): number | null => {
  let tokens: string[]
  try {
    tokens = tokenize(expr)
  } catch {
    return null
  }

  let pos = 0
  const peek = (): string | undefined => tokens[pos]
  const eat = (): string => tokens[pos++]
  const expect = (tok: string): void => {
    if (eat() !== tok) throw new Error(`expected ${tok}`)
  }

  // expr := term (('+' | '-') term)*
  const parseExpr = (): number => {
    let value = parseTerm()
    while (peek() === '+' || peek() === '-') {
      value = eat() === '+' ? value + parseTerm() : value - parseTerm()
    }
    return value
  }
  // term := factor (('*' | '/') factor)*
  const parseTerm = (): number => {
    let value = parseFactor()
    while (peek() === '*' || peek() === '/') {
      value = eat() === '*' ? value * parseFactor() : value / parseFactor()
    }
    return value
  }
  // factor := number | '-' factor | '(' expr ')' | call | identifier
  const parseFactor = (): number => {
    const tok = peek()
    if (tok === undefined) throw new Error('unexpected end of formula')
    if (tok === '-') {
      eat()
      return -parseFactor()
    }
    if (tok === '(') {
      eat()
      const value = parseExpr()
      expect(')')
      return value
    }
    if (/^[0-9.]/.test(tok)) {
      eat()
      return Number(tok)
    }
    if (/^[A-Za-z_]/.test(tok)) {
      eat()
      if (peek() === '(') return parseCall(tok)
      return asNumber(scope[tok])
    }
    throw new Error(`unexpected token ${tok}`)
  }
  // A function call `name(...)`. `ref`/`sum` take a single *name* argument, not an expression.
  const parseCall = (name: string): number => {
    expect('(')
    if (name === 'ref') {
      const argName = eat()
      expect(')')
      const targetName = scope[argName]
      return typeof targetName === 'string' ? asNumber(scope[targetName]) : 0
    }
    if (name === 'sum') {
      const ref = eat()
      expect(')')
      const dot = ref.indexOf('.')
      if (dot <= 0 || dot === ref.length - 1)
        throw new Error('sum expects table.column')
      const rows = scope[ref.slice(0, dot)]
      const column = ref.slice(dot + 1)
      if (!Array.isArray(rows)) return 0
      return rows.reduce(
        (total, row) =>
          total +
          asNumber(
            row !== null && typeof row === 'object'
              ? (row as Record<string, unknown>)[column]
              : undefined,
          ),
        0,
      )
    }
    const args: number[] = []
    if (peek() !== ')') {
      args.push(parseExpr())
      while (peek() === ',') {
        eat()
        args.push(parseExpr())
      }
    }
    expect(')')
    const fn = FUNCS[name]
    if (fn === undefined) throw new Error(`unknown function ${name}`)
    return fn(args)
  }

  try {
    const value = parseExpr()
    if (pos !== tokens.length) return null // trailing tokens ⇒ malformed
    return Number.isFinite(value) ? value : null
  } catch {
    return null
  }
}

/** Iterate the `derivedFrom` formulas in `fields` to a fixpoint against `scope`, returning the derived
 *  id → value map. Shared by top-level ([deriveValues]) and per-row ([deriveRow]) derivation. */
const deriveInScope = (
  fields: Array<{ id: string; derivedFrom?: string | null }>,
  scope: Record<string, unknown>,
): Record<string, number> => {
  const result: Record<string, number> = {}
  for (let pass = 0; pass <= fields.length; pass++) {
    let changed = false
    for (const field of fields) {
      const value = evaluateFormula(field.derivedFrom as string, {
        ...scope,
        ...result,
      })
      if (value !== null && result[field.id] !== value) {
        result[field.id] = value
        changed = true
      }
    }
    if (!changed) break
  }
  return result
}

/**
 * Compute every **top-level** derived field in `definition` from `values`, returning a map of derived
 * id → number. Derived fields may reference other derived fields (e.g. `initiative = dexMod`), so
 * evaluation iterates to a fixpoint. `sum(...)` fields read the raw table arrays straight from `values`.
 */
export const deriveValues = (
  definition: SheetDefinition,
  values: Record<string, unknown>,
): Record<string, number> => {
  const derived = definition.sections
    .flatMap((section) => section.fields)
    .filter(
      (field) =>
        field.type === 'derived' && typeof field.derivedFrom === 'string',
    )
  return deriveInScope(derived, values)
}

/**
 * Compute a table row's derived cells (id → number) from its `columns`, evaluating each derived column
 * against the row overlaid on the sheet-level scope (`sheetScope` = sheet values + top-level derived),
 * so a per-row formula can `ref(keyAbility)` a sheet-level modifier. Mirrors the server's per-row pass.
 */
export const deriveRow = (
  columns: SheetColumn[],
  row: Record<string, unknown>,
  sheetScope: Record<string, unknown>,
): Record<string, number> => {
  const derived = columns.filter(
    (column) =>
      column.type === 'derived' && typeof column.derivedFrom === 'string',
  )
  return deriveInScope(derived, { ...sheetScope, ...row })
}

/**
 * The base inputs of `values`: the sheet with every field the `definition` marks `derived` removed —
 * including, for `table` fields, each row's derived columns. Derived values are recomputed on read
 * (locally via [deriveValues]/[deriveRow] and on the server), so they are never persisted and never
 * sent on a write (D8); the client sends base inputs only. Mirrors the server-side strip.
 */
export const baseInputs = (
  definition: SheetDefinition,
  values: Record<string, unknown>,
): Record<string, unknown> => {
  const fields = definition.sections.flatMap((section) => section.fields)
  const derivedFieldIds = new Set(
    fields.filter((field) => field.type === 'derived').map((field) => field.id),
  )
  // For each table field, the ids of its derived columns (stripped from every row).
  const tableDerivedColumns = new Map<string, Set<string>>(
    fields
      .filter((field) => field.type === 'table')
      .map((field) => [
        field.id,
        new Set(
          (field.columns ?? [])
            .filter((column) => column.type === 'derived')
            .map((column) => column.id),
        ),
      ]),
  )

  return Object.fromEntries(
    Object.entries(values)
      .filter(([key]) => !derivedFieldIds.has(key))
      .map(([key, value]) => {
        const derivedColumns = tableDerivedColumns.get(key)
        if (derivedColumns && Array.isArray(value)) {
          const rows = value.map((row) =>
            row !== null && typeof row === 'object'
              ? Object.fromEntries(
                  Object.entries(row as Record<string, unknown>).filter(
                    ([columnId]) => !derivedColumns.has(columnId),
                  ),
                )
              : row,
          )
          return [key, rows]
        }
        return [key, value]
      }),
  )
}
