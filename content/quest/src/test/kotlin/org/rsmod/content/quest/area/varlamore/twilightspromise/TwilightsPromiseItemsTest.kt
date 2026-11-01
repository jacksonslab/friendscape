package org.rsmod.content.quest.area.varlamore.twilightspromise

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
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
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.quest.area.varlamore.twilightspromise.TpItems.giveFeed
import org.rsmod.content.quest.area.varlamore.twilightspromise.TpItems.giveOnce
import org.rsmod.content.quest.area.varlamore.twilightspromise.TpItems.takeOne
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TwilightsPromiseItemsTest {
    @Test
    fun `a quest item is handed out once`() {
        val f = Fixture()
        assertEquals(GiveResult.Given, f.run { giveOnce(TpObj.CREST) })
        assertEquals(GiveResult.AlreadyHas, f.run { giveOnce(TpObj.CREST) })
        assertEquals(1, f.count(TpObj.CREST))
    }

    @Test
    fun `a quest item is not handed out into a full inventory`() {
        val f = Fixture()
        f.fill(28)
        assertEquals(GiveResult.NoSpace, f.run { giveOnce(TpObj.AMULET) })
        assertEquals(0, f.count(TpObj.AMULET))
        assertEquals(28, f.count(FILLER))
    }

    @Test
    fun `a quest item banked away is not handed out again`() {
        val f = Fixture()
        f.player.invMap.getOrPut("inv.bank")[0] = InvObj(TpObj.CREST, 1)
        assertEquals(GiveResult.AlreadyHas, f.run { giveOnce(TpObj.CREST) })
        assertEquals(0, f.count(TpObj.CREST))
    }

    @Test
    fun `handing over an item takes exactly one`() {
        val f = Fixture()
        f.give(TpObj.FEED, 3)
        assertTrue(f.run { takeOne(TpObj.FEED) })
        assertEquals(2, f.count(TpObj.FEED))
    }

    @Test
    fun `handing over an item that is gone takes nothing`() {
        val f = Fixture()
        f.give(FILLER, 1)
        assertFalse(f.run { takeOne(TpObj.AMULET) })
        assertEquals(1, f.count(FILLER))
    }

    @Test
    fun `feed comes in batches of ten`() {
        val f = Fixture()
        assertEquals(TpItems.FEED_BATCH, f.run { giveFeed() })
        assertEquals(TpItems.FEED_BATCH, f.count(TpObj.FEED))
    }

    @Test
    fun `feed is limited to the free space`() {
        val f = Fixture()
        f.fill(24)
        assertEquals(4, f.run { giveFeed() })
        assertEquals(4, f.count(TpObj.FEED))
        assertEquals(0, f.player.inv.freeSpace())
    }

    @Test
    fun `no feed is given into a full inventory`() {
        val f = Fixture()
        f.fill(28)
        assertEquals(0, f.run { giveFeed() })
        assertEquals(0, f.count(TpObj.FEED))
    }

    private class Fixture {
        private val events = EventBus()
        private val coroutine = GameCoroutine("twilights-promise-test")
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(getEventBus = { events }, getAlignment = { TextAlignment() })

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = SilentClient()
                uuid = 2410L
                slotId = 1
                assignUid()
                coords = CoordGrid(1700, 3143, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
                invMap["inv.inv"] = inv
            }

        fun give(obj: String, count: Int) {
            repeat(count) {
                val slot = (0 until 28).first { player.inv[it] == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun fill(slots: Int) = give(FILLER, slots)

        fun count(obj: String): Int = player.inv.count(obj)

        fun <T> run(block: ProtectedAccess.() -> T): T {
            var value: Result<T>? = null
            val body: suspend () -> T = { ProtectedAccess(player, coroutine, context).block() }
            body.startCoroutine(
                object : Continuation<T> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<T>) {
                        value = result
                    }
                }
            )
            return checkNotNull(value).getOrThrow()
        }
    }

    private class SilentClient : Client<Any, Any> {
        override fun write(message: Any) {}

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val FILLER = "obj.bucket_empty"

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
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
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
