package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailingControlPanelScript @Inject constructor(private val panel: SailingControlPanel) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { panel.tick() }
        onEvent<SessionStateEvent.EngineLoginReady> { panel.reset(player) }
        onEvent<SessionStateEvent.Logout> { panel.forget(player) }
        onIfOverlayButton(SailingControlPanel.BUTTONS) { panel.press(this, it.comsub, it.op) }
        onIfOverlayButton("component.sailing_sidepanel:switch_button") { panel.combat(player) }
        onIfOverlayButton("component.combat_interface:switch_button") { panel.open(player) }
    }
}
