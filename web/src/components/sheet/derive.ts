import type { SheetDefinition } from '@/api/schemas'

/**
 * Live client-side evaluation of a rule set's derived fields, from the `derivedFrom` formulas in its
 * [SheetDefinition] — so the sheet updates a modifier/save/etc. the moment its inputs change, with no
 * round-trip. It is a **display layer only**: the server recomputes every derived value on save
 * (`RuleSet.computeDerived`), which is authoritative, so any drift between a formula here and the
 * server's logic is a transient preview that self-corrects on save — never persisted.
 *
 * Rule-set-agnostic: it evaluates whatever formulas the definition carries, so a new rule set is just
 * a new definition — no change here (FR-023). Formulas are plain arithmetic over other field ids:
 * `+ - * /`, parentheses, unary minus, decimals, and the functions below. An unknown identifier reads
 * as `0` (matching the server's `intOf` default), and any malformed/non-numeric formula is skipped.
 */

const FUNCS: Record<string, (args: number[]) => number> = {
  floor: ([x]) => Math.floor(x),
  ceil: ([x]) => Math.ceil(x),
  round: ([x]) => Math.round(x),
  abs: ([x]) => Math.abs(x),
  min: (args) => Math.min(...args),
  max: (args) => Math.max(...args),
}

/** Split a formula into number / identifier / operator tokens; throws on any stray character. */
const tokenize = (expr: string): string[] => {
  const tokens: string[] = []
  const re = /([0-9]*\.?[0-9]+|[A-Za-z_][A-Za-z0-9_]*|[+\-*/(),])|(\S)/g
  let match: RegExpExecArray | null
  while ((match = re.exec(expr)) !== null) {
    if (match[2] !== undefined)
      throw new Error(`unexpected character: ${match[2]}`)
    tokens.push(match[1])
  }
  return tokens
}

/**
 * Evaluate a single arithmetic formula against `scope` (field id → number; missing ids read as 0).
 * Returns the numeric result, or `null` if the formula is malformed or does not yield a finite number.
 */
export const evaluateFormula = (
  expr: string,
  scope: Record<string, number>,
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
  // factor := number | '-' factor | '(' expr ')' | func '(' args? ')' | identifier
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
      if (peek() === '(') {
        eat()
        const args: number[] = []
        if (peek() !== ')') {
          args.push(parseExpr())
          while (peek() === ',') {
            eat()
            args.push(parseExpr())
          }
        }
        expect(')')
        const fn = FUNCS[tok]
        if (fn === undefined) throw new Error(`unknown function ${tok}`)
        return fn(args)
      }
      return scope[tok] ?? 0
    }
    throw new Error(`unexpected token ${tok}`)
  }

  try {
    const value = parseExpr()
    if (pos !== tokens.length) return null // trailing tokens ⇒ malformed
    return Number.isFinite(value) ? value : null
  } catch {
    return null
  }
}

/**
 * Compute every derived field in `definition` from `values`, returning a map of derived id → number.
 * Derived fields may reference other derived fields (e.g. `initiative = dexMod`, `dexMod` itself
 * derived), so evaluation iterates to a fixpoint — bounded by the number of derived fields, which
 * also caps any accidental cyclic formula.
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

  // Numeric view of the raw inputs; non-numeric values are omitted (read as 0 by the evaluator).
  const base: Record<string, number> = {}
  for (const [key, value] of Object.entries(values)) {
    const n =
      typeof value === 'number'
        ? value
        : typeof value === 'string' && value.trim() !== ''
          ? Number(value)
          : NaN
    if (Number.isFinite(n)) base[key] = n
  }

  const result: Record<string, number> = {}
  for (let pass = 0; pass <= derived.length; pass++) {
    let changed = false
    for (const field of derived) {
      const value = evaluateFormula(field.derivedFrom as string, {
        ...base,
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
