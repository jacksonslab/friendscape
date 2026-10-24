package org.rsmod.content.skills.construction

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.instances.InstanceAttributes
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetPosition
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.FurnitureRow
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class ConstructionScript
@Inject
constructor(
    private val catalogue: ConstructionCatalogue,
    private val houses: HouseRegions,
    private val manager: HouseManager,
    private val houseFurniture: HouseFurniture,
    private val xpMods: XpModifiers,
    private val protectedAccess: ProtectedAccessLauncher,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val logger = InlineLogger()

    private var Player.buildMode by boolVarBit(VARBIT_BUILD_MODE)
    private var Player.houseLocation by intVarBit(VARBIT_HOUSE_LOCATION)

    override fun ScriptContext.startup() {
        for (town in HouseLocation.ALL) {
            val type = ServerCacheManager.getObject(town.portalLoc) ?: continue
            onOpLoc1(type) { portalMenu(town) }
            onOpLoc2(type) { enterOwnHouse(town, buildMode = false) }
            onOpLoc3(type) { enterOwnHouse(town, buildMode = true) }
            onOpLoc4(type) { enterFriendsHouse(town) }
        }
        onOpLoc1(EXIT_PORTAL) { leaveHouse() }

        // Every house hotspot, doorway and built piece carries its option at op5: a hotspot is
        // `ops=[4=Build]` and a built piece `ops=[.., 3=Upgrade, 4=Remove]`.
        for (locId in catalogue.hotspotLocIds()) {
            val type = ServerCacheManager.getObject(locId) ?: continue
            onOpLoc5(type) { openBuildMenu(it.loc.coords, it.loc.id) }
        }
        for (locId in catalogue.furnitureLocIds()) {
            val type = ServerCacheManager.getObject(locId) ?: continue
            onOpLoc5(type) { removeFurniture(it.loc.coords, it.loc.id) }
            if (type.actions.getOpOrNull(OP_UPGRADE).equals(UPGRADE, ignoreCase = true)) {
                onOpLoc4(type) { openBuildMenu(it.loc.coords, it.loc.id, upgrade = true) }
            }
        }
        for ((locId, destination) in PohPortals.teleports) {
            val type = ServerCacheManager.getObject(locId) ?: continue
            onOpLoc1(type) { enterPortal(destination) }
        }
        for (locId in catalogue.doorLocIds()) {
            val type = ServerCacheManager.getObject(locId) ?: continue
            onOpLoc5(type) { openRoomMenu(it.loc.coords) }
        }

        onPlayerLogout { leaveOnLogout(player) }
        onPlayerCoordsChanged {
            val house = manager.houseOf(player) ?: return@onPlayerCoordsChanged
            if (!house.contains(player.coords)) {
                exitHouse(player, house, teleport = false)
            }
        }

        onCommand("house") {
            desc = "Enter your player-owned house"
            cheat { protectedAccess.launch(player) { enterOwnHouse(null, buildMode = false) } }
        }
        onCommand("buildmode") {
            desc = "Enter your player-owned house in building mode"
            cheat { protectedAccess.launch(player) { enterOwnHouse(null, buildMode = true) } }
        }
        onPlayerQueueWithArgs<BuildTask>(QUEUE_BUILD) { finishBuild(it.args) }
        onPlayerQueueWithArgs<RemoveTask>(QUEUE_REMOVE) { finishRemove(it.args) }

        onCommand("pohwhere") {
            desc = "Report how the tile under you resolves to a room slot and hotspot"
            cheat { dumpWhere() }
        }
        onCommand("pohrooms") {
            desc = "Dump what the cache says about house rooms and their hotspots"
            cheat { dumpRooms(args.getOrNull(0)) }
        }
    }

    /**
     * Every refusal in [openBuildMenu] is a silent return, so a hotspot that will not open gives
     * nothing to go on. This walks the same chain out loud.
     */
    private fun Cheat.dumpWhere() {
        val coords = player.coords
        val house = manager.houseOf(player)
        if (house == null) {
            player.mes("No house session: you are not inside a house.")
            return
        }
        val session = house.session
        player.mes("at $coords  region sw=${session.region.southWest} buildMode=${house.buildMode}")
        val slot = session.slotAt(coords)
        if (slot == null) {
            player.mes("slotAt: no grid slot for this tile.")
            return
        }
        val placed = session.layout.placed(slot)
        player.mes("slot=$slot placed=${placed?.room} rotation=${placed?.rotation}")
        val room = placed?.room?.let(catalogue::room)
        if (room == null) {
            player.mes("No room placed in this slot.")
            return
        }
        player.mes("room='${room.row.name}' hotspots=${room.hotspots.size}")
        val target = session.resolve(catalogue, houses, coords)
        if (target == null) {
            player.mes("resolve: this tile is not on any hotspot of that room.")
        } else {
            val spot = target.hotspot
            val visible = spot.builds.filter { it.hiddenInBuildMenu != 1 }
            player.mes(
                "resolve -> slot=${target.slot} hotspot=${spot.index + 1} " +
                    "builds=${spot.builds.size} visibleInMenu=${visible.size} " +
                    "alreadyBuilt=${session.layout.built(target.slot, spot.index)} " +
                    "saw=${player.inv.contains(OBJ_SAW)} hammer=${player.inv.contains(OBJ_HAMMER)}"
            )
        }
        for (spot in room.hotspots) {
            val where =
                spot.parts.joinToString {
                    houses.localCoords(session.region, slot, placed.rotation, it.localX, it.localZ)
                        .toString()
                }
            player.mes("  [${spot.index + 1}] $where")
        }
        val door = session.resolveDoor(catalogue, houses, coords)
        if (door == null) {
            player.mes("door: this tile is not a doorway of that room.")
        } else {
            val target = neighbour(door.slot, door.direction)
            player.mes(
                "door -> dir=${door.direction} neighbour=$target " +
                    "occupiedBy=${target?.let { session.layout.placed(it)?.room }}"
            )
        }
        for (entry in room.doors) {
            val at =
                houses.localCoords(session.region, slot, placed.rotation, entry.localX, entry.localZ)
            player.mes("  door dir=${entry.direction} ${entry.localX},${entry.localZ} -> $at")
        }
    }

    private fun Cheat.dumpRooms(filter: String?) {
        val rooms =
            catalogue.all().filter { filter == null || it.row.name.contains(filter, true) }
        player.mes("${rooms.size} room(s) loaded from dbtable.poh_room:")
        for (room in rooms.sortedBy { it.row.name }) {
            player.mes(
                "${room.row.name} lvl=${room.levelRequirement} cost=${room.cost} " +
                    "zone=${room.sourceZone.x},${room.sourceZone.z} " +
                    "doors=${room.doorsAfter(0)} hotspots=${room.hotspots.size}/${room.row.hotspot.size}"
            )
            for (hotspot in room.hotspots) {
                val part = hotspot.primary
                val loc = RSCM.getReverseMapping(RSCMType.LOC, part.locId)
                player.mes(
                    "  [${hotspot.index + 1}] ${part.localX},${part.localZ} $loc " +
                        "x${hotspot.parts.size} builds=${hotspot.builds.joinToString { it.name }}"
                )
            }
        }
    }

    private suspend fun ProtectedAccess.portalMenu(town: HouseLocation) {
        val choice =
            choice4(
                "Go to your house.",
                0,
                "Go to your house (building mode).",
                1,
                "Go to a friend's house.",
                2,
                "Never mind.",
                3,
            )
        when (choice) {
            0 -> enterOwnHouse(town, buildMode = false)
            1 -> enterOwnHouse(town, buildMode = true)
            2 -> enterFriendsHouse(town)
        }
    }

    /** [town] is the portal used, or null for the commands, which reach the house from anywhere. */
    private fun ProtectedAccess.enterOwnHouse(town: HouseLocation?, buildMode: Boolean) {
        if (player.houseLocation == 0) {
            mes("You don't own a house. An estate agent can sell you one.")
            return
        }
        val home = HouseLocation.byId(player.houseLocation) ?: return
        if (town != null && town.id != home.id) {
            mes("Your house is in ${home.name}.")
            return
        }

        val current = manager.ownedBy(player)
        if (current != null && manager.houseOf(player) === current) {
            switchBuildMode(current, buildMode)
            return
        }
        current?.let { exitHouse(player, it, teleport = true) }

        val layout = player.layout()
        if (layout.rooms.isEmpty()) {
            starterLayout(layout)
            player.storeLayout(layout)
        }
        val house = manager.open(player, layout, home, buildMode)
        if (house == null) {
            mes("There is no space for your house right now. Try again shortly.")
            return
        }
        player.buildMode = buildMode
        player.attr[InstanceAttributes.LOGIN_EXIT_COORD] = home.exit.packed
        manager.admit(player, house, manager.entrance(house.session))
    }

    /** Building mode is the whole house's state, so turning it on sends any guests home first. */
    private fun ProtectedAccess.switchBuildMode(house: ActiveHouse, buildMode: Boolean) {
        if (house.buildMode == buildMode) {
            return
        }
        if (buildMode) {
            for (guest in house.occupants.filter { it.uid != player.uid }) {
                guest.mes("The owner has switched to building mode.")
                exitHouse(guest, house, teleport = true)
            }
        }
        if (!manager.rebuild(house, buildMode)) {
            mes("There is no space for your house right now. Try again shortly.")
            return
        }
        player.buildMode = buildMode
    }

    private suspend fun ProtectedAccess.enterFriendsHouse(town: HouseLocation) {
        val name = stringDialog("Enter name:").trim()
        if (name.isEmpty()) {
            return
        }
        val house = manager.findOpen(name, town)
        if (house == null) {
            mes("They don't seem to be at home.")
            return
        }
        if (house.buildMode) {
            mes("That player is in building mode.")
            return
        }
        manager.ownedBy(player)?.let { if (it !== house) exitHouse(player, it, teleport = false) }
        player.buildMode = false
        player.attr[InstanceAttributes.LOGIN_EXIT_COORD] = town.exit.packed
        manager.admit(player, house, manager.entrance(house.session))
    }

    private fun ProtectedAccess.leaveHouse() {
        val house = manager.houseOf(player) ?: return
        exitHouse(player, house, teleport = true)
    }

    private fun leaveOnLogout(player: Player) {
        val house = manager.houseOf(player) ?: return
        exitHouse(player, house, teleport = true)
        player.attr[InstanceAttributes.LOGIN_EXIT_COORD] = house.location.exit.packed
    }

    /**
     * Takes [player] out of [house]. With [teleport] false they have already left by other means -
     * a teleport spell, say - and only the bookkeeping is left to do. Guests stranded by the owner
     * leaving are always sent to the portal.
     */
    private fun exitHouse(player: Player, house: ActiveHouse, teleport: Boolean) {
        val stranded = manager.release(player)
        clearHouseState(player)
        if (teleport) {
            PathingEntityCommon.telejump(player, collision, house.location.exit)
        }
        for (guest in stranded) {
            clearHouseState(guest)
            guest.mes("The owner has left the house.")
            PathingEntityCommon.telejump(guest, collision, house.location.exit)
        }
    }

    private fun clearHouseState(player: Player) {
        player.attr.remove(InstanceAttributes.LOGIN_EXIT_COORD)
        player.buildMode = false
    }

    private fun starterLayout(layout: HouseLayout) {
        val garden = catalogue.all().firstOrNull { it.row.name == "garden" } ?: return
        layout.place(slotKey(LEVEL_GROUND, 4, 4), garden.id, rotation = 0)
    }

    /** The house [player] owns and is standing in, in building mode, or null with the reason given. */
    private fun ProtectedAccess.buildingHouse(): ActiveHouse? {
        val house = manager.houseOf(player) ?: return null
        if (house.ownerUid != player.uid) {
            mes("You can only build in your own house.")
            return null
        }
        if (!house.buildMode) {
            mes("You need to be in building mode to do that.")
            return null
        }
        return house
    }

    /**
     * Opens the build menu for the hotspot at [coords]. An empty hotspot offers the pieces that
     * stand on their own; [upgrade] offers the pieces that are built out of the one already there,
     * which is how a tool store becomes the next tool store up.
     */
    private suspend fun ProtectedAccess.openBuildMenu(
        coords: CoordGrid,
        locId: Int? = null,
        upgrade: Boolean = false,
    ) {
        val house = buildingHouse() ?: return
        val session = house.session
        val target = session.resolve(catalogue, houses, coords, locId) ?: return
        val built = session.layout.built(target.slot, target.hotspot.index)
        if (!upgrade && built != null) {
            mes("You need to remove the existing furniture first.")
            return
        }
        if (upgrade && built == null) {
            return
        }
        if (!inv.contains(OBJ_SAW) || !inv.contains(OBJ_HAMMER)) {
            mes("You need a hammer and a saw to build furniture.")
            return
        }

        val all = target.hotspot.builds
        val builds =
            all.filter { it.hiddenInBuildMenu != 1 }
                .filter { if (upgrade) upgradeSource(all, it)?.rowId == built else upgradeSource(all, it) == null }
        if (builds.isEmpty()) {
            if (upgrade) {
                mes("That can't be upgraded any further.")
            }
            return
        }

        ifOpenMainModal(INTERFACE_FURNITURE)
        val interfaceId = RSCM.getRSCM(INTERFACE_FURNITURE)
        for (slot in 0 until FURNITURE_SLOTS) {
            val furniture = builds.getOrNull(slot)
            val child = slot + FURNITURE_FIRST_ENTRY_CHILD
            player.ifSetHide(interfaceId, child, furniture == null)
            player.ifSetPosition(
                interfaceId,
                child,
                (slot % FURNITURE_ENTRY_COLUMNS) * FURNITURE_ENTRY_WIDTH,
                (slot / FURNITURE_ENTRY_COLUMNS) * FURNITURE_ENTRY_HEIGHT,
            )
            if (furniture == null) {
                player.runClientScript(entryScript, slot + 1, 0, -1, "", 0)
                continue
            }
            player.runClientScript(
                entryScript,
                slot + 1,
                furniture.rowId,
                furniture.buildLevel(),
                furniture.materialText(),
                if (canBuild(furniture)) 1 else 0,
            )
        }

        // The entry script clears its component and rebuilds the children, which drops any event
        // set on them, so the Build click is enabled only once an entry has been filled.
        for (slot in builds.indices) {
            ifSetEvents(
                furnitureEntryComponent(slot),
                0 until FURNITURE_ENTRY_CHILDREN,
                IfEvent.PauseButton,
            )
        }

        val input = pauseButton()
        ifClose()
        val chosen = builds.indices.firstOrNull { input.isComponentType(furnitureEntryComponent(it)) }
        if (chosen == null) {
            logger.warn { "Build menu click on an unknown component: '${input.component}'." }
            return
        }
        startBuild(target, builds[chosen], replaces = if (upgrade) built else null)
    }

    /** The piece [furniture] is built from, per `upgrade_source_relative`/`_absolute`, if any. */
    private fun upgradeSource(builds: List<FurnitureRow>, furniture: FurnitureRow): FurnitureRow? {
        val index = builds.indexOf(furniture)
        furniture.upgradeSourceRelative?.let { return builds.getOrNull(index - it) }
        furniture.upgradeSourceAbsolute?.let { return builds.getOrNull(it) }
        return null
    }

    private suspend fun ProtectedAccess.startBuild(
        target: HotspotTarget,
        furniture: FurnitureRow,
        replaces: Int?,
    ) {
        val required = furniture.buildLevel()
        if (player.constructionLvl < required) {
            mes("You need a Construction level of $required to build that.")
            return
        }
        if (!MaterialPayment.canAfford(inv, furniture.materials())) {
            mes("You don't have the materials to build that.")
            return
        }
        val variant = portalDestination(furniture) ?: if (isPortal(furniture)) return else null
        anim(SEQ_BUILD)
        weakQueue(
            QUEUE_BUILD,
            BUILD_TICKS,
            BuildTask(target.slot, target.rotation, target.hotspot.index, furniture.rowId, variant, replaces),
        )
    }

    /**
     * The checks are repeated because the queue runs ticks after the menu: the player may have
     * dropped a plank, left building mode or had the room removed under them in the meantime.
     */
    private fun ProtectedAccess.finishBuild(task: BuildTask) {
        resetAnim()
        val house = manager.houseOf(player) ?: return
        if (house.ownerUid != player.uid || !house.buildMode) {
            return
        }
        val session = house.session
        val placed = session.layout.placed(task.slot) ?: return
        if (placed.rotation != task.rotation) {
            return
        }
        val room = catalogue.room(placed.room) ?: return
        val hotspot = room.hotspot(task.hotspot) ?: return
        val furniture = hotspot.builds.firstOrNull { it.rowId == task.furniture } ?: return
        if (!inv.contains(OBJ_SAW) || !inv.contains(OBJ_HAMMER)) {
            mes("You need a hammer and a saw to build furniture.")
            return
        }
        var paid = true
        val built =
            session.layout.buildPaid(task.slot, task.hotspot, furniture.rowId, task.variant, task.replaces) {
                MaterialPayment.pay(player, inv, furniture.materials()).also { paid = it }
            }
        if (!built) {
            if (!paid) {
                mes("You don't have the materials to build that.")
            }
            return
        }

        val target = HotspotTarget(task.slot, task.rotation, hotspot)
        task.replaces?.let { houseFurniture.despawn(session, target, it) }
        player.storeLayout(session.layout)
        houseFurniture.spawn(session, task.slot, task.rotation, hotspot, furniture.rowId)
        statAdvance(STAT_CONSTRUCTION, furniture.xp() * xpMods.get(player, STAT_CONSTRUCTION))
        spam("You build a ${furniture.name}.")
    }

    private data class BuildTask(
        val slot: Int,
        val rotation: Int,
        val hotspot: Int,
        val furniture: Int,
        val variant: Int?,
        val replaces: Int?,
    )

    private suspend fun ProtectedAccess.removeFurniture(coords: CoordGrid, locId: Int? = null) {
        val house = buildingHouse() ?: return
        val session = house.session
        val target = session.resolve(catalogue, houses, coords, locId) ?: return
        val built = session.layout.built(target.slot, target.hotspot.index) ?: return
        if (menu("Really remove it?", hotkeys = false, choices = listOf("Yes", "No")) != 0) {
            return
        }
        anim(SEQ_BUILD)
        weakQueue(
            QUEUE_REMOVE,
            BUILD_TICKS,
            RemoveTask(target.slot, target.rotation, target.hotspot.index, built),
        )
    }

    private fun ProtectedAccess.finishRemove(task: RemoveTask) {
        resetAnim()
        val house = manager.houseOf(player) ?: return
        if (house.ownerUid != player.uid || !house.buildMode) {
            return
        }
        val session = house.session
        val placed = session.layout.placed(task.slot) ?: return
        val room = catalogue.room(placed.room) ?: return
        val hotspot = room.hotspot(task.hotspot) ?: return
        if (session.layout.built(task.slot, task.hotspot) != task.furniture) {
            return
        }
        session.layout.demolish(task.slot, task.hotspot)
        player.storeLayout(session.layout)
        houseFurniture.despawn(session, HotspotTarget(task.slot, placed.rotation, hotspot), task.furniture)
        spam("You remove the furniture.")
    }

    private data class RemoveTask(
        val slot: Int,
        val rotation: Int,
        val hotspot: Int,
        val furniture: Int,
    )

    /**
     * Builds a room through the doorway at [coords]: the room is picked from the add-room window,
     * then shown in place so it can be turned before it is paid for. A doorway that already leads
     * into a room offers to take that room out instead.
     */
    private suspend fun ProtectedAccess.openRoomMenu(coords: CoordGrid) {
        val house = buildingHouse() ?: return
        val door = house.session.resolveDoor(catalogue, houses, coords)
        if (door == null) {
            mes("This doorway is not one the room knows about. Try the other side of it.")
            return
        }
        val destination = neighbour(door.slot, door.direction)
        if (destination == null) {
            mes("You can't build a room there.")
            return
        }
        if (destination in house.layout.rooms) {
            offerRoomRemoval(house, destination)
            return
        }

        val room = chooseRoom() ?: return
        val level = slotLevel(destination)
        if (player.constructionLvl < room.levelRequirement) {
            mes("You need a Construction level of ${room.levelRequirement} to build a ${room.name.lowercase()}.")
            return
        }
        if (!room.allowedOn(level)) {
            mes(
                when (room.row.floorRestriction) {
                    FLOOR_DUNGEON -> "That room can only be built in a dungeon."
                    FLOOR_GROUND -> "That room can only be built on the ground floor."
                    else -> "That room can't be built down here."
                }
            )
            return
        }
        val facing = (door.direction + 2) and 3
        val rotations = (0..3).filter { facing in room.doorsAfter(it) }
        if (rotations.isEmpty()) {
            mes("That room has no doorway that would join on here.")
            return
        }
        if (inv.count(OBJ_COINS) < room.cost) {
            mes("You need ${room.cost} coins to build a ${room.name.lowercase()}.")
            return
        }
        previewRoom(house, destination, room, rotations)
    }

    private suspend fun ProtectedAccess.chooseRoom(): RoomDef? {
        ifOpenMainModal(INTERFACE_ADD_ROOM)
        ifSetEvents(COMPONENT_ADD_ROOM_LIST, 0 until ADD_ROOM_ENTRIES, IfEvent.PauseButton)
        val input = pauseButton()
        ifClose()
        if (!input.isComponentType(COMPONENT_ADD_ROOM_LIST)) {
            return null
        }
        return catalogue.all().filter { it.row.roomType == input.subcomponent }.minByOrNull { it.id }
    }

    /**
     * Places [room] for real and lets the player turn it between the rotations that join it to the
     * doorway they built from. It stays only once paid for; cancelling, or failing to pay, takes it
     * back out. Each turn rebuilds the house, which is also what the client shows as a reload.
     */
    private suspend fun ProtectedAccess.previewRoom(
        house: ActiveHouse,
        slot: Int,
        room: RoomDef,
        rotations: List<Int>,
    ) {
        var index = 0
        while (true) {
            house.layout.place(slot, room.id, rotations[index])
            if (!manager.rebuild(house)) {
                house.layout.remove(slot)
                mes("There is no space for your house right now. Try again shortly.")
                return
            }
            val choice =
                choice4(
                    "Rotate clockwise",
                    ROTATE_CLOCKWISE,
                    "Rotate anticlockwise",
                    ROTATE_ANTICLOCKWISE,
                    "Build",
                    BUILD,
                    "Cancel",
                    CANCEL,
                )
            if (manager.houseOf(player) !== house || house.layout.placed(slot)?.room != room.id) {
                return
            }
            when (choice) {
                ROTATE_CLOCKWISE -> index = (index + 1) % rotations.size
                ROTATE_ANTICLOCKWISE -> index = (index + rotations.size - 1) % rotations.size
                BUILD -> {
                    if (invDel(inv, OBJ_COINS, room.cost).failure) {
                        mes("You need ${room.cost} coins to build a ${room.name.lowercase()}.")
                        undoPreview(house, slot)
                        return
                    }
                    player.storeLayout(house.layout)
                    mes("You build a ${room.name.lowercase()}.")
                    return
                }
                else -> {
                    undoPreview(house, slot)
                    return
                }
            }
        }
    }

    private fun undoPreview(house: ActiveHouse, slot: Int) {
        house.layout.remove(slot)
        manager.rebuild(house)
    }

    private suspend fun ProtectedAccess.offerRoomRemoval(house: ActiveHouse, slot: Int) {
        val room = house.layout.placed(slot)?.room?.let(catalogue::room) ?: return
        house.layout.removalRefusal(slot)?.let {
            mes(it)
            return
        }
        if (house.occupants.any { house.session.slotAt(it.coords) == slot }) {
            mes("You can't remove a room while someone is standing in it.")
            return
        }
        val title = "Remove the ${room.name.lowercase()}? Anything built in it is lost."
        if (menu(title, "Remove it", "Keep it") != 0) {
            return
        }
        if (manager.houseOf(player) !== house || house.layout.placed(slot)?.room != room.id) {
            return
        }
        house.layout.remove(slot)
        player.storeLayout(house.layout)
        manager.rebuild(house)
        mes("You remove the ${room.name.lowercase()}.")
    }

    private fun ProtectedAccess.canBuild(furniture: FurnitureRow): Boolean {
        if (player.constructionLvl < furniture.buildLevel()) {
            return false
        }
        return MaterialPayment.canAfford(inv, furniture.materials())
    }

    private fun isPortal(furniture: FurnitureRow): Boolean =
        catalogue.builtLocIds(furniture).any(PohPortals::isPortalFrame)

    /**
     * A portal is one furniture row per material whichever destination it leads to, so the
     * destination is picked when it is built and kept as the layout's variant loc. Returns null for
     * anything that is not a portal, and for a portal whose menu was cancelled.
     */
    private suspend fun ProtectedAccess.portalDestination(furniture: FurnitureRow): Int? {
        if (!isPortal(furniture)) {
            return null
        }
        val material =
            PohPortals.MATERIALS.firstOrNull { m ->
                catalogue.builtLocIds(furniture).any { locName(it).contains("_${m}_") }
            } ?: return null
        val labels = PohPortals.labels()
        val chosen = PohPortals.destinationAt(menu("Where should it lead?", hotkeys = false, choices = labels + "Cancel"))
        return chosen?.let { PohPortals.portalLoc(material, it) }
    }

    private fun locName(locId: Int): String = RSCM.getReverseMapping(RSCMType.LOC, locId)

    private fun ProtectedAccess.enterPortal(destination: CoordGrid) {
        telejump(destination)
        spam("You step through the portal.")
    }

    private fun Player.layout(): HouseLayout = HouseLayout.decode(attr[LAYOUT])

    private fun Player.storeLayout(layout: HouseLayout) {
        attr[LAYOUT] = layout.encode()
    }

    private companion object {
        const val BUILD_TICKS = 3

        const val OP_UPGRADE = 3
        const val UPGRADE = "Upgrade"

        const val ADD_ROOM_ENTRIES = 30

        const val ROTATE_CLOCKWISE = 0
        const val ROTATE_ANTICLOCKWISE = 1
        const val BUILD = 2
        const val CANCEL = 3

        val LAYOUT: AttributeKey<String> = AttributeKey(persistenceKey = "poh_layout")
    }
}
