package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class SailingTestDeps
@Inject
constructor(val shipbuilding: Shipbuilding, val cargo: CargoService, val held: HeldInteractions)

private val TILE = CoordGrid(0, 50, 50, 20, 20)
private val LEDGER_TILE = TILE.translateX(1)
private const val SARIM_LEDGER = "loc.dock_loading_bay_ledger_table_port_sarim"
private const val PANDEMONIUM_LEDGER = "loc.dock_loading_bay_ledger_table_pandemonium"
private const val MUSA_LEDGER = "loc.dock_loading_bay_ledger_table_musa_point"
private const val TINY_BAG = "obj.port_task_lootsack_t0_coins"
private const val PLATEBODY_TASK = 1
private const val FISH_TASK = 8

private fun obj(internal: String): ItemServerType = ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))!!

private fun crate(taskId: Int): ItemServerType = CourierTasks[taskId]!!.crate

private fun GameTestScope.prepare() {
    allocZoneCollision(TILE)
    player.teleport(TILE)
    player.clearInv()
    player.setBaseLevel(STAT_SAILING, 99)
    player.setCurrentLevel(STAT_SAILING, 99)
    for (slot in 0 until TaskSlots.COUNT) {
        TaskSlots.clear(player, slot)
    }
    player.carryingCargo = 0
}

private fun GameTestScope.assign(taskId: Int, taken: Int = 0, delivered: Int = 0) {
    TaskSlots.set(player, TaskSlot(0, taskId, taken, delivered))
}

private fun GameTestScope.coinBags(): Int =
    (0..4).sumOf { tier -> player.inv.count(obj("obj.port_task_lootsack_t${tier}_coins")) }

@Execution(ExecutionMode.SAME_THREAD)
class SailingItemSafetyTest {
    @Test
    fun GameTestState.`taking cargo hands over exactly one crate`() =
        runGameTest(PortTaskScript::class) {
            prepare()
            assign(PLATEBODY_TASK)
            val ledger = placeMapLoc(LEDGER_TILE, SARIM_LEDGER)
            player.opLoc1(ledger)
            advance(ticks = 3)
            assertEquals(1, player.inv.count(crate(PLATEBODY_TASK)))
            assertEquals(1, TaskSlots.get(player, 0).taken)
            assertEquals(1, player.carryingCargo)

            player.opLoc1(ledger)
            advance(ticks = 3)
            assertEquals(1, player.inv.count(crate(PLATEBODY_TASK)))
            assertEquals(1, TaskSlots.get(player, 0).taken)
        }

    @Test
    fun GameTestState.`taking cargo with a full inventory gives nothing`() =
        runGameTest(PortTaskScript::class) {
            prepare()
            assign(PLATEBODY_TASK)
            player.fillInv()
            player.opLoc1(placeMapLoc(LEDGER_TILE, SARIM_LEDGER))
            advance(ticks = 3)
            assertEquals(0, player.inv.count(crate(PLATEBODY_TASK)))
            assertEquals(0, TaskSlots.get(player, 0).taken)
        }

    @Test
    fun GameTestState.`delivering completes the task once with one reward`() =
        runGameTest(PortTaskScript::class) {
            prepare()
            assign(PLATEBODY_TASK, taken = 1)
            player.inv[0] = InvObj(crate(PLATEBODY_TASK).internalName)
            player.carryingCargo = 1
            val xp = player.statMap.getFineXP(STAT_SAILING)
            val ledger = placeMapLoc(LEDGER_TILE, PANDEMONIUM_LEDGER)
            player.opLoc1(ledger)
            advance(ticks = 3)
            player.opLoc1(ledger)
            advance(ticks = 3)
            assertEquals(0, player.inv.count(crate(PLATEBODY_TASK)))
            assertEquals(1, coinBags())
            assertEquals(0, TaskSlots.get(player, 0).taskId)
            assertTrue(player.statMap.getFineXP(STAT_SAILING) > xp)
        }

