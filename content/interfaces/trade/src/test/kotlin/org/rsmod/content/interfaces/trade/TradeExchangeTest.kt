package org.rsmod.content.interfaces.trade

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TradeExchangeTest {
    @Test
    fun `offering part of a stack moves exactly that many`() {
        val alice = trader(1)
        alice.give(COINS, 500)
        assertEquals(100, TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(COINS), 100))
        assertEquals(400, alice.inv.count(COINS))
        assertEquals(100, alice.tradeOffer.count(COINS))
    }

    @Test
    fun `offering five of an item you hold three of moves three`() {
        val alice = trader(1)
        repeat(3) { alice.give(LOBSTER) }
        assertEquals(3, TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(LOBSTER), 5))
        assertEquals(0, alice.inv.count(LOBSTER))
        assertEquals(3, alice.tradeOffer.count(LOBSTER))
    }

    @Test
    fun `offering from an emptied slot moves nothing`() {
        val alice = trader(1)
        alice.give(COINS, 10)
        val slot = alice.slotOf(COINS)
        alice.inv[slot] = null
        assertEquals(0, TradeExchange.move(alice, alice.inv, alice.tradeOffer, slot, 10))
        assertTrue(alice.tradeOffer.isEmpty())
    }

    @Test
    fun `completing swaps both offers and keeps every item`() {
        val (alice, bob) = trader(1) to trader(2)
        alice.give(COINS, 1000)
        repeat(2) { bob.give(LOBSTER) }
        TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(COINS), 600)
        TradeExchange.move(bob, bob.inv, bob.tradeOffer, bob.slotOf(LOBSTER), 2)
        assertTrue(TradeExchange.complete(alice, bob))
        assertEquals(400, alice.inv.count(COINS))
        assertEquals(2, alice.inv.count(LOBSTER))
        assertEquals(600, bob.inv.count(COINS))
        assertEquals(0, bob.inv.count(LOBSTER))
        assertTrue(alice.tradeOffer.isEmpty())
        assertTrue(bob.tradeOffer.isEmpty())
    }

    @Test
    fun `completing again after a trade gives nothing more`() {
        val (alice, bob) = trader(1) to trader(2)
        alice.give(COINS, 100)
        TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(COINS), 100)
        assertTrue(TradeExchange.complete(alice, bob))
        TradeExchange.complete(alice, bob)
        assertEquals(0, alice.inv.count(COINS))
        assertEquals(100, bob.inv.count(COINS))
    }

    @Test
    fun `a full receiver blocks the whole trade`() {
        val (alice, bob) = trader(1) to trader(2)
        repeat(2) { alice.give(LOBSTER) }
        bob.give(COINS, 50)
        TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(LOBSTER), 2)
        TradeExchange.move(bob, bob.inv, bob.tradeOffer, bob.slotOf(COINS), 50)
        bob.fill()
        assertFalse(TradeExchange.complete(alice, bob))
        assertEquals(2, alice.tradeOffer.count(LOBSTER))
        assertEquals(50, bob.tradeOffer.count(COINS))
        assertEquals(0, bob.inv.count(LOBSTER))
        assertEquals(0, alice.inv.count(COINS))
    }

    @Test
    fun `a stack that would overflow blocks the whole trade`() {
        val (alice, bob) = trader(1) to trader(2)
        alice.give(COINS, 100)
        bob.give(COINS, Int.MAX_VALUE - 10)
        bob.give(LOBSTER)
        TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(COINS), 100)
        TradeExchange.move(bob, bob.inv, bob.tradeOffer, bob.slotOf(LOBSTER), 1)
        assertFalse(TradeExchange.complete(alice, bob))
        assertEquals(100, alice.tradeOffer.count(COINS))
        assertEquals(Int.MAX_VALUE - 10, bob.inv.count(COINS))
        assertEquals(1, bob.tradeOffer.count(LOBSTER))
    }

    @Test
    fun `a dry run changes nothing`() {
        val (alice, bob) = trader(1) to trader(2)
        alice.give(COINS, 100)
        TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(COINS), 100)
        assertTrue(TradeExchange.complete(alice, bob, commit = false))
        assertEquals(100, alice.tradeOffer.count(COINS))
        assertEquals(0, bob.inv.count(COINS))
    }

    @Test
    fun `returning an offer to a full inventory sends it to the bank`() {
        val alice = trader(1)
        repeat(3) { alice.give(LOBSTER) }
        TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(LOBSTER), 3)
        alice.fill()
        TradeExchange.returnOffer(alice)
        assertTrue(alice.tradeOffer.isEmpty())
        assertEquals(3, alice.invMap.getOrPut("inv.bank").count(LOBSTER))
    }

    @Test
    fun `returning an offer puts it back in the inventory`() {
        val alice = trader(1)
        alice.give(COINS, 70)
        TradeExchange.move(alice, alice.inv, alice.tradeOffer, alice.slotOf(COINS), 70)
        TradeExchange.returnOffer(alice)
        assertTrue(alice.tradeOffer.isEmpty())
        assertEquals(70, alice.inv.count(COINS))
    }

    @OptIn(InternalApi::class)
    private fun trader(id: Long): Player =
        Player().apply {
            uuid = id
            slotId = id.toInt()
            assignUid()
            coords = CoordGrid(3222, 3218, 0)
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
        }

    private fun Player.give(obj: String, count: Int = 1) {
        val slot = (0 until 28).first { inv[it] == null }
        inv[slot] = InvObj(obj, count)
    }

    private fun Player.fill() {
        for (slot in 0 until 28) if (inv[slot] == null) inv[slot] = InvObj(FILLER, 1)
    }

    private fun Player.slotOf(obj: String): Int =
        (0 until 28).first { inv[it]?.id == obj.asRSCM() }

    companion object {
        private const val COINS = "obj.coins"
        private const val LOBSTER = "obj.lobster"
        private const val FILLER = "obj.bronze_dagger"

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in
                listOf(
                    "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                    "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
                )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
