package org.rsmod.content.areas.city.varrock

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
import org.rsmod.content.areas.city.varrock.npcs.NEWSPAPER
import org.rsmod.content.areas.city.varrock.npcs.NEWSPAPER_PRICE
import org.rsmod.content.areas.city.varrock.npcs.buyNewspaper
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
class VarrockNewspaperTest {
    @Test
    fun `buying a paper takes fifty coins for one newspaper`() {
        val f = Fixture()
        f.put(COINS, 120)
        assertTrue(f.run { buyNewspaper() })
        assertEquals(120 - NEWSPAPER_PRICE, f.count(COINS))
        assertEquals(1, f.count(NEWSPAPER))
    }

    @Test
    fun `too few coins buys nothing`() {
        val f = Fixture()
        f.put(COINS, NEWSPAPER_PRICE - 1)
        assertFalse(f.run { buyNewspaper() })
        assertEquals(NEWSPAPER_PRICE - 1, f.count(COINS))
        assertEquals(0, f.count(NEWSPAPER))
    }

    @Test
    fun `a full inventory keeps its coins`() {
        val f = Fixture()
        f.put(COINS, 500)
        f.fill(27)
        assertFalse(f.run { buyNewspaper() })
        assertEquals(500, f.count(COINS))
        assertEquals(0, f.count(NEWSPAPER))
    }

    @Test
    fun `spending the whole stack frees the slot for the paper`() {
        val f = Fixture()
        f.put(COINS, NEWSPAPER_PRICE)
        f.fill(27)
        assertTrue(f.run { buyNewspaper() })
        assertEquals(0, f.count(COINS))
        assertEquals(1, f.count(NEWSPAPER))
    }

    @Test
    fun `repeated buys take fifty each until the coins run out`() {
        val f = Fixture()
        f.put(COINS, NEWSPAPER_PRICE * 2 + 10)
        assertTrue(f.run { buyNewspaper() })
        assertTrue(f.run { buyNewspaper() })
        assertFalse(f.run { buyNewspaper() })
        assertEquals(10, f.count(COINS))
        assertEquals(2, f.count(NEWSPAPER))
    }

    private class Fixture {
        private val events = EventBus()
        private val coroutine = GameCoroutine("varrock-newspaper-test")
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(getEventBus = { events }, getAlignment = { TextAlignment() })

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = SilentClient()
                uuid = 3219L
                slotId = 1
                assignUid()
                coords = CoordGrid(3219, 3433, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
                invMap["inv.inv"] = inv
            }

        fun put(obj: String, count: Int) {
            val slot = (0 until 28).first { player.inv[it] == null }
            player.inv[slot] = InvObj(obj, count)
        }

        fun fill(slots: Int) = repeat(slots) { put(FILLER, 1) }

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
        private const val COINS = "obj.coins"
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
