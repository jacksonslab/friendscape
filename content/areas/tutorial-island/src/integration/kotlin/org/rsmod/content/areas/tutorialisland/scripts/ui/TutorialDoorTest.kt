package org.rsmod.content.areas.tutorialisland.scripts.ui

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.areas.tutorialisland.scripts.locs.TutorialGatesScript
import org.rsmod.content.generic.locs.doors.DoorScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class TutorialDoorTest {
    @Test
    fun GameTestState.`starting house door opens crosses and closes back to the tutorial door`() =
        runInjectedGameTest(DoorDependencies::class, null, TutorialGatesScript::class, DoorScript::class) { deps ->
            val tile = CoordGrid(1690, 6115, 0)
            allocZoneCollision(tile)
            player.teleport(CoordGrid(1689, 6115, 0))
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.GielinorDoor)
            val door = placeMapLoc(tile, "loc.tut2_survival_entry", LocShape.WallStraight, LocAngle.West)
            player.opLoc1(door)
            advance(ticks = 4)
            assertEquals(tile, player.coords)
            assertEquals(TutorialStep.SurvivalTalk, deps.progression.current(player))
            val opened = deps.locs.findAll(tile.translateX(-1)).firstOrNull { ServerCacheManager.getObject(it.id)?.internalName == "loc.poordooropen" }
            assertNotNull(opened)
            player.opLoc1(BoundLocInfo(opened!!, ServerCacheManager.getObject(opened.id)!!))
            advance(ticks = 6)
            assertTrue(deps.locs.findLoc(tile, "loc.tut2_survival_entry"))
            assertFalse(deps.locs.findLoc(tile.translateX(-1), "loc.poordooropen"))
        }

    @Test
    fun GameTestState.`starting house door stays closed before guide tasks are complete`() =
        runInjectedGameTest(DoorDependencies::class, null, TutorialGatesScript::class) { deps ->
            val tile = CoordGrid(1690, 6115, 0)
            val start = CoordGrid(1689, 6115, 0)
            allocZoneCollision(tile)
            player.teleport(start)
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.GielinorTalk)
            player.opLoc1(placeMapLoc(tile, "loc.tut2_survival_entry", LocShape.WallStraight, LocAngle.West))
            advance(ticks = 4)
            assertEquals(start, player.coords)
            assertTrue(deps.locs.findLoc(tile, "loc.tut2_survival_entry"))
            assertEquals(TutorialStep.GielinorTalk, deps.progression.current(player))
        }
}

private class DoorDependencies @Inject constructor(val progression: TutorialProgression, val locs: LocRepository)
