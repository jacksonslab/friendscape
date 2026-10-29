package org.rsmod.content.areas.tutorialisland.scripts.ui

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.gameframe.script.GameframeScript
import org.rsmod.events.EventBus
import org.rsmod.game.entity.player.SessionStateEvent

class GameframeMinimapLayoutTest {
    @Test
    fun GameTestState.`restoring classic resizable frame repairs stale modern minimap selection`() =
        checkRestoredLayout("interface.toplevel_osrs_stretch", 0)

    @Test
    fun GameTestState.`restoring modern resizable frame repairs stale classic minimap selection`() =
        checkRestoredLayout("interface.toplevel_pre_eoc", 1)

    private fun GameTestState.checkRestoredLayout(topLevel: String, expectedArrangement: Int) =
        runInjectedGameTest(LayoutDependencies::class, null, GameframeScript::class) { deps ->
            val frame = topLevel.asRSCM()
            player.ui.frameResizable = true
            VarPlayerIntMapSetter.set(player, "varbit.gameframe_toplevel", frame)
            VarPlayerIntMapSetter.set(player, "varbit.resizable_stone_arrangement", 1 - expectedArrangement)
            deps.events.publish(SessionStateEvent.Initialize(player))
            assertEquals(expectedArrangement, player.vars["varbit.resizable_stone_arrangement"])
            assertEquals(frame, player.vars["varbit.gameframe_toplevel"])
        }
}

private class LayoutDependencies @Inject constructor(val events: EventBus)
