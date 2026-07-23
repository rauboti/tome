package no.rauboti.tome.catalogs

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Serves a catalog-backed select's filtered options to the web (T113): `GET
 * /api/rule-sets/{ruleSetId}/catalogs/{catalog}?filter={value}` — e.g. the spells on a caster class's
 * list. Read-only bundled data, behind the `/api` role gate (SecurityConfig). Unknown catalog → 404.
 */
@RestController
@RequestMapping("/api/rule-sets/{ruleSetId}/catalogs")
class CatalogController(
    private val registry: CatalogRegistry,
) {
    @GetMapping("/{catalog}")
    fun options(
        @PathVariable ruleSetId: String,
        @PathVariable catalog: String,
        @RequestParam(required = false) filter: String?,
    ): List<CatalogOption> = registry.get(ruleSetId, catalog).options(filter)
}
