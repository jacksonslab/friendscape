package org.rsmod.content.skills.construction

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

class ActiveHouse(
    val ownerUid: PlayerUid,
    val ownerName: String,
    val location: HouseLocation,
    var session: HouseSession,
    var buildMode: Boolean,
) {
    val occupants: MutableSet<Player> = LinkedHashSet()

    val layout: HouseLayout
        get() = session.layout

    val region: Region
        get() = session.region

    fun contains(coords: CoordGrid): Boolean =
        coords.x >= region.southWest.x &&
            coords.z >= region.southWest.z &&
            coords.x < region.northEast.x &&
            coords.z < region.northEast.z
}

/**
 * Every house that is loaded right now. A house is one dynamic region, kept protected from the
 * empty-region sweep while anyone is inside it and released the moment the last occupant leaves.
 * The owner leaving takes their guests out with them.
 */
@Singleton
class HouseManager
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val regions: HouseRegions,
    private val furniture: HouseFurniture,
    private val collision: CollisionFlagMap,
) {
    private val byOwner = HashMap<PlayerUid, ActiveHouse>()
    private val byOccupant = HashMap<PlayerUid, ActiveHouse>()

    fun houseOf(player: Player): ActiveHouse? = byOccupant[player.uid]

    fun ownedBy(player: Player): ActiveHouse? = byOwner[player.uid]

    fun findOpen(ownerName: String, location: HouseLocation): ActiveHouse? {
        val name = ownerName.trim()
        return byOwner.values.firstOrNull {
            it.ownerName.equals(name, ignoreCase = true) && it.location.id == location.id
        }
    }

    /** Loads [owner]'s house into a fresh region, or returns null when no region is free. */
    fun open(owner: Player, layout: HouseLayout, location: HouseLocation, buildMode: Boolean): ActiveHouse? {
        val region = allocate(layout, buildMode) ?: return null
        val name = owner.displayName.ifBlank { owner.username }
        val house = ActiveHouse(owner.uid, name, location, HouseSession(region, layout), buildMode)
        byOwner[owner.uid] = house
        return house
    }

    fun admit(player: Player, house: ActiveHouse, dest: CoordGrid) {
        byOccupant[player.uid]?.let { if (it !== house) release(player) }
        house.occupants += player
        byOccupant[player.uid] = house
        PathingEntityCommon.telejump(player, collision, dest)
    }

    /**
     * Takes [player] out of their house's bookkeeping without moving them. Returns the guests the
     * owner leaving left behind, who have to be sent out by the caller.
     */
    fun release(player: Player): List<Player> {
        val house = byOccupant.remove(player.uid) ?: return emptyList()
        house.occupants -= player
        if (player.uid != house.ownerUid && house.occupants.isNotEmpty()) {
            return emptyList()
        }
        val guests = house.occupants.toList()
        for (guest in guests) {
            byOccupant.remove(guest.uid)
        }
        house.occupants.clear()
        close(house)
        return guests
    }

    /**
     * Rebuilds [house] from its layout into a new region and carries everyone inside across to the
     * same spot in it. Rooms only change by rebuilding, so this is how a new room appears.
     */
    fun rebuild(house: ActiveHouse, buildMode: Boolean = house.buildMode): Boolean {
        val old = house.region
        val region = allocate(house.layout, buildMode) ?: return false
        house.session = HouseSession(region, house.layout)
        house.buildMode = buildMode
        for (occupant in house.occupants) {
            val dest =
                CoordGrid(
                    region.southWest.x + (occupant.coords.x - old.southWest.x),
                    region.southWest.z + (occupant.coords.z - old.southWest.z),
                    occupant.coords.level,
                )
            PathingEntityCommon.telejump(occupant, collision, dest)
        }
        regionRepo.unprotect(old)
        return true
    }

    fun entrance(session: HouseSession): CoordGrid {
        val slot =
            session.layout.rooms.keys.firstOrNull { slotLevel(it) == LEVEL_GROUND }
                ?: session.layout.rooms.keys.first()
        return regions.roomBase(session.region, slot).translate(ENTRANCE_OFFSET, ENTRANCE_OFFSET)
    }

    private fun allocate(layout: HouseLayout, buildMode: Boolean): Region? {
        val region = regions.allocate(layout) ?: return null
        regionRepo.protect(region)
        furniture.furnish(HouseSession(region, layout), buildMode)
        return region
    }

    private fun close(house: ActiveHouse) {
        if (byOwner[house.ownerUid] === house) {
            byOwner.remove(house.ownerUid)
        }
        regionRepo.unprotect(house.region)
    }

    private companion object {
        const val ENTRANCE_OFFSET = 3
    }
}
