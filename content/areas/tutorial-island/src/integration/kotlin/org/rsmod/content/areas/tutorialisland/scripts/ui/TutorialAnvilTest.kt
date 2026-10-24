package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.skills.smithing.AnvilSmithingScript
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class TutorialAnvilTest {
    @Test
    fun GameTestState.`the anvil opens the smithing menu during the dagger step`() =
        runInjectedGameTest(AnvilDependencies::class, null, AnvilSmithingScript::class) { deps ->
            val anvil = CoordGrid(1680, 12450, 0)
            allocZoneCollision(anvil)
            player.teleport(anvil.translateZ(-1))
            player.clearInv()
            player.inv[0] = InvObj("obj.hammer")
            player.inv[1] = InvObj("obj.bronze_bar")
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.MineDagger)

            player.opLoc1(placeMapLoc(anvil, "loc.anvil"))
            advance(ticks = 4)
            assertTrue(player.ui.containsModal("interface.smithing"))
        }
}

private class AnvilDependencies @Inject constructor(val progression: TutorialProgression)
