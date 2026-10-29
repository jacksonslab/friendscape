package org.rsmod.content.skills.sailing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

private val TILE = CoordGrid(0, 50, 50, 20, 20)
private val LEDGER_TILE = TILE.translateX(1)
private const val SARIM_LEDGER = "loc.dock_loading_bay_ledger_table_port_sarim"
private const val PANDEMONIUM_LEDGER = "loc.dock_loading_bay_ledger_table_pandemonium"
private const val CUP = PandemoniumExchanges.CUP
private const val FILLER_TASK = 1

private fun GameTestScope.prepare() {
    allocZoneCollision(TILE)
    player.teleport(TILE)
    player.clearInv()
    player.setBaseLevel(STAT_SAILING, 1)
    player.setCurrentLevel(STAT_SAILING, 1)
    for (slot in 0 until TaskSlots.COUNT) {
        TaskSlots.clear(player, slot)
    }
    player.carryingCargo = 0
}

private fun GameTestScope.takeJimsJob(): JimsJob {
    var result: JimsJob? = null
    player.withProtectedAccess { result = PandemoniumExchanges.takeJimsJob(this) }
    advance(ticks = 1)
    return checkNotNull(result)
}

private fun GameTestScope.haulUpCup(): Boolean {
    var result: Boolean? = null
    player.withProtectedAccess { result = PandemoniumExchanges.haulUpCup(this) }
    advance(ticks = 1)
    return checkNotNull(result)
}

private fun GameTestScope.pandemoniumSlots(): Int =
    TaskSlots.active(player).count { it.taskId == CourierTasks.PANDEMONIUM.id }

@Execution(ExecutionMode.SAME_THREAD)
class PandemoniumItemSafetyTest {
    @Test
    fun GameTestState.`the hook hauls up one cup and none into a full pack`() =
        runGameTest {
            prepare()
            assertTrue(haulUpCup())
            assertEquals(1, player.inv.count(CUP))
            player.fillInv()
            val before = player.inv.objs.toList()
            assertFalse(haulUpCup())
            assertEquals(before, player.inv.objs.toList())
        }

    @Test
    fun GameTestState.`Jim takes exactly one cup and assigns his job once`() =
        runGameTest {
            prepare()
            player.inv[0] = InvObj(CUP)
            player.inv[1] = InvObj(CUP)
            assertEquals(JimsJob.Assigned, takeJimsJob())
            assertEquals(JimsJob.AlreadyHeld, takeJimsJob())
            assertEquals(1, player.inv.count(CUP))
            assertEquals(1, pandemoniumSlots())
        }

    @Test
    fun GameTestState.`without a free task slot Jim keeps the job and the player keeps the cup`() =
        runGameTest {
            prepare()
            TaskSlots.set(player, TaskSlot(0, FILLER_TASK, taken = 0, delivered = 0))
            player.inv[0] = InvObj(CUP)
            assertEquals(JimsJob.NoFreeSlot, takeJimsJob())
            assertEquals(1, player.inv.count(CUP))
            assertEquals(0, pandemoniumSlots())
        }

    @Test
    fun GameTestState.`Jim's job is never offered on a notice board and cannot be cancelled`() =
        runGameTest {
            val task = CourierTasks.PANDEMONIUM
            assertFalse(task.cancellable)
            for (port in Ports.ALL) {
                assertFalse(task in CourierTasks.offeredAt(port), port.name)
            }
        }

    @Test
    fun GameTestState.`Jim's crate is collected once at Port Sarim and delivered at the Pandemonium`() =
        runGameTest(PortTaskScript::class) {
            prepare()
            assertEquals(JimsJob.Assigned, takeJimsJob())
            val crate = CourierTasks.PANDEMONIUM.crate
            val sarim = placeMapLoc(LEDGER_TILE, SARIM_LEDGER)
            player.opLoc1(sarim)
            advance(ticks = 3)
            player.opLoc1(sarim)
            advance(ticks = 3)
            assertEquals(1, player.inv.count(crate))

            val xp = player.statMap.getFineXP(STAT_SAILING)
            val pandemonium = placeMapLoc(TILE.translateX(-1), PANDEMONIUM_LEDGER)
            player.opLoc1(pandemonium)
            advance(ticks = 3)
            player.opLoc1(pandemonium)
            advance(ticks = 3)
            assertEquals(0, player.inv.count(crate))
            assertEquals(0, pandemoniumSlots())
            assertTrue(player.statMap.getFineXP(STAT_SAILING) > xp)
        }
}
