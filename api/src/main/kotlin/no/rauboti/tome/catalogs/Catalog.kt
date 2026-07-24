package no.rauboti.tome.catalogs

/**
 * One option returned by a catalog-backed select (openapi catalog endpoint item, T113). [label] is a
 * literal display string (catalog content is data, e.g. a spell name). [meta] carries optional
 * per-option data (e.g. a spell's level for the filtered class).
 */
data class CatalogOption(
    val value: String,
    val label: String,
    val meta: Map<String, Any?>? = null,
)

/**
 * A named, filterable option source behind a catalog-backed select (T113; a typed sheet component
 * fetches it — ADR-001). The **mechanism**
 * is rule-set-agnostic (resolved generically by [CatalogRegistry] + served by [CatalogController]); a
 * concrete catalog owns its data and filter semantics (e.g. [SpellCatalog] = the dnd35 spell list
 * filtered by caster class). Registering a new catalog is additive: declare its `@Component`.
 */
interface Catalog {
    /** The rule set this catalog belongs to, e.g. `dnd35`. */
    fun ruleSetId(): String

    /** The catalog name referenced by `optionsFrom.catalog`, e.g. `spells`. */
    fun name(): String

    /** The options for the given [filter] value (e.g. a caster class); a blank/absent filter yields none. */
    fun options(filter: String?): List<CatalogOption>
}
