package no.rauboti.tome.catalogs

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * The dnd35 `spells` catalog (T112/T113): the OGL 3.5 SRD spell list (`rulesets/dnd35/spells.json`,
 * sourced from d20srd.org via `tools/build-spells.mjs`), filtered by **caster class**. An option's
 * [CatalogOption.value] is the spell id, [label] its name, and `meta.level` the spell's level for the
 * filtered class. Results are ordered by level then name. A blank class filter yields no options.
 */
@Component
class SpellCatalog(
    objectMapper: ObjectMapper,
) : Catalog {
    private data class SpellEntry(
        val id: String,
        val name: String,
        val classLevels: Map<String, Int>,
    )

    private data class SpellFile(
        val spells: List<SpellEntry>,
    )

    private val spells: List<SpellEntry> =
        ClassPathResource(SPELLS_PATH).inputStream.use { objectMapper.readValue(it, SpellFile::class.java).spells }

    override fun ruleSetId(): String = "dnd35"

    override fun name(): String = "spells"

    override fun options(filter: String?): List<CatalogOption> {
        val cls = filter?.trim().orEmpty()
        if (cls.isEmpty()) return emptyList()
        return spells
            .filter { it.classLevels.containsKey(cls) }
            .sortedWith(compareBy({ it.classLevels[cls] }, { it.name }))
            .map { CatalogOption(value = it.id, label = it.name, meta = mapOf("level" to it.classLevels[cls])) }
    }

    private companion object {
        const val SPELLS_PATH = "rulesets/dnd35/spells.json"
    }
}
