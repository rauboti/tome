package no.rauboti.tome.campaigns.domain

/**
 * The caller's standing in one campaign (data-model §Authorization). Computed per campaign and never
 * persisted — this DM/player distinction is campaign-scoped and separate from the platform-wide Hive
 * roles Admin/User (FR-024).
 */
enum class CampaignRole {
    /** Runs the campaign — the full view (FR-012). */
    DM,

    /** Owns at least one character on the roster — the limited view (FR-011). */
    PLAYER,

    /** No relationship to this campaign; reads are denied (FR-014). */
    NONE,
}
