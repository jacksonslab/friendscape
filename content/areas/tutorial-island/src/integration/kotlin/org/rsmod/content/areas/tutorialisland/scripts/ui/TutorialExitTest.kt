package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.TutorialTeleportHook
import org.rsmod.content.areas.tutorialisland.progression.TutorialLeave
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.map.CoordGrid

class TutorialExitTest {
    @Test
    fun GameTestState.`ordinary teleports are blocked but the tutorial exit succeeds exactly once`() =
        runInjectedGameTest(ExitDependencies::class) { deps ->
            val origin = CoordGrid(0, 26, 95, 22, 35)
            allocZoneCollision(origin)
            allocZoneCollision(TutorialLeave.LUMBRIDGE_SPAWN)
            player.teleport(origin)
            player.clearInv()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            val validator = PlayerTeleportValidator(setOf(deps.hook))
            assertNotNull(validator.validate(player, TeleportType.Standard, deps.areas))
            assertNull(validator.validate(player, TeleportType.Exempt, deps.areas))
            assertEquals(origin, player.coords)
            player.withProtectedAccess { deps.leave.giveLeaveKit(this) }
            advance()
            assertEquals(TutorialLeave.LUMBRIDGE_SPAWN, player.coords)
            assertFalse(deps.progression.isActive(player))
            assertEquals(25, player.inv.count("obj.bronze_arrow"))
            player.withProtectedAccess { deps.leave.giveLeaveKit(this) }
            advance()
            assertEquals(25, player.inv.count("obj.bronze_arrow"))
        }
}

private class ExitDependencies @Inject constructor(
    val leave: TutorialLeave,
    val progression: TutorialProgression,
    val hook: TutorialTeleportHook,
    val areas: AreaChecker,
)
