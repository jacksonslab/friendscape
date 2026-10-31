package org.rsmod.content.generic.locs.doors

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.generic.locs.gate.PicketGate
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class VarrockDoorsTest {
    @Test
    fun GameTestState.`poor house doors open`() =
        assertDoorOpens("loc.fai_varrock_poor_door", "loc.fai_varrock_poor_door_open")

    @Test
    fun GameTestState.`flipped poor house doors open`() =
        assertDoorOpens("loc.fai_varrock_poor_door_flipped", "loc.fai_varrock_poor_door_open_flipped")

    @Test
    fun GameTestState.`shanty doors open`() =
        assertDoorOpens("loc.fai_varrock_shanty_door", "loc.fai_varrock_shanty_door_open")

    @Test
    fun GameTestState.`taller doors open`() =
        assertDoorOpens("loc.fai_varrock_door_taller", "loc.fai_varrock_door_open_taller")

    @Test
    fun GameTestState.`farm gates south of Varrock open`() =
        assertGateOpens("loc.fai_varrock_gate_l", "loc.fai_varrock_gate_r")

    @Test
    fun GameTestState.`fence gates by the Cooks' Guild open`() =
        assertGateOpens("loc.ii_fencegate_l", "loc.ii_fencegate_r")

    private fun GameTestState.assertDoorOpens(closed: String, opened: String) =
        runInjectedGameTest(LocDependencies::class, null, DoorScript::class) { deps ->
            allocZoneCollision(DOOR)
            player.teleport(DOOR.translateZ(-1))
            val door = placeMapLoc(DOOR, closed, LocShape.WallStraight, LocAngle.South)
            player.opLoc1(door)
            advance(ticks = 3)
            assertFalse(deps.locs.findLoc(DOOR, closed))
            assertTrue(deps.locs.findLoc(DOOR.translateZ(-1), opened) || deps.locs.findLoc(DOOR, opened))
        }

    private fun GameTestState.assertGateOpens(left: String, right: String) =
        runInjectedGameTest(LocDependencies::class, null, PicketGate::class) { deps ->
            allocZoneCollision(GATE)
            player.teleport(GATE.translateZ(-1))
            val gate = placeMapLoc(GATE, left, LocShape.WallStraight, LocAngle.South)
            placeMapLoc(GATE.translateX(-1), right, LocShape.WallStraight, LocAngle.South)
            player.opLoc1(gate)
            advance(ticks = 3)
            assertFalse(deps.locs.findLoc(GATE, left))
            assertFalse(deps.locs.findLoc(GATE.translateX(-1), right))
        }

    private companion object {
        private val DOOR = CoordGrid(3190, 3384, 0)
        private val GATE = CoordGrid(3214, 3354, 0)
    }
}

private class LocDependencies @Inject constructor(val locs: LocRepository)
