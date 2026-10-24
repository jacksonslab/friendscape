package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GangplankEvents
@Inject
constructor(private val boats: BoatManager, private val cargo: CargoService) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.sailing_gangplank_embark") { boardOwn(it.loc, chooseBoat = false) }
        onOpLoc2("loc.sailing_gangplank_embark") { boardOwn(it.loc, chooseBoat = true) }
        onOpLoc3("loc.sailing_gangplank_embark") { boardFriend(it.loc) }
        onOpLoc1("loc.sailing_gangplank_disembark") { disembark(it.loc) }
    }

    private suspend fun ProtectedAccess.boardOwn(loc: BoundLocInfo, chooseBoat: Boolean) {
        val port = Ports.byGangplank(loc.id) ?: return
        if (!meetsDockLevel(port)) {
            return
        }
        val type = if (chooseBoat) chooseBoat() ?: return else activeBoat()
        val existing = boats.ownedBy(player)
        val relocating = existing?.port != port || existing.type != type
        val fee = retrievalFee(type, port)
        if (relocating && fee > 0 && invCoinTotal() < fee) {
            mes("You need $fee coins to have your ${type.name.lowercase()} brought to ${port.name}.")
            return
        }
        val boat = boats.moorFor(player, type, port, loc.coords)
        if (boat == null) {
            mes("There's no room to moor your ${type.name.lowercase()} here right now.")
            return
        }
        if (relocating && fee > 0) {
            if (!invTakeFee(fee)) {
                boats.despawn(boat)
                mes("You need $fee coins to have your ${type.name.lowercase()} brought to ${port.name}.")
                return
            }
            mes("You pay $fee coins to have your ${type.name.lowercase()} brought to ${port.name}.")
        }
        if (relocating && fee > 0 && cargo.confiscate(this)) {
            mes("The cargo you had loaded is returned to its ledger tables.")
        }
        player.sailingBoatDock = port.dockRowId + 1
        climbAboard(boat, loc)
        mes("You board your ${type.name.lowercase()}.")
    }

    private suspend fun ProtectedAccess.boardFriend(loc: BoundLocInfo) {
        val port = Ports.byGangplank(loc.id) ?: return
        if (!meetsDockLevel(port)) {
            return
        }
        val moored = boats.all.filter { it.port == port && !it.isOwnedBy(player) && it.owner != null }
        if (moored.isEmpty()) {
            mes("There are no other boats moored here.")
            return
        }
        val boat = pickBoat(moored) ?: return
        if (boats.passengers(boat).size >= boat.type.boat.playerCapacity) {
            mes("That boat is full.")
            return
        }
        climbAboard(boat, loc)
        mes("You board ${boat.ownerName}'s ${boat.type.name.lowercase()}.")
    }

    private suspend fun ProtectedAccess.pickBoat(moored: List<Boat>): Boat? {
        val options = moored.take(MAX_BOAT_CHOICES)
        if (options.size == 1) {
            return options.single()
        }
        val labels = options.map { "${it.ownerName}'s ${it.type.name.lowercase()}" }
        return choose(labels, options)
    }

    private fun ProtectedAccess.climbAboard(boat: Boat, plank: BoundLocInfo) {
        player.aboardPlayerBoat = 1
        player.sailingShore = plank.coords.packed
        player.lastKnownNormalCoord = plank.coords
        telejump(boat.boardDest, TeleportType.Exempt)
    }

    private fun ProtectedAccess.disembark(loc: BoundLocInfo) {
        val port = Ports.byGangplank(loc.id) ?: return
        val boat = boats.boatOf(player) ?: return
        if (boat.isOwnedBy(player) || boat.port != port) {
            if (!boat.isStopped) {
                mes("You need to bring your boat to a stop first.")
                return
            }
            if (!boat.isOwnedBy(player) && boat.port != port) {
                mes("The captain needs to moor the boat here first.")
                return
            }
            if (!boats.moorAt(boat, port, loc.coords)) {
                mes("Your boat is too far from the dock to moor here.")
                return
            }
            player.sailingBoatDock = port.dockRowId + 1
        }
        boats.releaseHelm(player)
        player.aboardPlayerBoat = 0
        player.sailingShore = 0
        telejump(loc.coords, TeleportType.Exempt)
        mes("You disembark at ${port.name}.")
    }

    private fun ProtectedAccess.meetsDockLevel(port: Port): Boolean {
        if (statBase(STAT_SAILING) >= port.level) {
            return true
        }
        mes("You need a Sailing level of ${port.level} to use the dock at ${port.name}.")
        return false
    }

    private fun ProtectedAccess.activeBoat(): BoatType {
        val chosen = BoatTypes.byTypeId(player.sailingActiveBoat)
        if (chosen != null && player.ownsBoat(chosen) && statBase(STAT_SAILING) >= chosen.levelRequired) {
            return chosen
        }
        return BoatTypes.RAFT
    }

    private suspend fun ProtectedAccess.chooseBoat(): BoatType? {
        val usable = BoatTypes.ALL.filter { player.ownsBoat(it) && statBase(STAT_SAILING) >= it.levelRequired }
        if (usable.size <= 1) {
            mes("You only have your raft to sail.")
            return BoatTypes.RAFT
        }
        val type = choose(usable.map { it.name }, usable) ?: return null
        player.sailingActiveBoat = type.typeId
        return type
    }

    private fun ProtectedAccess.retrievalFee(type: BoatType, port: Port): Int {
        val lastDock = player.sailingBoatDock
        if (lastDock == 0 || lastDock == port.dockRowId + 1) {
            return 0
        }
        return type.boat.retrievalCost
    }

    private companion object {
        private const val MAX_BOAT_CHOICES = 4
    }
}
