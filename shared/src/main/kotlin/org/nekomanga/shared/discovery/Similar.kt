package org.nekomanga.shared.discovery

import kotlin.math.ln
import kotlinx.serialization.Serializable
import org.nekomanga.shared.model.Title

/**
 * How "More like this" weighs a candidate, each 0–1 (More → Library → More like this). 0 ignores
 * that quality; 1 counts it fully.
 */
@Serializable
data class SimilarWeights(
    /** How many of this title's genres and themes the candidate shares. */
    val genres: Float = 1f,
    /** How many people follow it. */
    val popularity: Float = 0.5f,
    /** Its score. */
    val rating: Float = 0.4f,
    /** Same kind of comic: manga with manga, manhwa with manhwa. */
    val sameType: Float = 0.3f,
    /** Newer titles first. */
    val newer: Float = 0f,
)

object Similar {
    /**
     * Orders [pool] (candidates sharing some of [title]'s genres) by [weights] and keeps [limit].
     * Each quality is scaled to 0–1 within the pool, so the weights compare like with like; ties
     * keep the pool's own order (most popular first).
     */
    fun rank(
        title: Title,
        pool: List<Title>,
        weights: SimilarWeights,
        limit: Int = 20,
    ): List<Title> {
        if (pool.isEmpty()) return emptyList()
        val ids = title.comickGenreIds.toSet()
        val tags = title.tags.map { it.lowercase() }.toSet()
        fun overlap(c: Title): Float {
            val byId =
                if (ids.isEmpty()) 0f else c.comickGenreIds.count { it in ids }.toFloat() / ids.size
            val byName =
                if (tags.isEmpty()) 0f
                else c.tags.count { it.lowercase() in tags }.toFloat() / tags.size
            return maxOf(byId, byName)
        }
        val maxFollows = pool.maxOf { ln(1.0 + (it.follows ?: 0)) }.takeIf { it > 0 } ?: 1.0
        val years = pool.mapNotNull { it.year }
        val oldest = years.minOrNull() ?: 0
        val span = ((years.maxOrNull() ?: 0) - oldest).takeIf { it > 0 } ?: 1
        fun score(c: Title): Float =
            weights.genres * overlap(c) +
                weights.popularity * (ln(1.0 + (c.follows ?: 0)) / maxFollows).toFloat() +
                weights.rating * ((c.rating ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f) +
                weights.sameType * (if (title.type != null && c.type == title.type) 1f else 0f) +
                weights.newer * (c.year?.let { (it - oldest).toFloat() / span } ?: 0f)
        return pool
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Title>> { score(it.value) }.thenBy { it.index },
            )
            .map { it.value }
            .take(limit)
    }
}
