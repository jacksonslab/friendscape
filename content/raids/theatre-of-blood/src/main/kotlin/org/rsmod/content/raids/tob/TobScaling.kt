package org.rsmod.content.raids.tob

import kotlin.math.max

/** Normal-mode hitpoint scaling: full at five raiders, 87.5% at four, 75% at three or fewer. */
internal object TobScaling {
    const val MAX_PARTY = 5

    fun hpFactor(partySize: Int): Double =
        when {
            partySize >= 5 -> 1.0
            partySize == 4 -> 0.875
            else -> 0.75
        }

    fun scaledHitpoints(base: Int, factor: Double): Int = max(1, (base * factor).toInt())

    fun matomenosCount(raidersInRoom: Int): Int = (raidersInRoom * 2).coerceIn(2, 10)
}
