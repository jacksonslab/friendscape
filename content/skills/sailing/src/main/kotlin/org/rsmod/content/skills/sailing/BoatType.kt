package org.rsmod.content.skills.sailing

import org.rsmod.game.loc.LocShape

data class BoatType(
    val key: String,
    val worldEntityType: Int,
    val templateZoneX: Int,
    val templateZoneZ: Int,
    val sizeZonesX: Int,
    val sizeZonesZ: Int,
    val deckLevel: Int,
    val boardDestDx: Int,
    val boardDestDz: Int,
    val dockFineDx: Int,
    val dockFineDz: Int,
    val baseSpeed: Int,
    val speedCap: Int,
    val acceleration: Int,
    val helmPlayerGrab: String,
    val helmSynthGrab: Int,
    val helmSynthRelease: Int,
    val deckLocs: List<DeckLoc>,
)

object BoatTypes {
    val ALL: List<BoatType> by lazy {
        org.rsmod.api.table.sailing.FriendscapeSailingTemplatesRow.all().map { row ->
            BoatType(
                key = row.key,
                worldEntityType = row.worldEntityType,
                templateZoneX = row.templateZoneX,
                templateZoneZ = row.templateZoneZ,
                sizeZonesX = row.sizeZonesX,
                sizeZonesZ = row.sizeZonesZ,
                deckLevel = row.deckLevel,
                boardDestDx = row.boardDestDx,
                boardDestDz = row.boardDestDz,
                dockFineDx = row.dockFineDx,
                dockFineDz = row.dockFineDz,
                baseSpeed = row.baseSpeed,
                speedCap = row.speedCap,
                acceleration = row.acceleration,
                helmPlayerGrab = row.helmPlayerGrab,
                helmSynthGrab = row.helmSynthGrab,
                helmSynthRelease = row.helmSynthRelease,
                deckLocs = org.rsmod.api.table.sailing.FriendscapeSailingFixturesRow.all()
                    .filter { it.boatKey == row.key }
                    .map { DeckLoc(it.locSymbol, it.dx, it.dz, it.level, LocShape[it.shape]) },
            )
        }
    }
    val RAFT: BoatType get() = checkNotNull(byKey("raft"))
    fun byKey(key: String): BoatType? = ALL.firstOrNull { it.key.equals(key, ignoreCase = true) }
}
