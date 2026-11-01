package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SpyglassScript @Inject constructor(private val boats: BoatManager, private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(SPYGLASS) { observe() }
        onOpHeld2(SPYGLASS) { CaptainLog.open(this, boats.boatOf(player)?.entity?.coords ?: player.coords) }
        onOpHeld3("obj.sailing_log") { CaptainLog.open(this, boats.boatOf(player)?.entity?.coords ?: player.coords) }
        onOpHeld4("obj.sailing_log") { CaptainLog.tasks(this) }
        onOpHeld4("obj.sailing_log_initial") { CaptainLog.tasks(this) }
    }

    private suspend fun ProtectedAccess.observe() {
        val boat = boats.boatOf(player)
        if (boat != null && !boat.isStopped) {
            mes("Bring the boat to a stop before looking through your spyglass.")
            return
        }
        val coords = boat?.entity?.coords ?: player.coords
        val nearby = SeaCharting.OBSERVATIONS.filter { it.contains(coords) }
        val observation = nearby.firstOrNull { !SeaCharting.isComplete(player, it.core) } ?: nearby.firstOrNull()
        if (observation == null) {
            mes("There's nothing nearby worth charting with your spyglass. Inspect it for charting hints.")
            return
        }
        if (SeaCharting.isComplete(player, observation.core)) {
            mes("You've already recorded this view in your captain's log.")
            return
        }
        if (stat(STAT_SAILING) < observation.row.level) {
            mes("You need a Sailing level of ${observation.row.level} to chart this view.")
            return
        }
        mes("You look through your spyglass and study the view.")
        delay(OBSERVE_TICKS)
        val currentBoat = boats.boatOf(player)
        if (SPYGLASS !in inv || currentBoat != boat || (currentBoat != null && !currentBoat.isStopped)) return
        if (!observation.contains(currentBoat?.entity?.coords ?: player.coords)) return
        if (stat(STAT_SAILING) < observation.row.level) return
        val xp = SeaCharting.xp(observation.core) * xpMods.get(player, STAT_SAILING)
        if (!SeaCharting.complete(player, observation.core)) return
        statAdvance(STAT_SAILING, xp)
        mes("You record the view of ${observation.core.sailingSea.name} in your captain's log.")
    }

    private companion object {
        private const val SPYGLASS = "obj.sailing_charting_spyglass"
        private const val OBSERVE_TICKS = 2
    }
}
