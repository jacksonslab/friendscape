package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onApLoc2
import org.rsmod.api.script.onApLoc5
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailEvents @Inject constructor(private val boats: BoatManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (sail in SAIL_LOCS) {
            onApLoc1(sail) { trimSails() }
            onApLoc2(sail) { setSails() }
            onApLoc5(sail) { unsetSails() }
        }
    }

    private fun ProtectedAccess.setSails() {
        val boat = controlledBoat() ?: return
        if (boat.targetSpeed > 0) {
            mes("Your sails are already set.")
            return
        }
        boat.moveMode = SailingMoveModes.FULL
        boat.targetSpeed = boat.type.baseSpeed
        boat.port = null
        mes("You set the sails.")
    }

    private fun ProtectedAccess.unsetSails() {
        val boat = controlledBoat() ?: return
        when {
            boat.targetSpeed != 0 -> {
                boat.moveMode = SailingMoveModes.STOPPED
                boat.targetSpeed = 0
                mes("You un-set the sails.")
            }
            !boat.isStopped -> mes("Your boat is still slowing down.")
            else -> {
                boat.moveMode = SailingMoveModes.REVERSE
                boat.targetSpeed = -boat.type.baseSpeed / REVERSE_DIVISOR
                boat.port = null
                mes("You back the sails and your boat starts to reverse.")
            }
        }
    }

    private fun ProtectedAccess.trimSails() {
        val boat = controlledBoat() ?: return
        if (boat.targetSpeed <= 0) {
            mes("You need to set the sails before you can trim them.")
            return
        }
        mes("There's no gust of wind to catch right now.")
    }

    private fun ProtectedAccess.controlledBoat(): Boat? {
        val boat = boats.boatOf(player) ?: return null
        if (!boat.isOwnedBy(player) && boat.helmsman != player) {
            mes("Only the captain can adjust these sails.")
            return null
        }
        return boat
    }

    private companion object {
        private const val SAIL_PREFIX = "loc.sailing_boat_sail_"
        private const val REVERSE_DIVISOR = 3

        private val SAIL_LOCS: List<String> by lazy {
            ServerCacheManager.getObjects()
                .filterValues { type -> type.actions.ops.any { it?.text == "Un-set" } }
                .keys
                .mapNotNull { runCatching { RSCM.getReverseMapping(RSCMType.LOC, it) }.getOrNull() }
                .filter { it.startsWith(SAIL_PREFIX) }
                .sorted()
        }
    }
}