    @Test
    fun GameTestState.`a crate is refused at the wrong port`() =
        runGameTest(PortTaskScript::class) {
            prepare()
            assign(PLATEBODY_TASK, taken = 1)
            player.inv[0] = InvObj(crate(PLATEBODY_TASK).internalName)
            player.carryingCargo = 1
            player.opLoc1(placeMapLoc(LEDGER_TILE, SARIM_LEDGER))
            advance(ticks = 3)
            assertEquals(1, player.inv.count(crate(PLATEBODY_TASK)))
            assertEquals(0, TaskSlots.get(player, 0).delivered)
        }

    @Test
    fun GameTestState.`partial delivery keeps the task open without a reward`() =
        runGameTest(PortTaskScript::class) {
            prepare()
            assign(FISH_TASK, taken = 1)
            player.inv[0] = InvObj(crate(FISH_TASK).internalName)
            player.carryingCargo = 1
            player.opLoc1(placeMapLoc(LEDGER_TILE, MUSA_LEDGER))
            advance(ticks = 3)
            assertEquals(0, player.inv.count(crate(FISH_TASK)))
            assertEquals(1, TaskSlots.get(player, 0).delivered)
            assertEquals(FISH_TASK, TaskSlots.get(player, 0).taskId)
            assertEquals(0, coinBags())
        }

    @Test
    fun GameTestState.`opening a coin bag refuses on coin overflow and pays out otherwise`() =
        runInjectedGameTest(SailingTestDeps::class, null, PortTaskScript::class) { deps ->
            prepare()
            player.inv[0] = InvObj(TINY_BAG)
            player.inv[1] = InvObj("obj.coins", Int.MAX_VALUE - 100)
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
            advance(ticks = 2)
            assertEquals(1, player.inv.count(obj(TINY_BAG)))
            assertEquals(Int.MAX_VALUE - 100, player.inv.count(obj("obj.coins")))

            player.clearInv()
            player.inv[0] = InvObj(TINY_BAG)
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
            advance(ticks = 2)
            assertEquals(0, player.inv.count(obj(TINY_BAG)))
            assertTrue(player.inv.count(obj("obj.coins")) in 800..1200)
        }

    @Test
    fun GameTestState.`relocation confiscates crates and reopens them on the task`() =
        runInjectedGameTest(SailingTestDeps::class, null, PortTaskScript::class) { deps ->
            prepare()
            assign(FISH_TASK, taken = 2)
            player.inv[0] = InvObj(crate(FISH_TASK).internalName)
            player.withProtectedAccess {
                invAdd(deps.cargo.hold(this), crate(FISH_TASK).internalName)
                deps.cargo.confiscate(this)
            }
            advance()
            assertEquals(0, player.inv.count(crate(FISH_TASK)))
            assertEquals(0, TaskSlots.get(player, 0).taken)
            assertEquals(0, player.carryingCargo)
        }

    @Test
    fun GameTestState.`commissioning a skiff takes exactly its materials or nothing`() =
        runInjectedGameTest(SailingTestDeps::class, null) { deps ->
            prepare()
            val skiff = BoatTypes.byKey("skiff")!!
            val cost = deps.shipbuilding.materials(skiff)
            assertTrue(cost.isNotEmpty())
            fun noted(obj: ItemServerType): String =
                if (obj.canCert) ServerCacheManager.getItem(obj.certlink)!!.internalName else obj.internalName
            fun held(obj: ItemServerType): Int {
                var total = 0
                player.withProtectedAccess { total = deps.shipbuilding.held(this, obj) }
                return total
            }
            for ((material, count) in cost) {
                player.withProtectedAccess { invAdd(inv, noted(material), count + 1) }
            }
            val short = cost.entries.first()
            player.withProtectedAccess { invDel(inv, noted(short.key), 2) }
            var built = true
            player.withProtectedAccess { built = deps.shipbuilding.build(this, skiff) }
            advance()
            assertFalse(built)
            assertFalse(player.ownsBoat(skiff))
            assertEquals(short.value - 1, held(short.key))

            player.withProtectedAccess { invAdd(inv, noted(short.key), 2) }
            player.withProtectedAccess { built = deps.shipbuilding.build(this, skiff) }
            advance()
            assertTrue(built)
            assertTrue(player.ownsBoat(skiff))
            for ((material, _) in cost) {
                assertEquals(1, held(material))
            }
        }
}
