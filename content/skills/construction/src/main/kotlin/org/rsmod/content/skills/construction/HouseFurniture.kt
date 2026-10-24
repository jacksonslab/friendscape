package org.rsmod.content.skills.construction

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo

/** Places what a layout says is built into a house region, and clears it again. */
@Singleton
class HouseFurniture
@Inject
constructor(
    private val catalogue: ConstructionCatalogue,
    private val houses: HouseRegions,
    private val locRepo: LocRepository,
) {
    private val logger = InlineLogger()

    /**
     * Spawns every built piece. Outside building mode the empty hotspots and doorway hotspots are
     * taken away as well, so a furnished house shows no ghost furniture.
     */
    fun furnish(session: HouseSession, buildMode: Boolean) {
        for ((slot, placed) in session.layout.rooms) {
            val def = catalogue.room(placed.room) ?: continue
            for (hotspot in def.hotspots) {
                val row = session.layout.built(slot, hotspot.index)
                if (row != null) {
                    spawn(session, slot, placed.rotation, hotspot, row)
                } else if (!buildMode) {
                    hideHotspot(session, slot, placed.rotation, hotspot)
                }
            }
            if (!buildMode) {
                for (door in def.doors) {
                    removeTemplateLoc(session, slot, placed.rotation, door.part)
                }
            }
        }
    }

    fun spawn(session: HouseSession, slot: Int, rotation: Int, hotspot: HotspotDef, furnitureRow: Int) {
        for (loc in builtLocs(session, slot, rotation, hotspot, furnitureRow)) {
            locRepo.add(loc, duration = HOUSE_LOC_DURATION)
        }
    }

    fun despawn(session: HouseSession, target: HotspotTarget, furnitureRow: Int) {
        for (loc in builtLocs(session, target.slot, target.rotation, target.hotspot, furnitureRow)) {
            locRepo.del(loc, duration = HOUSE_LOC_DURATION)
        }
    }

    private fun hideHotspot(session: HouseSession, slot: Int, rotation: Int, hotspot: HotspotDef) {
        for (part in hotspot.parts) {
            removeTemplateLoc(session, slot, rotation, part)
        }
    }

    private fun removeTemplateLoc(session: HouseSession, slot: Int, rotation: Int, part: HotspotPart) {
        val coords = houses.partCoords(session.region, slot, rotation, part)
        val entity = LocEntity(part.locId, part.shapeId, (part.angleId + rotation) and 3)
        locRepo.del(LocInfo(part.layer, coords, entity), duration = HOUSE_LOC_DURATION)
    }

    /**
     * A multi-tile piece covers every part of its hotspot, each with the loc for that part, so a rug
     * lays its corners and sides rather than one tile at the hotspot's anchor.
     */
    private fun builtLocs(
        session: HouseSession,
        slot: Int,
        rotation: Int,
        hotspot: HotspotDef,
        furnitureRow: Int,
    ): List<LocInfo> {
        val furniture = hotspot.builds.firstOrNull { it.rowId == furnitureRow } ?: return emptyList()
        if (catalogue.builtLocIds(furniture).isEmpty()) {
            logger.warn { "Furniture '${furniture.name}' has no matching loc to place." }
            return emptyList()
        }
        val variant = session.layout.variant(slot, hotspot.index)
        return hotspot.parts.mapNotNull { part ->
            val locId = variant ?: catalogue.builtLocFor(furniture, part) ?: return@mapNotNull null
            val coords = houses.partCoords(session.region, slot, rotation, part)
            LocInfo(part.layer, coords, LocEntity(locId, part.shapeId, (part.angleId + rotation) and 3))
        }
    }

    private companion object {
        const val HOUSE_LOC_DURATION = Int.MAX_VALUE
    }
}
