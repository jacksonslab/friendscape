package org.rsmod.content.raids.tob

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.raids.tob.rewards.TobLoot
import org.rsmod.content.raids.tob.rewards.TobLoot.claimLoot
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class TobLootTest {
    @Test
    fun GameTestState.`claiming moves every stored item exactly once`() =
        runGameTest {
            player.clearInv()
            TobLoot.store(player, listOf(DEATH_RUNE to 550, NOTED_COAL to 520, SCYTHE to 1))
            var first: TobLoot.Claim? = null
            var second: TobLoot.Claim? = null
            player.withProtectedAccess {
                first = claimLoot()
                second = claimLoot()
            }
            assertTrue(first is TobLoot.Claim.Claimed)
            assertEquals(TobLoot.Claim.Empty, second)
            assertEquals(550, player.inv.count(DEATH_RUNE))
            assertEquals(520, player.inv.count(NOTED_COAL))
            assertEquals(1, player.inv.count(SCYTHE))
            assertFalse(TobLoot.hasUnclaimed(player))
        }

    @Test
    fun GameTestState.`a full inventory takes nothing and keeps the loot stored`() =
        runGameTest {
            player.clearInv()
            fillAllBut(freeSlots = 0)
            TobLoot.store(player, listOf(DEATH_RUNE to 550, SCYTHE to 1))
            var result: TobLoot.Claim? = null
            player.withProtectedAccess { result = claimLoot() }
            assertTrue(result is TobLoot.Claim.NoRoom)
            assertEquals(0, player.inv.count(DEATH_RUNE))
            assertEquals(0, player.inv.count(SCYTHE))
            assertEquals(mapOf(DEATH_RUNE to 550, SCYTHE to 1), TobLoot.unclaimed(player))
        }

    @Test
    fun GameTestState.`room for only part of the loot moves none of it`() =
        runGameTest {
            player.clearInv()
            fillAllBut(freeSlots = 1)
            TobLoot.store(player, listOf(DEATH_RUNE to 550, SCYTHE to 1))
            var result: TobLoot.Claim? = null
            player.withProtectedAccess { result = claimLoot() }
            assertTrue(result is TobLoot.Claim.NoRoom)
            assertEquals(0, player.inv.count(DEATH_RUNE))
            assertEquals(0, player.inv.count(SCYTHE))
            assertEquals(2, TobLoot.unclaimed(player).size)
        }

    @Test
    fun GameTestState.`a stack that would overflow rolls the whole claim back`() =
        runGameTest {
            player.clearInv()
            player.inv[0] = InvObj(DEATH_RUNE.asRSCM(RSCMType.OBJ), Int.MAX_VALUE - 100)
            TobLoot.store(player, listOf(DEATH_RUNE to 550, SCYTHE to 1))
            var result: TobLoot.Claim? = null
            player.withProtectedAccess { result = claimLoot() }
            assertTrue(result is TobLoot.Claim.NoRoom)
            assertEquals(Int.MAX_VALUE - 100, player.inv.count(DEATH_RUNE))
            assertEquals(0, player.inv.count(SCYTHE))
            assertEquals(550, TobLoot.unclaimed(player)[DEATH_RUNE])
        }

    @Test
    fun GameTestState.`storing twice adds up and caps at the stack limit`() =
        runGameTest {
            player.clearInv()
            TobLoot.store(player, listOf(DEATH_RUNE to 500))
            TobLoot.store(player, listOf(DEATH_RUNE to 100, SCYTHE to 1))
            assertEquals(600, TobLoot.unclaimed(player)[DEATH_RUNE])
            TobLoot.store(player, listOf(DEATH_RUNE to Int.MAX_VALUE))
            assertEquals(Int.MAX_VALUE, TobLoot.unclaimed(player)[DEATH_RUNE])
            TobLoot.store(player, listOf(SCYTHE to 0, SCYTHE to -5))
            assertEquals(1, TobLoot.unclaimed(player)[SCYTHE])
        }

    private fun GameTestScope.fillAllBut(freeSlots: Int) {
        val filler = InvObj(FILLER.asRSCM(RSCMType.OBJ), 1)
        for (slot in 0 until player.inv.size - freeSlots) {
            player.inv[slot] = filler
        }
    }

    private companion object {
        const val DEATH_RUNE = "obj.deathrune"
        const val NOTED_COAL = "obj.cert_coal"
        const val SCYTHE = "obj.scythe_of_vitur_uncharged"
        const val FILLER = "obj.beer"
    }
}
