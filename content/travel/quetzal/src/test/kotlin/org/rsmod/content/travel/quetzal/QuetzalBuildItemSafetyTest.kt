package org.rsmod.content.travel.quetzal

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class QuetzalBuildItemSafetyTest {
    @Test
    fun `building a pad takes exactly 3 bricks and 4 clay and unlocks the site`() {
        val f = Fixture()
        f.give(BRICK, 5)
        f.give(CLAY, 6)
        f.build()
        assertEquals(2, f.count(BRICK))
        assertEquals(2, f.count(CLAY))
        assertNotEquals(0, f.player.vars[UNLOCKED] and (1 shl CAM_TORUM_ID))
        assertNotEquals(0, f.player.vars[CAM_TORUM_VARBIT])
    }

    @Test
    fun `building without enough clay takes nothing`() {
        val f = Fixture()
        f.give(BRICK, 3)
        f.give(CLAY, 3)
        f.build()
        assertEquals(3, f.count(BRICK))
        assertEquals(3, f.count(CLAY))
        assertEquals(0, f.player.vars[UNLOCKED])
    }

    @Test
    fun `building without enough bricks takes nothing`() {
        val f = Fixture()
        f.give(BRICK, 2)
        f.give(CLAY, 10)
        f.build()
        assertEquals(2, f.count(BRICK))
        assertEquals(10, f.count(CLAY))
        assertEquals(0, f.player.vars[UNLOCKED])
    }

    @Test
    fun `building an already built pad again takes nothing`() {
        val f = Fixture()
        f.give(BRICK, 6)
        f.give(CLAY, 8)
        f.build()
        f.build()
        assertEquals(3, f.count(BRICK))
        assertEquals(4, f.count(CLAY))
    }

    private class Fixture {
        private val events = EventBus()
        private val coroutine = GameCoroutine("quetzal-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getRandom = { DefaultGameRandom(seed = 241L) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = SilentClient()
                uuid = 914L
                slotId = 1
                assignUid()
                coords = CoordGrid(1446, 3106, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            }

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(QuetzalTransport()) { scripts.startup() }
        }

        fun give(obj: String, count: Int) {
            repeat(count) {
                val slot = (0 until 28).first { player.inv[it] == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun build() {
            val base = checkNotNull(ServerCacheManager.getObject(PAD.asRSCM(RSCMType.LOC)))
            val vis = checkNotNull(ServerCacheManager.getObject(UNBUILT.asRSCM(RSCMType.LOC)))
            val coords = CoordGrid(1447, 3107, 0)
            val loc = BoundLocInfo(LocInfo(10, coords, LocEntity(base.id, 10, 1)), base)
            val visLoc = BoundLocInfo(LocInfo(10, coords, LocEntity(vis.id, 10, 1)), vis)
            start { events.publish(this, LocEvents.Op1(loc, visLoc, vis)) }
            repeat(20) {
                if (coroutine.isIdle) return
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
                result?.getOrThrow()
            }
        }

        private fun start(block: suspend ProtectedAccess.() -> Unit) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { ProtectedAccess(player, coroutine, context).block() }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            result?.getOrThrow()
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
        private const val BRICK = "obj.limestonebrick"
        private const val CLAY = "obj.softclay"
        private const val PAD = "loc.quetzal_landing_site_camtorum"
        private const val UNBUILT = "loc.quetzal_landing_site_unbuilt"
        private const val UNLOCKED = "varp.quetzals_unlocked"
        private const val CAM_TORUM_VARBIT = "varbit.quetzal_camtorum"
        private const val CAM_TORUM_ID = 5

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
