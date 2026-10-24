package org.rsmod.content.skills.sailing

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailingSessionScript
@Inject
constructor(private val boats: BoatManager, private val cargo: CargoService) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.Startup> { checkMoorings() }
        onEvent<SessionStateEvent.PrepareLogin> {
            if (player.sailingShore != 0) {
                player.coords = CoordGrid(player.sailingShore)
                player.lastKnownNormalCoord = player.coords
                player.sailingShore = 0
            }
            player.aboardPlayerBoat = 0
            player.helmLockedIn = 0
        }
        onEvent<SessionStateEvent.EngineLoginReady> {
            cargo.refreshCarrying(player)
        }
        onEvent<SessionStateEvent.Logout> {
            if (player.sailingShore != 0 || boats.boatOf(player) != null) {
                boats.returnToShore(player)
            }
            boats.removeOwnedBy(player)
        }
    }

    private fun checkMoorings() {
        val missing = ArrayList<String>()
        for (port in Ports.ALL) {
            for (type in BoatTypes.ALL) {
                if (boats.findMooring(type, port.gangplankCoord) == null) {
                    missing += "${port.name}/${type.key}"
                }
            }
        }
        if (missing.isEmpty()) {
            logger.info { "Sailing: every boat can moor at all ${Ports.ALL.size} ports." }
        } else {
            logger.warn { "Sailing: no mooring found for ${missing.size} port/boat pairs: ${missing.joinToString()}" }
        }
    }
}
