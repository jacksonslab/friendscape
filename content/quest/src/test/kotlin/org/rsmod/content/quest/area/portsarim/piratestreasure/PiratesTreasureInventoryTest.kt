package org.rsmod.content.quest.area.portsarim.piratestreasure

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
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureQuest.Companion.CASKET
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureQuest.Companion.CASKET_COINS
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureQuest.Companion.KEY
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureQuest.Companion.RUM
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PiratesTreasureInventoryTest {
    @Test
    fun `Frank takes exactly one rum for the key`() {
        val access = access(InvObj(RUM, 1), InvObj(RUM, 1))
        assertTrue(PiratesTreasureQuest.swapRumForKey(access))
        assertEquals(1, access.inv.count(RUM))
        assertEquals(1, access.inv.count(KEY))
    }

    @Test
    fun `the rum swap works in a full inventory`() {
        val access = fullInventory(RUM)
        assertTrue(PiratesTreasureQuest.swapRumForKey(access))
        assertEquals(0, access.inv.count(RUM))
        assertEquals(1, access.inv.count(KEY))
        assertEquals(27, access.inv.count("obj.ashes"))
    }

    @Test
    fun `no key is handed over without rum`() {
        val access = access()
        assertFalse(PiratesTreasureQuest.swapRumForKey(access))
        assertEquals(0, access.inv.count(KEY))
    }

    @Test
    fun `opening the casket swaps it for the whole treasure`() {
        val access = access(InvObj(CASKET, 1))
        assertTrue(PiratesTreasureQuest.openCasket(access))
        assertEquals(0, access.inv.count(CASKET))
        assertEquals(CASKET_COINS, access.inv.count("obj.coins"))
        assertEquals(1, access.inv.count("obj.gold_ring"))
        assertEquals(1, access.inv.count("obj.emerald"))
    }

    @Test
    fun `a casket without room for the treasure stays shut and whole`() {
        val access = fullInventory(CASKET)
        assertFalse(PiratesTreasureQuest.openCasket(access))
        assertEquals(1, access.inv.count(CASKET))
        assertEquals(0, access.inv.count("obj.coins"))
        assertEquals(0, access.inv.count("obj.gold_ring"))
        assertEquals(0, access.inv.count("obj.emerald"))
    }

    @Test
    fun `a coin stack that would overflow keeps the casket shut`() {
        val coins = Int.MAX_VALUE - 1
        val access = access(InvObj(CASKET, 1), InvObj("obj.coins", coins))
        assertFalse(PiratesTreasureQuest.openCasket(access))
        assertEquals(1, access.inv.count(CASKET))
        assertEquals(coins, access.inv.count("obj.coins"))
        assertEquals(0, access.inv.count("obj.gold_ring"))
    }

    @Test
    fun `opening a second time after the casket is gone gives nothing`() {
        val access = access(InvObj(CASKET, 1))
        assertTrue(PiratesTreasureQuest.openCasket(access))
        assertFalse(PiratesTreasureQuest.openCasket(access))
        assertEquals(CASKET_COINS, access.inv.count("obj.coins"))
        assertEquals(1, access.inv.count("obj.gold_ring"))
    }

    @Test
    fun `shipping the crate sends hidden rum to Wydin and empties it`() {
        val player = player()
        player.crateBananas = BananaPlantation.CRATE_CAPACITY
        player.crateRum = true
        player.luthasJob = true
        BananaPlantation.shipCrate(player)
        assertTrue(player.rumShipped)
        assertFalse(player.crateRum)
        assertFalse(player.luthasJob)
        assertEquals(0, player.crateBananas)
    }

    @Test
    fun `shipping a crate without rum ships nothing`() {
        val player = player()
        player.crateBananas = BananaPlantation.CRATE_CAPACITY
        BananaPlantation.shipCrate(player)
        assertFalse(player.rumShipped)
    }

    private fun fullInventory(first: String): ProtectedAccess =
        access(*Array(28) { if (it == 0) InvObj(first, 1) else InvObj("obj.ashes", 1) })

    private fun access(vararg objs: InvObj?): ProtectedAccess {
        val player = player()
        objs.forEachIndexed { slot, obj -> player.inv[slot] = obj }
        return ProtectedAccess(player, GameCoroutine("pirates-treasure-test"), CONTEXT)
    }

    @OptIn(InternalApi::class)
    private fun player(): Player =
        Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
        }

    companion object {
        private val CONTEXT = ProtectedAccessContextFactory.empty()
        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in listOf(
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
