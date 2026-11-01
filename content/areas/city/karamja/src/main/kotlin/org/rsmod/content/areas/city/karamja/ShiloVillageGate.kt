package org.rsmod.content.areas.city.karamja

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ShiloVillageGate : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.zqshilogateclosedl") { passGate(it.loc) }
        onOpLoc1("loc.zqshilogateclosedr") { passGate(it.loc) }
    }

    private suspend fun ProtectedAccess.passGate(gate: BoundLocInfo) {
        if (!QuestRequirements.hasCompleted(player, SHILO_VILLAGE)) {
            mes("The gate is locked. You need to complete Shilo Village to enter.")
            return
        }
        val outside = coords.x >= gate.coords.x
        val across = if (outside) gate.coords.translateX(-1) else gate.coords
        soundSynth(GATE_SOUND)
        mes(if (outside) ENTER_MESSAGE else LEAVE_MESSAGE)
        delay(1)
        teleport(across)
    }

    private companion object {
        const val SHILO_VILLAGE = "quest_shilovillage"
        const val GATE_SOUND = "synth.picketgate_open"
        const val ENTER_MESSAGE = "You open the gate and walk into Shilo Village."
        const val LEAVE_MESSAGE = "You open the gate and leave Shilo Village."
    }
}
