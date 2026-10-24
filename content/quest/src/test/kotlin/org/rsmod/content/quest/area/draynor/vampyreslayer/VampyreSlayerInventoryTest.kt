package org.rsmod.content.quest.area.draynor.vampyreslayer

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
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.BEER
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.STAKE
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
class VampyreSlayerInventoryTest {
    @Test
    fun `Harlow takes one beer for the stake`() {
        val access = access(InvObj(BEER, 1), InvObj(BEER, 1))
        assertTrue(VampyreSlayerQuest.swapBeerForStake(access))
        assertEquals(1, access.inv.count(BEER))
        assertEquals(1, access.inv.count(STAKE))
    }

    @Test
    fun `the beer swap works in a full inventory`() {
        val access = fullInventory(BEER)
        assertTrue(VampyreSlayerQuest.swapBeerForStake(access))
        assertEquals(0, access.inv.count(BEER))
        assertEquals(1, access.inv.count(STAKE))
    }

    @Test
    fun `no stake is handed over without beer`() {
        val access = access()
        assertFalse(VampyreSlayerQuest.swapBeerForStake(access))
        assertEquals(0, access.inv.count(STAKE))
    }

    private fun fullInventory(first: String): ProtectedAccess =
        access(*Array(28) { if (it == 0) InvObj(first, 1) else InvObj("obj.ashes", 1) })

    private fun access(vararg objs: InvObj?): ProtectedAccess {
        val player = player()
        objs.forEachIndexed { slot, obj -> player.inv[slot] = obj }
        return ProtectedAccess(player, GameCoroutine("vampyre-slayer-test"), CONTEXT)
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
