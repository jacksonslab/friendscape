package org.rsmod.content.skills.sailing

import org.rsmod.api.table.sailing.FriendscapeSailingFixturesRow
import org.rsmod.api.table.sailing.FriendscapeSailingTemplatesRow
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.game.loc.LocShape

class BoatType(private val row: FriendscapeSailingTemplatesRow) {
    val key: String = row.key
    val worldEntityType: Int = row.worldEntityType
    val templateZoneX: Int = row.templateZoneX
    val templateZoneZ: Int = row.templateZoneZ
    val sizeZonesX: Int = row.sizeZonesX
    val sizeZonesZ: Int = row.sizeZonesZ
    val deckLevel: Int = row.deckLevel
    val boardDestDx: Int = row.boardDestDx
    val boardDestDz: Int = row.boardDestDz
    val dockFineDx: Int = row.dockFineDx
    val dockFineDz: Int = row.dockFineDz
    val baseSpeed: Int = row.baseSpeed
    val speedCap: Int = row.speedCap
    val acceleration: Int = row.acceleration
    val purchaseCost: Int = row.purchaseCost
    val helmPlayerGrab: String = row.helmPlayerGrab
    val helmSynthGrab: Int = row.helmSynthGrab
    val helmSynthRelease: Int = row.helmSynthRelease
    val hullWidth: Int = row.hullWidth
    val hullLength: Int = row.hullLength
    val hook: Int = row.hook.id
    val hookDx: Int = row.hookDx
    val hookDz: Int = row.hookDz
    val hookReplacesCargoHold: Boolean = row.hookReplacesCargoHold

    val boat: SailingBoatRow
        get() = row.boat

    val typeId: Int
        get() = boat.typeId

    val name: String
        get() = boat.name

    val levelRequired: Int
        get() = boat.levelRequired

    val deckLocs: List<DeckLoc> by lazy {
        FriendscapeSailingFixturesRow.all()
            .filter { it.boatKey == key }
            .map { DeckLoc(it.locSymbol, it.dx, it.dz, it.level, LocShape[it.shape]) }
    }

    val cargoHold: DeckLoc?
        get() = deckLocs.firstOrNull { it.loc.startsWith(CARGO_HOLD_PREFIX) }

    override fun toString(): String = "BoatType($key)"

    private companion object {
        private const val CARGO_HOLD_PREFIX = "loc.sailing_boat_cargo_hold_"
    }
}

object BoatTypes {
    val ALL: List<BoatType> by lazy { FriendscapeSailingTemplatesRow.all().map(::BoatType) }

    val RAFT: BoatType
        get() = checkNotNull(byKey("raft"))

    fun byKey(key: String): BoatType? = ALL.firstOrNull { it.key.equals(key, ignoreCase = true) }

    fun byTypeId(typeId: Int): BoatType? = ALL.firstOrNull { it.typeId == typeId }
}
