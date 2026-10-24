package org.rsmod.content.skills.sailing

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.InteractionModes
import org.rsmod.api.player.output.mes
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistryResult
import org.rsmod.api.registry.worldentity.isSuccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class BoatManager
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val locRepo: LocRepository,
    private val worldEntityRegistry: WorldEntityRegistry,
    private val playerList: PlayerList,
    private val collision: CollisionFlagMap,
) {
    private val logger = InlineLogger()

    private val boats = HashMap<Int, Boat>()

    val all: Collection<Boat>
        get() = boats.values

    fun spawn(type: BoatType, level: Int, fineX: Int, fineZ: Int, angle: Int = 0): Boat? {
        val template =
            RegionTemplate.create {
                copyAllLevels(type.templateZoneX, type.templateZoneZ) {
                    zoneWidth = type.sizeZonesX
                    zoneLength = type.sizeZonesZ
                }
            }
        val region = regionRepo.add(template)
        if (region == null) {
            logger.error { "Could not allocate a deck region for boat type `${type.key}`." }
            return null
        }
        regionRepo.protect(region)
        val swZone = region.southWestZone
        val entity =
            WorldEntity(
                id = type.worldEntityType,
                sizeX = type.sizeZonesX,
                sizeZ = type.sizeZonesZ,
                southWestZoneX = swZone.x,
                southWestZoneZ = swZone.z,
                fineX = fineX,
                fineZ = fineZ,
                activeLevel = type.deckLevel,
                projectedLevel = level,
                angle = angle,
            )
        entity.region = region
        val result = worldEntityRegistry.add(entity)
        if (!result.isSuccess()) {
            regionRepo.unprotect(region)
            logger.error { "Could not register boat world entity `${type.key}`: $result" }
            return null
        }
        val boat = Boat(type, entity, region)
        furnish(boat)
        boats[entity.slotId] = boat
        return boat
    }

    private fun furnish(boat: Boat) {
        val southWest = boat.region.southWest
        for (deckLoc in boat.type.deckLocs) {
            locRepo.add(
                CoordGrid(southWest.x + deckLoc.dx, southWest.z + deckLoc.dz, deckLoc.level),
                deckLoc.loc,
                Int.MAX_VALUE,
                LocAngle.West,
                deckLoc.shape,
            )
        }
    }

    fun spawnAtDock(type: BoatType, dock: Dock): Boat? {
        val boat =
            spawn(
                type = type,
                level = dock.boatTile.level,
                fineX = WorldEntity.tileToFine(dock.boatTile.x) + type.dockFineDx,
                fineZ = WorldEntity.tileToFine(dock.boatTile.z) + type.dockFineDz,
                angle = dock.angle,
            )
        boat?.dock = dock
        return boat
    }

    fun mooredAt(dock: Dock): Boat? = boats.values.firstOrNull { it.dock == dock }

    fun retrieve(player: Player, type: BoatType, dock: Dock): Boat? {
        val existing = boats.values.firstOrNull { it.isOwnedBy(player) }
        if (existing != null && existing.dock == dock) return existing
        if (existing != null) despawn(existing)
        return spawnAtDock(type, dock)?.apply {
            owner = player.uuid
            ownerWorld = player.worldType
        }
    }

    fun removeOwnedBy(player: Player) {
        boats.values.filter { it.isOwnedBy(player) }.toList().forEach(::despawn)
    }

    fun moorNearby(boat: Boat): Dock? {
        if (boat.speed != 0 || boat.targetSpeed != 0) return null
        boat.dock?.let { return it }
        val coords = boat.entity.coords
        val dock = Docks.ALL.filter { it.boatTile.level == coords.level }
            .minByOrNull { it.boatTile.chebyshevDistance(coords) } ?: return null
        if (dock.boatTile.chebyshevDistance(coords) > 3) return null
        boat.entity.teleport(dock.boatTile.level,
            WorldEntity.tileToFine(dock.boatTile.x) + boat.type.dockFineDx,
            WorldEntity.tileToFine(dock.boatTile.z) + boat.type.dockFineDz)
        boat.entity.updateAngle(dock.angle)
        boat.targetAngle = dock.angle
        boat.dock = dock
        return dock
    }

    fun returnToShore(player: Player) {
        val destination = if (player.sailingShore != 0) CoordGrid(player.sailingShore) else player.lastKnownNormalCoord
        releaseHelm(player)
        PathingEntityCommon.telejump(player, collision, destination)
        player.lastKnownNormalCoord = destination
        player.aboardPlayerBoat = 0
        player.helmLockedIn = 0
        player.sailingShore = 0
    }

    fun despawn(boat: Boat): WorldEntityRegistryResult.Delete {
        evacuate(boat)
        val slot = boat.entity.slotId
        val result = worldEntityRegistry.del(boat.entity)
        boat.entity.region = null
        regionRepo.unprotect(boat.region)
        if (slot != WorldEntity.INVALID_SLOT) {
            boats.remove(slot)
        }
        return result
    }

    fun boatAt(coords: CoordGrid): Boat? = boats.values.firstOrNull { it.entity.contains(coords) }

    fun boatOf(player: Player): Boat? = boatAt(player.coords)

    fun releaseHelm(player: Player) {
        val boat = boatOf(player) ?: return
        if (boat.helmsman != player) {
            return
        }
        InteractionModes.resetInteractionMode(player, boat.entity.slotId)
        InteractionModes.setInteractionMode(
            player,
            InteractionModes.WORLD_DEFAULT,
            InteractionModes.TILE_MODE_WALK,
            InteractionModes.ENTITY_MODE_ALL,
        )
        player.helmLockedIn = 0
        boat.helmsman = null
        boat.moveMode = SailingMoveModes.STOPPED
        boat.targetSpeed = 0
    }

    private fun evacuate(boat: Boat) {
        for (player in playerList) {
            if (!boat.entity.contains(player.coords)) {
                continue
            }
            returnToShore(player)
            player.mes("You are returned to shore.")
        }
    }
}
