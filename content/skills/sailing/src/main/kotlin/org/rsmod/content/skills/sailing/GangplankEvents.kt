package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GangplankEvents
@Inject
constructor(private val boats: BoatManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.sailing_gangplank_embark") {
            if (!player.modLevel.isAtLeast(dev.or2.central.account.Rights.ADMINISTRATOR)) {
                mes("Sailing is being prepared. The dock is not open yet.")
                return@onOpLoc1
            }
            val dock = Docks.nearest(it.loc.coords)
            if (dock == null) {
                mes("Nothing interesting happens.")
                return@onOpLoc1
            }
            val boat = boats.retrieve(player, BoatTypes.RAFT, dock)
            if (boat == null) {
                mes("Your boat cannot moor here right now.")
                return@onOpLoc1
            }
            player.aboardPlayerBoat = 1
            player.sailingShore = dock.returnTile.packed
            player.lastKnownNormalCoord = dock.returnTile
            telejump(boat.boardDest, TeleportType.Exempt)
            mes("You board your boat.")
        }
        onOpLoc1("loc.sailing_gangplank_disembark") {
            val boat = boats.boatOf(player) ?: return@onOpLoc1
            val dock = boats.moorNearby(boat)
            if (dock == null) {
                mes("Moor your boat at a dock before disembarking.")
                return@onOpLoc1
            }
            val dest = dock.returnTile
            boats.releaseHelm(player)
            player.aboardPlayerBoat = 0
            player.sailingShore = 0
            telejump(dest, TeleportType.Exempt)
            mes("You disembark at ${dock.name}.")
        }
    }
}
