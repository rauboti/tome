package no.rauboti.tome.characters

import no.rauboti.tome.characters.domain.Character
import no.rauboti.tome.rulesets.domain.RuleWarning

/**
 * A [Character] paired with the soft [RuleWarning]s from validating its sheet. [Character.data]
 * is the stored base inputs; the controller enriches it for the response. Warnings are never persisted.
 */
data class CharacterWithWarnings(
    val character: Character,
    val warnings: List<RuleWarning>,
)
