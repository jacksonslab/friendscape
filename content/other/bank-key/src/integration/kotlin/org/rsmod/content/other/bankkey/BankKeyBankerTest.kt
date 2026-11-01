package org.rsmod.content.other.bankkey

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.game.entity.NpcList
import org.rsmod.map.CoordGrid

class BankKeyBankerTest {
    @Test
    fun GameTestState.`repeated summons replace the banker and opening a bank removes it`() =
        runInjectedGameTest(BankerDependencies::class) { deps ->
            val tile = CoordGrid(3222, 3218)
            allocZoneCollision(tile)
            player.teleport(tile)
            deps.bankers.summon(player)
            val first = deps.npcs.single { it.type.isType(BankKeyScript.BANKER) }
            assertTrue(deps.bankers.owns(player, first))
            deps.bankers.summon(player)
            assertFalse(first.isSlotAssigned)
            val replacement = deps.npcs.single { it.type.isType(BankKeyScript.BANKER) }
            assertTrue(deps.bankers.owns(player, replacement))
            player.withProtectedAccess { assertTrue(tryOpenBank()) }
            advance(ticks = 1)
            deps.bankers.maintain(player)
            assertFalse(replacement.isSlotAssigned)
            assertEquals(0, deps.npcs.count { it.type.isType(BankKeyScript.BANKER) })
        }

    @Test
    fun GameTestState.`unused banker disappears after its lifetime`() =
        runInjectedGameTest(BankerDependencies::class) { deps ->
            val tile = CoordGrid(3222, 3218)
            allocZoneCollision(tile)
            player.teleport(tile)
            deps.bankers.summon(player)
            val banker = deps.npcs.single { it.type.isType(BankKeyScript.BANKER) }
            advance(ticks = 101)
            deps.bankers.maintain(player)
            assertFalse(banker.isSlotAssigned)
            assertFalse(deps.bankers.owns(player, banker))
        }
}

private class BankerDependencies @Inject constructor(val bankers: KeyBankers, val npcs: NpcList)
