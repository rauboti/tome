package no.rauboti.tome.rulesets

/**
 * Rule-set-agnostic compute-on-read (D8, T105). Given a [SheetDefinition] and a sheet's base inputs,
 * it fills in **every** derived value from the definition's `derivedFrom` formulas via
 * [FormulaEvaluator] — top-level fields and per-row [FieldType.TABLE] cells alike — and returns a new
 * resolved sheet. Pure: never mutates [data].
 *
 * This is the single shared home of the compute step (FR-023/SC-009): a rule set supplies only its
 * definition + `validate` logic; the actual derivation is authored as formulas in the definition and
 * evaluated here, identically to the web `derive.ts`. Because all v1 derived values are
 * formula-expressible (spec §Clarifications 2026-07-23), no rule set needs hand-written compute code.
 *
 * Derived results are coerced to `Int` when integral (every 3.5 derived value is), matching the sheet's
 * natural integer values and the existing contract; a genuinely fractional result stays a `Double`.
 */
object SheetCompute {
    fun resolve(
        definition: SheetDefinition,
        data: SheetData,
    ): SheetData {
        val fields = definition.sections.flatMap { it.fields }
        val topDerived = fields.filter { it.type == FieldType.DERIVED && it.derivedFrom != null }
        val tables = fields.filter { it.type == FieldType.TABLE }
        val derivedColumns: Map<String, List<SheetField>> =
            tables.associate { table ->
                table.id to table.columns.orEmpty().filter { it.type == FieldType.DERIVED && it.derivedFrom != null }
            }

        // Working sheet-level scope (a shallow copy — the input map is never mutated).
        val scope: MutableMap<String, Any?> = data.toMutableMap()
        // Rebuild each table's rows as fresh mutable maps (so we never mutate the caller's row maps) and
        // expose them in scope so `sum(table.column)` can read them.
        val tableRows: Map<String, MutableList<MutableMap<String, Any?>>> =
            tables.associate { table ->
                val rows = (data[table.id] as? List<*>).orEmpty()
                table.id to
                    rows
                        .mapNotNull { row ->
                            (row as? Map<*, *>)?.entries?.associateTo(mutableMapOf()) { it.key.toString() to it.value }
                        }.toMutableList()
            }
        tableRows.forEach { (id, rows) -> scope[id] = rows }

        // Derived can depend on other derived (top-level or per-row), so iterate to a fixpoint — bounded
        // by the total number of derived cells, which also caps any accidental cyclic formula.
        val cellCount = topDerived.size + tables.sumOf { (tableRows[it.id]?.size ?: 0) * (derivedColumns[it.id]?.size ?: 0) }
        var pass = 0
        while (pass <= cellCount) {
            var changed = false
            for (field in topDerived) {
                val value = FormulaEvaluator.evaluate(field.derivedFrom!!, scope) ?: continue
                val coerced = coerce(value)
                if (scope[field.id] != coerced) {
                    scope[field.id] = coerced
                    changed = true
                }
            }
            for (table in tables) {
                val cols = derivedColumns[table.id].orEmpty()
                if (cols.isEmpty()) continue
                for (row in tableRows[table.id].orEmpty()) {
                    // Row scope: sheet-level values overlaid with this row's own cells (row shadows sheet).
                    val rowScope = HashMap(scope).apply { putAll(row) }
                    for (col in cols) {
                        val value = FormulaEvaluator.evaluate(col.derivedFrom!!, rowScope) ?: continue
                        val coerced = coerce(value)
                        if (row[col.id] != coerced) {
                            row[col.id] = coerced
                            changed = true
                        }
                    }
                }
            }
            if (!changed) break
            pass++
        }
        return scope
    }

    /** Integral results become `Int` (matching the sheet's natural integer values); others stay `Double`. */
    private fun coerce(value: Double): Any = if (value % 1.0 == 0.0) value.toInt() else value
}
