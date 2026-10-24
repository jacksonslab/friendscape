package org.rsmod.content.skills.sailing

import org.rsmod.map.CoordGrid

data class Dock(
    val name: String,
    val returnTile: CoordGrid,
    val gangplankTile: CoordGrid,
    val boatTile: CoordGrid,
    val sailingLevel: Int,
    val angle: Int = 0,
)

object Docks {
    val ALL: List<Dock> by lazy {
        org.rsmod.api.table.sailing.FriendscapeSailingDocksRow.all().map { row ->
            Dock(row.name, CoordGrid(row.returnX, row.returnZ), CoordGrid(row.plankX, row.plankZ),
                CoordGrid(row.boatX, row.boatZ), row.sailingLevel, row.angle)
        }
    }

    fun byName(name: String): Dock? = ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun nearest(coords: CoordGrid, maxDistance: Int = MAX_GANGPLANK_DISTANCE): Dock? {
        val dock = ALL.minByOrNull { it.gangplankTile.chebyshevDistance(coords) } ?: return null
        val within =
            dock.gangplankTile.level == coords.level &&
                dock.gangplankTile.chebyshevDistance(coords) <= maxDistance
        return if (within) dock else null
    }

    private const val MAX_GANGPLANK_DISTANCE = 8

}
