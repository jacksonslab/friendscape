package org.rsmod.content.areas.tutorialisland.scripts.ui

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.areas.tutorialisland.scripts.skills.TutorialSkillsScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class TutorialFishingTest {
    @Test
    fun GameTestState.`fishing works across the blocked shoreline without moving into water`() =
        runInjectedGameTest(FishingDependencies::class, null, TutorialSkillsScript::class) { deps ->
            val spot = prepare(deps)
            deps.flags.add(1693, 6101, 0, CollisionFlag.WALL_SOUTH)
            deps.flags.add(1693, 6100, 0, CollisionFlag.WALL_NORTH)
            assertEquals(false, deps.bounds.touches(player.avatar, spot.avatar))
            val before = player.statMap.getFineXP("stat.fishing")
            player.opNpc1(spot)
            advance(ticks = 4)
            assertEquals(CoordGrid(1693, 6101, 0), player.coords)
            assertEquals(1, player.inv.count("obj.tut2_raw_shrimp"))
            assertEquals(1, player.inv.count("obj.net"))
            assertEquals(TutorialStep.SurvivalSkills, deps.progression.current(player))
            assertTrue(player.statMap.getFineXP("stat.fishing") > before)
        }

    @Test
    fun GameTestState.`full inventory cannot grant fishing XP or advance the lesson`() =
        runInjectedGameTest(FishingDependencies::class, null, TutorialSkillsScript::class) { deps ->
            val spot = prepare(deps)
            player.fillInv()
            player.inv[0] = InvObj("obj.net")
            player.inv[1] = InvObj("obj.tut2_raw_shrimp")
            val before = player.statMap.getFineXP("stat.fishing")
            player.opNpc1(spot)
            advance(ticks = 4)
            assertEquals(1, player.inv.count("obj.tut2_raw_shrimp"))
            assertEquals(before, player.statMap.getFineXP("stat.fishing"))
            assertEquals(TutorialStep.SurvivalFish, deps.progression.current(player))
        }

    @Test
    fun GameTestState.`fishing without a net grants no fish or XP`() =
        runInjectedGameTest(FishingDependencies::class, null, TutorialSkillsScript::class) { deps ->
            val spot = prepare(deps)
            player.clearInv()
            val before = player.statMap.getFineXP("stat.fishing")
            player.opNpc1(spot)
            advance(ticks = 4)
            assertEquals(0, player.inv.count("obj.tut2_raw_shrimp"))
            assertEquals(before, player.statMap.getFineXP("stat.fishing"))
            assertEquals(TutorialStep.SurvivalFish, deps.progression.current(player))
        }

    private fun GameTestScope.prepare(deps: FishingDependencies): Npc {
        val water = CoordGrid(1693, 6100, 0)
        allocZoneCollision(water)
        player.teleport(CoordGrid(1693, 6101, 0))
        player.clearInv()
        player.inv[0] = InvObj("obj.net")
        VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
        deps.progression.setStep(player, TutorialStep.SurvivalFish)
        return spawnNpc(water, ServerCacheManager.getNpc("npc.0_26_95_tut2_fishing".asRSCM())!!)
    }
}

private class FishingDependencies @Inject constructor(
    val progression: TutorialProgression,
    val flags: CollisionFlagMap,
    val bounds: BoundValidator,
)
