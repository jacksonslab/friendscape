package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.areas.tutorialisland.scripts.skills.TutorialSkillsScript
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class TutorialMiningTest {
    @Test
    fun GameTestState.`mining tin then copper gives one of each and advances to smelting`() =
        runInjectedGameTest(MiningDependencies::class, null, TutorialSkillsScript::class) { deps ->
            val tin = CoordGrid(1680, 12450, 0)
            val copper = tin.translateX(2)
            allocZoneCollision(tin)
            player.teleport(tin.translateZ(-4))
            player.clearInv()
            player.inv[0] = InvObj("obj.bronze_pickaxe")
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.MineOres)

            player.opLoc1(placeMapLoc(tin, "loc.tut2_tinrock"))
            advance(ticks = 10)
            assertEquals(1, player.inv.count("obj.tin_ore"))

            player.opLoc1(placeMapLoc(copper, "loc.tut2_copperrock"))
            advance(ticks = 10)
            assertEquals(1, player.inv.count("obj.copper_ore"))
            assertEquals(TutorialStep.MineSmelt, deps.progression.current(player))
        }

    @Test
    fun GameTestState.`a wielded pickaxe mines too`() =
        runInjectedGameTest(MiningDependencies::class, null, TutorialSkillsScript::class) { deps ->
            val tin = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tin)
            player.teleport(tin.translateZ(-1))
            player.clearInv()
            player.worn[3] = InvObj("obj.bronze_pickaxe")
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.MineOres)

            player.opLoc1(placeMapLoc(tin, "loc.tut2_tinrock"))
            advance(ticks = 10)
            assertEquals(1, player.inv.count("obj.tin_ore"))
        }
}

private class MiningDependencies @Inject constructor(val progression: TutorialProgression)
