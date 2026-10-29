package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.progression.TutorialLeave
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class TutorialLeaveTest {
    @Test
    fun GameTestState.`instructor exit teleports before completing and cannot duplicate the kit`() =
        runInjectedGameTest(LeaveDependencies::class) { deps ->
            val island = CoordGrid(1719, 6115, 0)
            allocZoneCollision(island)
            allocZoneCollision(TutorialLeave.LUMBRIDGE_SPAWN)
            player.teleport(island)
            player.clearInv()
            player.bank.fillNulls()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.LeaveTalk)
            player.withProtectedAccess { deps.leave.giveLeaveKit(this) }
            advance(ticks = 1)
            assertEquals(TutorialLeave.LUMBRIDGE_SPAWN, player.coords)
            assertFalse(deps.progression.isActive(player))
            assertEquals(25, player.inv.count("obj.bronze_arrow"))
            assertEquals(25, player.bank.count("obj.coins"))
            player.inv[0] = InvObj("obj.ashes", 1)
            player.withProtectedAccess { deps.leave.giveLeaveKit(this) }
            advance(ticks = 1)
            assertEquals(1, player.inv.count("obj.ashes"))
            assertEquals(25, player.inv.count("obj.bronze_arrow"))
        }

    @Test
    fun GameTestState.`completed player stranded on island can leave without receiving another kit`() =
        runInjectedGameTest(LeaveDependencies::class) { deps ->
            val island = CoordGrid(1719, 6115, 0)
            allocZoneCollision(island)
            allocZoneCollision(TutorialLeave.LUMBRIDGE_SPAWN)
            player.teleport(island)
            player.clearInv()
            player.inv[0] = InvObj("obj.ashes", 1)
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.Completed)
            player.withProtectedAccess { deps.leave.giveLeaveKit(this) }
            advance(ticks = 1)
            assertEquals(TutorialLeave.LUMBRIDGE_SPAWN, player.coords)
            assertEquals(1, player.inv.count("obj.ashes"))
            assertEquals(0, player.inv.count("obj.bronze_arrow"))
        }
}

private class LeaveDependencies @Inject constructor(val leave: TutorialLeave, val progression: TutorialProgression)

private val Player.bank get() = invMap.getOrPut("inv.bank")
