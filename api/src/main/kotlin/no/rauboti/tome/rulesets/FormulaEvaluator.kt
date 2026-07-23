package no.rauboti.tome.rulesets

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The shared, rule-set-agnostic formula evaluator behind compute-on-read (D8, T105). It evaluates a
 * definition field's `derivedFrom` expression against a scope of sheet values, so every derived value
 * is authored **once** (in the [SheetDefinition]) and computed identically here (server-authoritative)
 * and in the web `derive.ts` (client live preview). Keeping the two in lockstep **is** the parity
 * guarantee — any change to this grammar MUST be mirrored in `web/src/components/sheet/derive.ts`.
 *
 * Grammar: `+ - * /`, parentheses, unary minus, decimals, and the functions
 * `floor | ceil | round | abs | min | max`. Two structured-sheet primitives (T105):
 *  - `sum(table.column)` — total a numeric column across a table field's rows (e.g. gear weight);
 *  - `ref(field)` — **indirection**: the value of the field *named by* `field`'s value (e.g. a skill
 *    row's `ref(keyAbility)` where `keyAbility` holds `"strMod"`), so per-row totals resolve the right
 *    modifier without coupling this shared grammar to any rule set's semantics.
 *
 * An unknown identifier reads as `0` (matching the web default). A malformed expression yields `null`
 * (never throws) so one bad formula can never break a whole sheet.
 */
object FormulaEvaluator {
    /**
     * Evaluate [expr] against [scope] (field id → raw sheet value). Numeric identifiers are used
     * directly; `ref` reads a string-named field; `sum` reads a table field (a list of row maps).
     * Returns the numeric result, or `null` if the formula is malformed / non-finite.
     */
    fun evaluate(
        expr: String,
        scope: Map<String, Any?>,
    ): Double? {
        val tokens =
            try {
                tokenize(expr)
            } catch (_: IllegalArgumentException) {
                return null
            }
        return try {
            val parser = Parser(tokens, scope)
            val value = parser.parseExpr()
            if (!parser.atEnd()) return null // trailing tokens ⇒ malformed
            if (value.isFinite()) value else null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /** Read a raw sheet value as a Double; absent/null/non-numeric reads as 0.0. */
    private fun asNumber(value: Any?): Double =
        when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    /**
     * Split a formula into number / identifier / operator tokens; an identifier may contain `.` so a
     * `sum` argument like `gear.weight` is one token. Throws on any stray character.
     */
    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        val re = Regex("([0-9]*\\.?[0-9]+|[A-Za-z_][A-Za-z0-9_.]*|[+\\-*/(),])|(\\S)")
        for (match in re.findAll(expr)) {
            val stray = match.groupValues[2]
            if (stray.isNotEmpty()) throw IllegalArgumentException("unexpected character: $stray")
            tokens += match.groupValues[1]
        }
        return tokens
    }

    private val FUNCS: Map<String, (List<Double>) -> Double> =
        mapOf(
            "floor" to { a -> floor(a[0]) },
            "ceil" to { a -> ceil(a[0]) },
            // Match JS Math.round (half rounds toward +∞): floor(x + 0.5).
            "round" to { a -> floor(a[0] + 0.5) },
            "abs" to { a -> abs(a[0]) },
            "min" to { a -> a.min() },
            "max" to { a -> a.max() },
        )

    /** Recursive-descent parser/evaluator; mirrors the grammar in the web `derive.ts`. */
    private class Parser(
        private val tokens: List<String>,
        private val scope: Map<String, Any?>,
    ) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= tokens.size

        private fun peek(): String? = tokens.getOrNull(pos)

        private fun eat(): String = tokens.getOrNull(pos++) ?: error("unexpected end of formula")

        private fun expect(tok: String) {
            if (eat() != tok) error("expected $tok")
        }

        // expr := term (('+' | '-') term)*
        fun parseExpr(): Double {
            var value = parseTerm()
            while (peek() == "+" || peek() == "-") {
                value = if (eat() == "+") value + parseTerm() else value - parseTerm()
            }
            return value
        }

        // term := factor (('*' | '/') factor)*
        private fun parseTerm(): Double {
            var value = parseFactor()
            while (peek() == "*" || peek() == "/") {
                value = if (eat() == "*") value * parseFactor() else value / parseFactor()
            }
            return value
        }

        // factor := number | '-' factor | '(' expr ')' | func '(' args? ')' | ref/sum call | identifier
        private fun parseFactor(): Double {
            val tok = peek() ?: error("unexpected end of formula")
            if (tok == "-") {
                eat()
                return -parseFactor()
            }
            if (tok == "(") {
                eat()
                val value = parseExpr()
                expect(")")
                return value
            }
            if (tok[0].isDigit() || tok[0] == '.') {
                eat()
                return tok.toDouble()
            }
            if (tok[0].isLetter() || tok[0] == '_') {
                eat()
                if (peek() == "(") return parseCall(tok)
                // Plain identifier: its numeric value from the scope (missing ⇒ 0).
                return asNumber(scope[tok])
            }
            error("unexpected token $tok")
        }

        /** A function call `name(...)`. `ref`/`sum` take a single **name** argument, not an expression. */
        private fun parseCall(name: String): Double {
            expect("(")
            when (name) {
                "ref" -> {
                    // ref(field): value of the field *named by* `field`'s current value.
                    val argName = eat()
                    expect(")")
                    val targetName = scope[argName] as? String ?: return 0.0
                    return asNumber(scope[targetName])
                }
                "sum" -> {
                    // sum(table.column): total `column` across the rows of table field `table`.
                    val ref = eat()
                    expect(")")
                    val dot = ref.indexOf('.')
                    if (dot <= 0 || dot == ref.length - 1) error("sum expects table.column")
                    val tableId = ref.substring(0, dot)
                    val column = ref.substring(dot + 1)
                    val rows = scope[tableId] as? List<*> ?: return 0.0
                    return rows.sumOf { row -> asNumber((row as? Map<*, *>)?.get(column)) }
                }
                else -> {
                    val fn = FUNCS[name] ?: error("unknown function $name")
                    val args = mutableListOf<Double>()
                    if (peek() != ")") {
                        args += parseExpr()
                        while (peek() == ",") {
                            eat()
                            args += parseExpr()
                        }
                    }
                    expect(")")
                    return fn(args)
                }
            }
        }
    }
}
