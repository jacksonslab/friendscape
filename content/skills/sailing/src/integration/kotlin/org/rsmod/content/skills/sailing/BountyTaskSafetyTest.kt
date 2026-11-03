package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class BountyTestDeps @Inject constructor(val rewards: BountyRewards)

private val BOUNTY_TILE = CoordGrid(0, 50, 50, 22, 22)
private const val TERN_FEATHERS = 438
private const val OSPREY_BEAKS = 442
private const val TERN_BEAKS = 449

private fun item(internal: String): ItemServerType = ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))!!

private fun bounty(id: Int): BountyTask = BountyTasks[id]!!

private fun GameTestScope.prepareBounties() {
    allocZoneCollision(BOUNTY_TILE)
    player.teleport(BOUNTY_TILE)
    player.clearInv()
    player.setBaseLevel(STAT_SAILING, 99)
    player.setCurrentLevel(STAT_SAILING, 99)
    for (slot in 0 until TaskSlots.COUNT) {
        TaskSlots.clear(player, slot)
    }
}

private fun GameTestScope.hold(vararg ids: Int) {
    ids.forEachIndexed { slot, id -> TaskSlots.set(player, TaskSlot(slot, id, 0, 0)) }
}

private fun GameTestScope.giveParts(task: BountyTask, count: Int, firstSlot: Int = 0) {
    if (task.item.stackable) {
        player.inv[firstSlot] = InvObj(task.item.internalName, count)
    } else {
        repeat(count) { player.inv[firstSlot + it] = InvObj(task.item.internalName) }
    }
}

private fun GameTestScope.parts(task: BountyTask): Int = player.inv.count(task.item)

private fun GameTestScope.coins(): Int = player.inv.count(item("obj.coins"))

@Execution(ExecutionMode.SAME_THREAD)
class BountyTaskSafetyTest {
    @Test
    fun GameTestState.`claiming takes exactly the bounty's parts and pays its coins`() =
        runInjectedGameTest(BountyTestDeps::class, null) { deps ->
            prepareBounties()
            val task = bounty(TERN_FEATHERS)
            hold(task.id)
            giveParts(task, task.amount + 2)
            var claimed = 0
            player.withProtectedAccess { claimed = deps.rewards.claim(this) }
            advance()
            assertEquals(1, claimed)
            assertEquals(2, parts(task))
            assertEquals(task.xp, coins())
            assertEquals(0, TaskSlots.get(player, 0).taskId)

            player.withProtectedAccess { claimed = deps.rewards.claim(this) }
            advance()
            assertEquals(0, claimed)
            assertEquals(2, parts(task))
            assertEquals(task.xp, coins())
        }

    @Test
    fun GameTestState.`claiming with too few parts changes nothing`() =
        runInjectedGameTest(BountyTestDeps::class, null) { deps ->
            prepareBounties()
            val task = bounty(TERN_FEATHERS)
            hold(task.id)
            giveParts(task, task.amount - 1)
            player.withProtectedAccess { deps.rewards.claim(this) }
            advance()
            assertEquals(task.amount - 1, parts(task))
            assertEquals(0, coins())
            assertEquals(task.id, TaskSlots.get(player, 0).taskId)
        }

    @Test
    fun GameTestState.`a coin overflow rolls the whole claim back`() =
        runInjectedGameTest(BountyTestDeps::class, null) { deps ->
            prepareBounties()
            val task = bounty(TERN_FEATHERS)
            hold(task.id)
            player.inv[0] = InvObj("obj.coins", Int.MAX_VALUE - 1)
            giveParts(task, task.amount, firstSlot = 1)
            player.withProtectedAccess { deps.rewards.claim(this) }
            advance()
            assertEquals(task.amount, parts(task))
            assertEquals(Int.MAX_VALUE - 1, coins())
            assertEquals(task.id, TaskSlots.get(player, 0).taskId)
        }

    @Test
    fun GameTestState.`each completed bounty pays once and incomplete ones stay held`() =
        runInjectedGameTest(BountyTestDeps::class, null) { deps ->
            prepareBounties()
            val feathers = bounty(TERN_FEATHERS)
            val beaks = bounty(OSPREY_BEAKS)
            hold(feathers.id, beaks.id)
            giveParts(feathers, feathers.amount)
            giveParts(beaks, beaks.amount - 1, firstSlot = 20)
            player.withProtectedAccess { deps.rewards.claim(this) }
            advance()
            assertEquals(0, parts(feathers))
            assertEquals(beaks.amount - 1, parts(beaks))
            assertEquals(feathers.xp, coins())
            assertEquals(0, TaskSlots.get(player, 0).taskId)
            assertEquals(beaks.id, TaskSlots.get(player, 1).taskId)
        }

    @Test
    fun GameTestState.`a kill rolls for one bounty at a time and stops once its parts are in`() =
        runInjectedGameTest(BountyTestDeps::class, null) { deps ->
            prepareBounties()
            val feathers = bounty(TERN_FEATHERS)
            val beaks = bounty(TERN_BEAKS)
            check(feathers.target == beaks.target)
            hold(feathers.id, beaks.id)

            random.next = 1
            assertTrue(deps.rewards.rollKill(player, feathers.target))
            assertEquals(1, parts(feathers))
            assertEquals(0, parts(beaks))

            random.next = 2
            assertFalse(deps.rewards.rollKill(player, feathers.target))
            assertEquals(1, parts(feathers))

            player.clearInv()
            giveParts(feathers, feathers.amount)
            random.next = 1
            assertTrue(deps.rewards.rollKill(player, feathers.target))
            assertEquals(feathers.amount, parts(feathers))
            assertEquals(1, parts(beaks))
        }

    @Test
    fun GameTestState.`kills of other creatures or without a bounty give nothing`() =
        runInjectedGameTest(BountyTestDeps::class, null) { deps ->
            prepareBounties()
            val feathers = bounty(TERN_FEATHERS)
            random.next = 1
            assertFalse(deps.rewards.rollKill(player, feathers.target))
            hold(OSPREY_BEAKS)
            random.next = 1
            assertFalse(deps.rewards.rollKill(player, feathers.target))
            assertEquals(0, parts(feathers))
        }

    @Test
    fun GameTestState.`the board pins held tasks first, then takeable, then locked, and hides open bounties`() =
        runGameTest {
            val sarim = Ports.byDock(bounty(TERN_FEATHERS).board)!!
            val level = 30
            val courier = CourierTasks.offeredAt(sarim)
            val heldCourier = courier.first { it.level <= level }
            val held = setOf(heldCourier.id, TERN_FEATHERS)
            val notices = PortTaskBoard.notices(sarim, level, held)

            assertEquals(PortTaskBoard.NOTICES.coerceAtMost(courier.size + 1), notices.size)
            assertEquals(held, notices.take(2).map { it.id }.toSet())
            assertTrue(notices.drop(2).none { it is BountyTask })
            val levels = notices.drop(2).map { it.level }
            val firstLocked = levels.indexOfFirst { it > level }.let { if (it < 0) levels.size else it }
            assertEquals(levels.take(firstLocked).sortedDescending(), levels.take(firstLocked))
            assertTrue(levels.drop(firstLocked).all { it > level })

            val open = PortTaskBoard.notices(sarim, 99, held, bounties = true)
            assertTrue(open.any { it is BountyTask && it.id != TERN_FEATHERS })
            assertEquals(0, PortTaskBoard.noticeIndex(0))
            assertEquals(2, PortTaskBoard.noticeIndex(2 * PortTaskBoard.COMPONENTS_PER_NOTICE))
        }
}
