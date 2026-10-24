package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.rsmod.api.player.hook.HomeTeleportRequest
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.tutorialisland.progression.TutorialLeave
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TutorialMagicUiScript @Inject constructor(
    private val progression: TutorialProgression,
    private val leave: TutorialLeave,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<HomeTeleportRequest> {
            if (progression.isActive(access.player)) {
                handled = true
                if (progression.current(access.player) == TutorialStep.HomeTele) {
                    leave.giveLeaveKit(access)
                } else {
                    access.mes("Finish talking to the Magic Instructor before leaving the island.")
                }
            }
        }
    }
}
