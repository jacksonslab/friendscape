package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class DraynorManorBackDoorTest {
    @Test
    fun GameTestState.`back door lets a player out of the spade room and closes behind them`() =
        runInjectedGameTest(BackDoorDependencies::class, null, DraynorManorDoors::class) { deps ->
            allocZoneCollision(BACK_DOOR)
            player.teleport(SPADE_ROOM)
            val door = placeMapLoc(BACK_DOOR, "loc.hauntedbackdoor", LocShape.WallStraight, LocAngle.South)
            player.opLoc1(door)
            advance(ticks = 3)
            assertEquals(BACK_DOOR, player.coords)
            assertFalse(deps.locs.findLoc(BACK_DOOR, "loc.hauntedbackdoor"))
            advance(ticks = 4)
            assertTrue(deps.locs.findLoc(BACK_DOOR, "loc.hauntedbackdoor"))
            assertFalse(deps.locs.findLoc(SPADE_ROOM, "loc.hauntedbackdoor_inactive"))
        }

    @Test
    fun GameTestState.`back door also lets a player back into the spade room`() =
        runInjectedGameTest(BackDoorDependencies::class, null, DraynorManorDoors::class) {
            allocZoneCollision(BACK_DOOR)
            player.teleport(BACK_DOOR)
            val door = placeMapLoc(BACK_DOOR, "loc.hauntedbackdoor", LocShape.WallStraight, LocAngle.South)
            player.opLoc1(door)
            advance(ticks = 3)
            assertEquals(SPADE_ROOM, player.coords)
        }

    private companion object {
        private val BACK_DOOR = CoordGrid(3123, 3361, 0)
        private val SPADE_ROOM = CoordGrid(3123, 3360, 0)
    }
}

private class BackDoorDependencies @Inject constructor(val locs: LocRepository)
