package no.rauboti.tome.characters

import no.rauboti.tome.common.JsonbSupport
import no.rauboti.tome.rulesets.SheetData
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

/**
 * `characters`-table access via [NamedParameterJdbcTemplate] (no JPA — research D3/D5). The sheet
 * [SheetData] round-trips through [JsonbSupport] (T014): serialized to a JSON string and bound into
 * the `jsonb` column with an explicit `cast(:data as jsonb)`, read back out via `getString` +
 * `fromJson`. Persistence only — the rule-set logic and 409 mapping live in the service (T030).
 *
 * Writes use `RETURNING *` so the DB-generated id/version/timestamps come back in the same round
 * trip. [update] carries the optimistic-concurrency guard in its `WHERE`: it bumps `version` only
 * when the caller's [expectedVersion] still matches, so a stale write matches no row and returns
 * `null` (the service turns that into a 409, SC-006) rather than silently overwriting.
 */
@Repository
class CharacterRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val jsonb: JsonbSupport,
) {
    private val rowMapper =
        RowMapper { rs, _ ->
            Character(
                id = rs.getObject("id", UUID::class.java),
                userId = rs.getObject("user_id", UUID::class.java),
                ruleSetId = rs.getString("rule_set_id"),
                name = rs.getString("name"),
                data = jsonb.fromJson(rs.getString("data")),
                version = rs.getInt("version"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
            )
        }

    /** Insert a new character (id/version/timestamps default in the DB) and return the stored row. */
    fun insert(
        userId: UUID,
        ruleSetId: String,
        name: String,
        data: SheetData,
    ): Character {
        val params =
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("ruleSetId", ruleSetId)
                .addValue("name", name)
                .addValue("data", jsonb.toJson(data))
        val sql =
            """
            INSERT INTO characters (user_id, rule_set_id, name, data)
            VALUES (:userId, :ruleSetId, :name, cast(:data as jsonb))
            RETURNING *
            """.trimIndent()
        // Insert always yields exactly one row, so queryForObject is safe (never empty).
        return jdbc.queryForObject(sql, params, rowMapper)!!
    }

    /** The character with [id], or null if none exists. */
    fun findById(id: UUID): Character? =
        jdbc
            .query("SELECT * FROM characters WHERE id = :id", MapSqlParameterSource("id", id), rowMapper)
            .firstOrNull()

    /** Every character owned by [userId], newest first (backs `GET /api/characters`). */
    fun findByUserId(userId: UUID): List<Character> =
        jdbc.query(
            "SELECT * FROM characters WHERE user_id = :userId ORDER BY created_at DESC",
            MapSqlParameterSource("userId", userId),
            rowMapper,
        )

    /**
     * Optimistic update: rewrite [name]/[data] and bump `version`, but only while the stored version
     * still equals [expectedVersion]. Returns the updated row, or `null` when nothing matched — either
     * the character is gone or a concurrent edit already moved the version on (the service, which has
     * already confirmed existence/ownership, reads `null` as a stale write → 409).
     */
    fun update(
        id: UUID,
        name: String,
        data: SheetData,
        expectedVersion: Int,
    ): Character? {
        val params =
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("data", jsonb.toJson(data))
                .addValue("expectedVersion", expectedVersion)
        val sql =
            """
            UPDATE characters
            SET name = :name, data = cast(:data as jsonb), version = version + 1, updated_at = now()
            WHERE id = :id AND version = :expectedVersion
            RETURNING *
            """.trimIndent()
        return jdbc.query(sql, params, rowMapper).firstOrNull()
    }

    /** Delete the character with [id]; returns true if a row was removed. */
    fun deleteById(id: UUID): Boolean =
        jdbc.update("DELETE FROM characters WHERE id = :id", MapSqlParameterSource("id", id)) > 0
}
