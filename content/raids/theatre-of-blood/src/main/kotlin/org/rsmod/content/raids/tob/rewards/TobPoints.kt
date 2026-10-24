package org.rsmod.content.raids.tob.rewards

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.random.GameRandom

/**
 * The Monumental chest's points model: every raider starts on [BASE] points, loses [DEATH_PENALTY]
 * per death and gains the MVP points of each boss they dealt the most damage to. The team's share
 * of its maximum scales the 1/9.1 unique roll, and a raider's own share scales their loot.
 */
internal object TobPoints {
    const val BASE = 18
    const val DEATH_PENALTY = 4
    const val MVP_TOTAL = 14
    const val UNIQUE_DENOMINATOR = 9.1
    const val PET_DENOMINATOR = 650
    const val CLUE_NUMERATOR = 3
    const val CLUE_DENOMINATOR = 25

    fun personal(deaths: Int, mvpPoints: Int): Int = max(0, BASE - DEATH_PENALTY * deaths) + mvpPoints

    fun teamMaximum(teamSize: Int): Int = BASE * teamSize + MVP_TOTAL

    fun uniqueChance(teamPoints: Int, teamSize: Int): Double {
        if (teamSize <= 0) return 0.0
        val share = min(1.0, teamPoints.toDouble() / teamMaximum(teamSize))
        return share / UNIQUE_DENOMINATOR
    }

    fun performance(personalPoints: Int): Double = min(1.0, personalPoints.toDouble() / BASE)

    fun scaledCount(count: Int, performance: Double): Int =
        max(1, floor(count * performance).toInt())

    fun rollsChance(random: GameRandom, chance: Double): Boolean = random.randomDouble() < chance

    /** Picks the unique's recipient weighted by personal points, or null when nobody has any. */
    fun <T> pickRecipient(random: GameRandom, points: Map<T, Int>): T? {
        val total = points.values.sumOf { max(0, it) }
        if (total <= 0) return null
        var roll = random.of(total)
        for ((raider, value) in points) {
            val weight = max(0, value)
            if (roll < weight) return raider
            roll -= weight
        }
        return null
    }
}
