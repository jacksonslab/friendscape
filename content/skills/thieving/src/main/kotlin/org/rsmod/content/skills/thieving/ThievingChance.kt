package org.rsmod.content.skills.thieving

import org.rsmod.api.utils.skills.SkillingSuccessRate

internal data class PickpocketBoosts(
    val glovesOfSilence: Boolean = false,
    val ardougneDiary: Boolean = false,
    val thievingCape: Boolean = false,
)

internal object ThievingChance {
    private const val GLOVES_PERCENT = 5
    private const val DIARY_PERCENT = 10
    private const val CAPE_PERCENT = 10
    private const val MAX_LEVEL = 99

    fun boosted(rate: Int, boosts: PickpocketBoosts): Int {
        var numerator = rate.toLong()
        var denominator = 1L
        for (percent in boosts.percents()) {
            numerator *= 100 + percent
            denominator *= 100
        }
        return (numerator / denominator).toInt()
    }

    fun successChance(low: Int, high: Int, level: Int, boosts: PickpocketBoosts): Double {
        val rate =
            SkillingSuccessRate.successRate(
                boosted(low, boosts),
                boosted(high, boosts),
                level.coerceIn(1, MAX_LEVEL),
                MAX_LEVEL,
            )
        return rate.coerceIn(0.0, 1.0)
    }

    private fun PickpocketBoosts.percents(): List<Int> =
        listOfNotNull(
            GLOVES_PERCENT.takeIf { glovesOfSilence },
            DIARY_PERCENT.takeIf { ardougneDiary },
            CAPE_PERCENT.takeIf { thievingCape },
        )
}
