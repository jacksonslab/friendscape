package org.rsmod.content.quest.area.varrock.demonslayer

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
class DemonSlayerInventoryTest {
    @Test
    fun `three keys are consumed for exactly one sword`() {
        val access = access(*DemonSlayerQuest.KEYS.map { InvObj(it, 1) }.toTypedArray())
        assertTrue(DemonSlayerQuest.openCase(access))
        DemonSlayerQuest.KEYS.forEach { assertEquals(0, access.inv.count(it)) }
        assertEquals(1, access.inv.count(DemonSlayerQuest.SILVERLIGHT))
        assertFalse(DemonSlayerQuest.openCase(access))
        assertEquals(1, access.inv.count(DemonSlayerQuest.SILVERLIGHT))
    }

    @Test
    fun `keys release space for the sword in a full inventory`() {
        val items = DemonSlayerQuest.KEYS.map { InvObj(it, 1) } + List(25) { InvObj("obj.ashes", 1) }
        val access = access(*items.toTypedArray())
        assertTrue(DemonSlayerQuest.openCase(access))
        assertEquals(25, access.inv.count("obj.ashes"))
        assertEquals(1, access.inv.count(DemonSlayerQuest.SILVERLIGHT))
    }

    @Test
    fun `each missing key rolls back the whole exchange`() {
        for (missing in DemonSlayerQuest.KEYS) {
            val access = access(*DemonSlayerQuest.KEYS.filter { it != missing }.map { InvObj(it, 1) }.toTypedArray())
            assertFalse(DemonSlayerQuest.openCase(access))
            DemonSlayerQuest.KEYS.forEach { assertEquals(if (it == missing) 0 else 1, access.inv.count(it)) }
            assertEquals(0, access.inv.count(DemonSlayerQuest.SILVERLIGHT))
        }
    }

    @Test
    fun `pouring water swaps one bucket even when full and cannot repeat`() {
        val access = fullInventory("obj.bucket_water")
        assertTrue(DemonSlayerQuest.emptyBucket(access))
        assertEquals(0, access.inv.count("obj.bucket_water"))
        assertEquals(1, access.inv.count("obj.bucket_empty"))
        assertEquals(27, access.inv.count("obj.ashes"))
        assertFalse(DemonSlayerQuest.emptyBucket(access))
        assertEquals(1, access.inv.count("obj.bucket_empty"))
    }
    private fun fullInventory(first: String): ProtectedAccess =
        access(*Array(28) { if (it == 0) InvObj(first, 1) else InvObj("obj.ashes", 1) })

    private fun access(vararg objs: InvObj?): ProtectedAccess {
        val player = player()
        objs.forEachIndexed { slot, obj -> player.inv[slot] = obj }
        return ProtectedAccess(player, GameCoroutine("demon-slayer-test"), CONTEXT)
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
            ServerCacheManager.init(241).close()
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
