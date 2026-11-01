package org.rsmod.content.bosses.gemstonecrab

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GemstoneCrabScript
@Inject
constructor(
    private val sites: GemstoneCrabSites,
    private val mining: GemstoneCrabMiningScript,
    private val cave: GemstoneCrabCaveScript,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { sites.all.forEach(GemstoneCrabManager::tick) }

        for (crab in sites.all) {
            crab.site.areas.forEachIndexed { index, area ->
                onArea(area) { crab.enterSpotArea(player, index) }
                onAreaExit(area) { crab.exitSpotArea(player, index) }
            }
            onOpNpc1(crab.site.remains) { with(mining) { attempt(it.npc) } }
        }

        onOpLoc1("loc.cave_rock02_entrance01_gemstone") { with(cave) { crawl(it.loc) } }

        onCommand("gemstonecrab") {
            desc = "Force every active gemstone crab to burrow immediately"
            cheat { forceBurrow() }
        }

        sites.all.forEach(GemstoneCrabManager::start)
    }

    private fun Cheat.forceBurrow() {
        sites.all.forEach(GemstoneCrabManager::forceBurrow)
        player.mes("Forced the gemstone crabs to burrow.")
    }
}
