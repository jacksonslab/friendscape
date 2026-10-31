package org.rsmod.content.skills.prayer

import org.rsmod.api.table.prayer.FriendscapePrayerAltarsRow

object PrayerAltars {
    val ALL by lazy { FriendscapePrayerAltarsRow.all().flatMap { row -> row.altars.map { it.id to row } }.toMap() }

    fun multiplier(altar: Int, burners: Int): Double {
        val row = ALL.getValue(altar)
        return (row.xpMultiplier + burners.coerceIn(0, 2) * row.burnerBonus) / 1000.0
    }
}
