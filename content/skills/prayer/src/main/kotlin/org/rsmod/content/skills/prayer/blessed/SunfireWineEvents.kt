package org.rsmod.content.skills.prayer.blessed

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SunfireWineEvents : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU("obj.jug_wine", "obj.sunfiresplinter") { startMakeSunfireWine() }
        onOpHeldU("obj.jug_wine_blessed", "obj.sunfiresplinter") { rejectBlessedWineMix() }
        onPlayerQueue("queue.prayer_make_sunfire_wine") { processMakeSunfireWine() }
    }

    private fun ProtectedAccess.startMakeSunfireWine() {
        if (!canMakeSunfireWine()) {
            return
        }
        processMakeSunfireWine()
    }

    private fun ProtectedAccess.processMakeSunfireWine() {
        if (!canMakeSunfireWine()) {
            return
        }

        val splinter = "obj.sunfiresplinter"
        val result = player.invTransaction(inv) {
            val target = select(inv)
            delete(target, splinter.asRSCM(RSCMType.OBJ), 2)
            delete(target, "obj.jug_wine".asRSCM(RSCMType.OBJ), 1)
            add(target, "obj.jug_sunfire_wine".asRSCM(RSCMType.OBJ), 1)
        }
        if (!result.success) return

        player.anim("seq.human_herbing_grind")
        mes("You crush the sunfire splinters into the wine, making a jug of sunfire wine.")

        if (canMakeSunfireWine()) {
            weakQueue("queue.prayer_make_sunfire_wine", 4)
        }
    }

    private fun ProtectedAccess.canMakeSunfireWine(): Boolean {
        if (!inv.contains("obj.pestle_and_mortar")) {
            mes("You need a pestle and mortar to crush the splinters into the wine.")
            return false
        }
        val splinter = "obj.sunfiresplinter"
        val splinterCount = inv.count(splinter)
        if (splinterCount < 2) {
            return false
        }
        val wineCount = inv.count("obj.jug_wine")
        if (wineCount <= 0) {
            return false
        }
        return true
    }

    private fun ProtectedAccess.rejectBlessedWineMix() {
        mes("You can only add sunfire splinters to an unblessed jug of wine.")
    }
}
