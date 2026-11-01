package org.rsmod.content.skills.sailing

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
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
    private val sea: SeaMap,
) {
    private val logger = InlineLogger()

    private val boats = HashMap<Int, Boat>()

    val all: Collection<Boat>
        get() = boats.values

    fun spawn(type: BoatType, level: Int, fineX: Int, fineZ: Int, angle: Int = 0, owner: Player? = null): Boat? {
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
        boat.targetAngle = angle
        if (owner != null) {
            boat.owner = owner.uuid
            boat.ownerWorld = owner.worldType
            boat.ownerName = owner.displayName
        }
        furnish(boat, owner)
        boats[entity.slotId] = boat
        return boat
    }

    private fun furnish(boat: Boat, owner: Player?) {
        val hookInstead = boat.type.hookReplacesCargoHold && owner?.sailingRaftFacility == RAFT_FACILITY_HOOK
        for (deckLoc in boat.type.deckLocs) {
            if (hookInstead && deckLoc == boat.type.cargoHold) {
                continue
            }
            addDeckLoc(boat, deckLoc.loc, deckLoc.dx, deckLoc.dz, deckLoc)
        }
        if (!boat.type.hookReplacesCargoHold || hookInstead) {
            val hook = boat.type
            locRepo.add(
                boat.deckCoord(hook.hookDx, hook.hookDz),
                RSCM.getReverseMapping(RSCMType.LOC, hook.hook),
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        }
    }

    private fun addDeckLoc(boat: Boat, loc: String, dx: Int, dz: Int, template: DeckLoc) {
        locRepo.add(boat.deckCoord(dx, dz, template.level), loc, Int.MAX_VALUE, LocAngle.West, template.shape)
    }

    /** Swaps the raft's single facility slot between its cargo hold and its salvaging hook. */
    fun swapRaftFacility(boat: Boat, owner: Player): Boolean {
        val type = boat.type
        val hold = type.cargoHold ?: return false
        if (!type.hookReplacesCargoHold) {
            return false
        }
        val coords = boat.deckCoord(hold.dx, hold.dz, hold.level)
        val hookName = RSCM.getReverseMapping(RSCMType.LOC, type.hook)
        if (owner.sailingRaftFacility == RAFT_FACILITY_HOOK) {
            owner.sailingRaftFacility = 0
            locRepo.add(coords, hold.loc, Int.MAX_VALUE, LocAngle.West, hold.shape)
        } else {
            owner.sailingRaftFacility = RAFT_FACILITY_HOOK
            locRepo.add(coords, hookName, Int.MAX_VALUE, LocAngle.West, hold.shape)
        }
        boat.hookDeployed = false
        return true
    }

    fun navigableAt(level: Int): (Int, Int) -> Boolean = { x, z ->
        level == SEA_LEVEL && sea.isWater(x, z) && Navigation.isClear(collision[x, z, level])
    }

    internal fun findMooring(type: BoatType, plank: CoordGrid): Mooring? =
        Navigation.findMooring(navigableAt(plank.level), plank.x, plank.z, type.hullWidth, type.hullLength)

    fun ownedBy(player: Player): Boat? = boats.values.firstOrNull { it.isOwnedBy(player) }

    /**
     * Brings [player]'s boat alongside [plank]. A boat already moored at [port] is reused; any other
     * boat they own is removed first.
     */
    fun moorFor(player: Player, type: BoatType, port: Port, plank: CoordGrid): Boat? {
        val existing = ownedBy(player)
        if (existing != null && existing.port == port && existing.type == type) {
            return existing
        }
        val mooring = findMooring(type, plank) ?: return null
        if (existing != null) {
            despawn(existing)
        }
        val boat =
            spawn(
                type = type,
                level = plank.level,
                fineX = mooring.fineX + type.dockFineDx,
                fineZ = mooring.fineZ + type.dockFineDz,
                angle = mooring.angle,
                owner = player,
            ) ?: return null
        boat.port = port
        return boat
    }

    /** Snaps a stopped [boat] alongside [plank] if it is within [MOORING_RANGE] tiles of it. */
    fun moorAt(boat: Boat, port: Port, plank: CoordGrid): Boolean {
        if (!boat.isStopped) {
            return false
        }
        if (boat.port == port) {
            return true
        }
        val coords = boat.entity.coords
        if (coords.level != plank.level || coords.chebyshevDistance(plank) > MOORING_RANGE) {
            return false
        }
        val mooring = findMooring(boat.type, plank) ?: return false
        boat.entity.teleport(plank.level, mooring.fineX + boat.type.dockFineDx, mooring.fineZ + boat.type.dockFineDz)
        boat.entity.updateAngle(mooring.angle)
        boat.targetAngle = mooring.angle
        boat.port = port
        return true
    }

    fun returnToShore(player: Player) {
        val destination =
            if (player.sailingShore != 0) CoordGrid(player.sailingShore) else player.lastKnownNormalCoord
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

    fun removeOwnedBy(player: Player) {
        boats.values.filter { it.isOwnedBy(player) }.toList().forEach(::despawn)
    }

    fun boatAt(coords: CoordGrid): Boat? = boats.values.firstOrNull { it.entity.contains(coords) }

    fun boatOf(player: Player): Boat? = boatAt(player.coords)

    fun passengers(boat: Boat): List<Player> = playerList.filter { boat.entity.contains(it.coords) }

    fun releaseHelm(player: Player) {
        val boat = boatOf(player) ?: return
        releaseHelm(boat, player)
    }

    /**
     * Teleporting off a boat (spell, tablet, command) skips the gangplank, so nothing undoes what
     * boarding and the helm set: the main world stays in heading mode and every click steers instead
     * of walking. Runs each cycle to put such players back to normal.
     */
    fun releaseStranded() {
        for (boat in boats.values.toList()) {
            val helmsman = boat.helmsman ?: continue
            if (!boat.entity.contains(helmsman.coords)) {
                releaseHelm(boat, helmsman)
            }
        }
        for (player in playerList) {
            if (player.aboardPlayerBoat != 0 && boatOf(player) == null) {
                player.aboardPlayerBoat = 0
                player.helmLockedIn = 0
                player.sailingShore = 0
            }
        }
    }

    private fun releaseHelm(boat: Boat, player: Player) {
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
        for (player in passengers(boat)) {
            returnToShore(player)
            player.mes("You are returned to shore.")
        }
    }

    companion object {
        const val MOORING_RANGE = 10
        private const val SEA_LEVEL = 0
    }
}
