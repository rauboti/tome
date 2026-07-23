package no.rauboti.tome.characters

import no.rauboti.tome.rulesets.RuleSet
import no.rauboti.tome.rulesets.SheetData
import org.springframework.stereotype.Component

/**
 * The single home of **character** compute-on-read (D8): given a stored sheet holding **base inputs
 * only** and the character's [RuleSet], produce the fully resolved sheet — base inputs overlaid with
 * freshly computed derived values — for every consumer (REST responses, the player view, combat, SSE).
 *
 * Derived values are never persisted; they are recomputed here on every read, so they can never drift
 * from the stored inputs. The recomputed values always win the merge, so any stale derived value that
 * somehow leaked into storage is overwritten on read.
 *
 * Entity-scoped by design (data-model.md §Derived values): NPCs and other sheet-bearing entities get
 * analogous resolvers; a shared core is extracted only if duplication warrants.
 */
@Component
class CharacterDataResolver {
    /** Base inputs overlaid with `ruleSet.computeDerived(...)` — the resolved sheet returned on read. */
    fun resolve(
        data: SheetData,
        ruleSet: RuleSet,
    ): SheetData = data + ruleSet.computeDerived(data)
}
