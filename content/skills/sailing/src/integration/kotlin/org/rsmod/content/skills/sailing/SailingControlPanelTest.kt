package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.map.CoordGrid

class BoatPanelTestDeps @Inject constructor(val boats: BoatManager, val panel: SailingControlPanel)

@Execution(ExecutionMode.SAME_THREAD)
class SailingControlPanelTest {
    @Test
    fun GameTestState.`native sail buttons control the owner's boat and leaving restores the combat tab`() =
        runInjectedGameTest(BoatPanelTestDeps::class, null, SailingControlPanelScript::class) { deps ->
            val boat = prepareBoat(deps)
            deps.panel.tick()
            assertTrue(player.ui.containsOverlay(SailingControlPanel.PANEL))
            player.helmLockedIn = 3
            boat.helmsman = player
            val buttons = ServerCacheManager.fromComponent(RSCM.getRSCM(SailingControlPanel.BUTTONS))
            player.ifButton(buttons, comsub = 0)
            advance(ticks = 2)
            assertEquals(SailingMoveModes.FULL, boat.moveMode)
            assertEquals(boat.type.baseSpeed, boat.targetSpeed)
            player.ifButton(buttons, comsub = 1)
            advance(ticks = 2)
            assertEquals(boat.type.baseSpeed / 2, boat.targetSpeed)
            player.ifButton(buttons, comsub = 1)
            advance(ticks = 2)
            assertEquals(0, boat.targetSpeed)
            player.ifButton(buttons, comsub = 1)
            advance(ticks = 2)
            assertTrue(boat.targetSpeed < 0)
            player.aboardPlayerBoat = 0
            deps.panel.tick()
            assertFalse(player.ui.containsOverlay(SailingControlPanel.PANEL))
            assertTrue(player.ui.containsOverlay("interface.combat_interface"))
        }

    @Test
    fun GameTestState.`panel clicks cannot change a boat without its helm or with a forged button slot`() =
        runInjectedGameTest(BoatPanelTestDeps::class, null, SailingControlPanelScript::class) { deps ->
            val boat = prepareBoat(deps)
            deps.panel.tick()
            player.withProtectedAccess { deps.panel.press(this, 0, IfButtonOp.Op1) }
            advance()
            assertEquals(0, boat.targetSpeed)
            boat.helmsman = player
            player.helmLockedIn = 3
            player.withProtectedAccess { deps.panel.press(this, 10, IfButtonOp.Op1) }
            advance()
            assertEquals(0, boat.targetSpeed)
            boat.owner = null
            player.withProtectedAccess { deps.panel.press(this, 0, IfButtonOp.Op1) }
            advance()
            assertEquals(0, boat.targetSpeed)
        }

    @Test
    fun GameTestState.`native switch buttons return to combat and back without releasing the helm`() =
        runInjectedGameTest(BoatPanelTestDeps::class, null, SailingControlPanelScript::class) { deps ->
            val boat = prepareBoat(deps)
            boat.helmsman = player
            player.helmLockedIn = 3
            deps.panel.tick()
            player.ifButton(ServerCacheManager.fromComponent(RSCM.getRSCM("component.sailing_sidepanel:switch_button")), comsub = 11)
            advance(ticks = 2)
            assertTrue(player.ui.containsOverlay("interface.combat_interface"))
            assertEquals(player, boat.helmsman)
            player.ifButton(ServerCacheManager.fromComponent(RSCM.getRSCM("component.combat_interface:switch_button")))
            advance(ticks = 2)
            assertEquals(player, boat.helmsman)
            assertTrue(player.ui.containsOverlay(SailingControlPanel.PANEL))
        }

    private fun GameTestScope.prepareBoat(deps: BoatPanelTestDeps): Boat {
        val shore = CoordGrid(3051, 3193, 0)
        allocZoneCollision(shore)
        player.teleport(shore)
        val boat = checkNotNull(deps.boats.spawn(BoatTypes.RAFT, 0, 3104 * 128, 3181 * 128, owner = player))
        allocZoneCollision(boat.boardDest)
        player.teleport(boat.boardDest)
        player.aboardPlayerBoat = 1
        return boat
    }
}
