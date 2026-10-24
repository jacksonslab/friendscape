package org.rsmod.content.skills.farming

import kotlin.math.floor

/**
 * The chance, out of 256, that picking one crop does not use up a harvest life. It interpolates
 * between the crop's level-1 and level-99 constants; magic secateurs raise both by 10% first.
 */
object HarvestLives {
    private const val SECATEURS_BONUS = 0.1

    fun saveChance(level: Int, low: Int, high: Int, magicSecateurs: Boolean): Int {
        val boost = if (magicSecateurs) 1 + SECATEURS_BONUS else 1.0
        val lo = floor(low * boost).toInt()
        val hi = floor(high * boost).toInt()
        val f = level.coerceIn(1, 99)
        return 1 + floor(lo * (99 - f) / 98.0 + hi * (f - 1) / 98.0 + 0.5).toInt()
    }
}
