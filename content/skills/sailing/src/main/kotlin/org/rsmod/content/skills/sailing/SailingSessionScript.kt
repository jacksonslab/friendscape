package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailingSessionScript @Inject constructor(private val boats: BoatManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.PrepareLogin> {
            if (player.sailingShore != 0) {
                player.coords = CoordGrid(player.sailingShore)
                player.lastKnownNormalCoord = player.coords
                player.sailingShore = 0
            }
            player.aboardPlayerBoat = 0
            player.helmLockedIn = 0
        }
        onEvent<SessionStateEvent.Logout> {
            if (player.sailingShore != 0 || boats.boatOf(player) != null) boats.returnToShore(player)
            boats.removeOwnedBy(player)
        }
    }
}
