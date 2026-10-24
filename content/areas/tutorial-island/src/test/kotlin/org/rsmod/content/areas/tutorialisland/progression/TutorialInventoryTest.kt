package org.rsmod.content.areas.tutorialisland.progression

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
import org.rsmod.api.player.ironman.setGamemode
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
class TutorialInventoryTest {
    @Test
    fun `ultimate ironman starter cash moves out of the bank exactly once`() {
        val access = access(InvObj("obj.tut2_logs", 1))
        access.player.setGamemode(org.rsmod.api.player.ironman.PlayerGamemode.ULTIMATE_IRONMAN)
        access.bank[0] = InvObj("obj.coins", 25)
        assertTrue(access.replaceTutorialKit(listOf("obj.coins" to 25)))
        assertEquals(25, access.inv.count("obj.coins"))
        assertEquals(0, access.bank.count("obj.coins"))
        assertTrue(access.replaceTutorialKit(listOf("obj.coins" to 25)))
        assertEquals(25, access.inv.count("obj.coins"))
        assertEquals(0, access.bank.count("obj.coins"))
    }

    @Test
    fun `dough with no room preserves both ingredients and every other item`() {
        val access = access(*Array(28) { when (it) {
            0 -> InvObj("obj.pot_flour", 1)
            1 -> InvObj("obj.bucket_water", 1)
            else -> InvObj("obj.ashes", 1)
        } })
        assertFalse(access.tutorialExchange(DOUGH_INPUT, DOUGH_OUTPUT))
        assertEquals(1, access.inv.count("obj.pot_flour"))
        assertEquals(1, access.inv.count("obj.bucket_water"))
        assertEquals(26, access.inv.count("obj.ashes"))
        assertEquals(0, access.inv.count("obj.bread_dough"))
    }

    @Test
    fun `dough consumes ingredients exactly once and keeps the empty containers`() {
        val access = access(InvObj("obj.pot_flour", 1), InvObj("obj.bucket_water", 1))
        assertTrue(access.tutorialExchange(DOUGH_INPUT, DOUGH_OUTPUT))
        assertFalse(access.tutorialExchange(DOUGH_INPUT, DOUGH_OUTPUT))
        assertEquals(0, access.inv.count("obj.pot_flour"))
        assertEquals(0, access.inv.count("obj.bucket_water"))
        assertEquals(1, access.inv.count("obj.pot_empty"))
        assertEquals(1, access.inv.count("obj.bucket_empty"))
        assertEquals(1, access.inv.count("obj.bread_dough"))
    }

    @Test
    fun `a missing ingredient does not consume the other ingredient`() {
        for ((item, _) in DOUGH_INPUT) {
            val access = access(InvObj(item, 1))
            assertFalse(access.tutorialExchange(DOUGH_INPUT, DOUGH_OUTPUT))
            assertEquals(1, access.inv.count(item))
        }
    }

    @Test
    fun `overflow rolls back the source item and full destination stack`() {
        val access = access(InvObj("obj.tut2_logs", 1), InvObj("obj.coins", Int.MAX_VALUE))
        assertFalse(access.tutorialExchange(listOf("obj.tut2_logs" to 1), listOf("obj.coins" to 1)))
        assertEquals(1, access.inv.count("obj.tut2_logs"))
        assertEquals(Int.MAX_VALUE, access.inv.count("obj.coins"))
    }

    @Test
    fun `starter kit clears tutorial equipment without duplicating it and seeds bank coins once`() {
        val access = fullInventory("obj.tut2_logs")
        access.worn[3] = InvObj("obj.bronze_sword", 1)
        val kit = listOf("obj.bronze_sword" to 1, "obj.bronze_arrow" to 25)
        assertTrue(access.replaceTutorialKit(kit))
        assertEquals(1, access.inv.count("obj.bronze_sword"))
        assertEquals(25, access.inv.count("obj.bronze_arrow"))
        assertEquals(0, access.inv.count("obj.tut2_logs"))
        assertEquals(0, access.inv.count("obj.ashes"))
        assertTrue(access.worn.isEmpty())
        assertEquals(25, access.bank.count("obj.coins"))
        assertTrue(access.replaceTutorialKit(kit))
        assertEquals(1, access.inv.count("obj.bronze_sword"))
        assertEquals(25, access.bank.count("obj.coins"))
    }

    @Test
    fun `oversized starter kit rolls back inventory equipment and bank together`() {
        val access = access(InvObj("obj.tut2_logs", 1))
        access.worn[3] = InvObj("obj.bronze_sword", 1)
        assertFalse(access.replaceTutorialKit(listOf("obj.ashes" to 29)))
        assertEquals(1, access.inv.count("obj.tut2_logs"))
        assertEquals(1, access.worn.count("obj.bronze_sword"))
        assertEquals(0, access.bank.count("obj.coins"))
    }

    private val DOUGH_INPUT = listOf("obj.pot_flour" to 1, "obj.bucket_water" to 1)
    private val DOUGH_OUTPUT = listOf("obj.pot_empty" to 1, "obj.bucket_empty" to 1, "obj.bread_dough" to 1)
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
            invMap["inv.inv"] = inv
            invMap["inv.worn"] = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            worn = invMap.getValue("inv.worn")
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
