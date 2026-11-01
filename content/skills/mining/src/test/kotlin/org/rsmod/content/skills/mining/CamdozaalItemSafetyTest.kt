package org.rsmod.content.skills.mining

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
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
import org.rsmod.content.skills.mining.scripts.CamdozaalForge
import org.rsmod.content.skills.mining.scripts.forge
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
class CamdozaalItemSafetyTest {
    @Test
    fun `crushing swaps every deposit for exactly one reward`() {
        val f = Fixture()
        f.give(HAMMER)
        repeat(5) { f.give(DEPOSIT) }
        f.crush()
        assertEquals(0, f.count(DEPOSIT))
        assertEquals(1, f.count(HAMMER))
        assertEquals(5, f.crushes())
        val rares = RARES.sumOf(f::count)
        assertTrue(f.count(SHARDS) >= (5 - rares) * 6, "shards ${f.count(SHARDS)}, rares $rares")
    }

    @Test
    fun `crushing without a hammer takes nothing`() {
        val f = Fixture()
        repeat(2) { f.give(DEPOSIT) }
        f.crush()
        assertEquals(2, f.count(DEPOSIT))
        assertEquals(0, f.crushes())
    }

    @Test
    fun `crushing below 14 smithing takes nothing`() {
        val f = Fixture(smithing = 13)
        f.give(HAMMER)
        f.give(DEPOSIT)
        f.crush()
        assertEquals(1, f.count(DEPOSIT))
        assertEquals(0, f.crushes())
    }

    @Test
    fun `a deposit that vanishes mid-batch is never crushed`() {
        val f = Fixture()
        f.give(HAMMER)
        repeat(3) { f.give(DEPOSIT) }
        f.crush { f.player.inv[f.slotOf(DEPOSIT)] = null }
        assertEquals(0, f.count(DEPOSIT))
        assertEquals(2, f.crushes())
    }

    @Test
    fun `losing the hammer mid-batch stops crushing`() {
        val f = Fixture()
        f.give(HAMMER)
        repeat(3) { f.give(DEPOSIT) }
        f.crush { f.player.inv[f.slotOf(HAMMER)] = null }
        assertEquals(2, f.count(DEPOSIT))
        assertEquals(1, f.crushes())
    }

    @Test
    fun `crushing with a full inventory still works`() {
        val f = Fixture()
        f.give(HAMMER)
        f.give(DEPOSIT)
        f.fill()
        f.crush()
        assertEquals(0, f.count(DEPOSIT))
        assertEquals(1, f.crushes())
        assertEquals(28, f.player.inv.occupiedSpace())
    }

    @Test
    fun `forging a mace takes the three parts and exactly 1500 shards`() {
        val f = Fixture()
        CamdozaalForge.MACE_PARTS.forEach { f.give(it) }
        f.give(SHARDS, 1600)
        assertTrue(f.player.forge(CamdozaalForge.MACE_PARTS, CamdozaalForge.MACE_SHARDS, MACE))
        CamdozaalForge.MACE_PARTS.forEach { assertEquals(0, f.count(it), it) }
        assertEquals(100, f.count(SHARDS))
        assertEquals(1, f.count(MACE))
    }

    @Test
    fun `forging with too few shards takes nothing`() {
        val f = Fixture()
        CamdozaalForge.MACE_PARTS.forEach { f.give(it) }
        f.give(SHARDS, 1499)
        assertFalse(f.player.forge(CamdozaalForge.MACE_PARTS, CamdozaalForge.MACE_SHARDS, MACE))
        CamdozaalForge.MACE_PARTS.forEach { assertEquals(1, f.count(it), it) }
        assertEquals(1499, f.count(SHARDS))
        assertEquals(0, f.count(MACE))
    }

    @Test
    fun `forging with a missing part takes nothing`() {
        val f = Fixture()
        CamdozaalForge.MACE_PARTS.drop(1).forEach { f.give(it) }
        f.give(SHARDS, 2000)
        assertFalse(f.player.forge(CamdozaalForge.MACE_PARTS, CamdozaalForge.MACE_SHARDS, MACE))
        assertEquals(2000, f.count(SHARDS))
        assertEquals(0, f.count(MACE))
    }

    @Test
    fun `repairing twice with one broken hammer repairs it once`() {
        val f = Fixture()
        f.give(BROKEN_HAMMER)
        f.give(SHARDS, 5000)
        val parts = listOf(BROKEN_HAMMER)
        assertTrue(f.player.forge(parts, CamdozaalForge.HAMMER_SHARDS, IMCANDO_HAMMER))
        assertFalse(f.player.forge(parts, CamdozaalForge.HAMMER_SHARDS, IMCANDO_HAMMER))
        assertEquals(0, f.count(BROKEN_HAMMER))
        assertEquals(1, f.count(IMCANDO_HAMMER))
        assertEquals(5000 - CamdozaalForge.HAMMER_SHARDS, f.count(SHARDS))
    }

    private class Fixture(smithing: Int = 99) {
        private val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("camdozaal-test")
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
                this.client = this@Fixture.client
                uuid = 913L
                slotId = 1
                assignUid()
                coords = CoordGrid(2958, 5770, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
                statMap.setBaseLevel("stat.smithing", smithing.toByte())
                statMap.setCurrentLevel("stat.smithing", smithing.toByte())
            }

        private val startXp = player.statMap.getFineXP("stat.smithing")

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(CamdozaalForge()) { scripts.startup() }
        }

        fun give(obj: String, count: Int = 1) {
            val slot = (0 until 28).first { player.inv[it] == null }
            player.inv[slot] = InvObj(obj, count)
        }

        fun fill() {
            for (slot in 0 until 28) if (player.inv[slot] == null) player.inv[slot] = InvObj(FILLER, 1)
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun slotOf(obj: String): Int =
            (0 until 28).firstOrNull { player.inv[it]?.id == obj.asRSCM(RSCMType.OBJ) } ?: 0

        fun crushes(): Int = (player.statMap.getFineXP("stat.smithing") - startXp) / CRUSH_FINE_XP

        fun crush(afterFirst: () -> Unit = {}) {
            val type = checkNotNull(ServerCacheManager.getObject(CamdozaalForge.CRUSHER.asRSCM(RSCMType.LOC)))
            val bound = BoundLocInfo(LocInfo(10, player.coords.translateZ(1), LocEntity(type.id, 10, 0)), type)
            start { assertTrue(events.publish(this, LocEvents.Op1(bound, bound, type))) }
            var hooked = false
            repeat(500) {
                if (coroutine.isIdle) return
                if (!hooked && crushes() == 1) {
                    afterFirst()
                    hooked = true
                }
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
                result?.getOrThrow()
            }
            fail<Unit>("Crushing did not finish")
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

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) {}

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val HAMMER = "obj.hammer"
        private const val DEPOSIT = CamdozaalForge.DEPOSIT
        private const val SHARDS = CamdozaalForge.SHARDS
        private const val MACE = "obj.barronite_mace"
        private const val BROKEN_HAMMER = "obj.imcando_hammer_broken"
        private const val IMCANDO_HAMMER = "obj.imcando_hammer"
        private const val FILLER = "obj.bronze_dagger"
        private const val CRUSH_FINE_XP = 300
        private val RARES =
            listOf(
                "obj.barronite_mace_1",
                BROKEN_HAMMER,
                "obj.camdozaal_relic_1",
                "obj.camdozaal_relic_2",
                "obj.camdozaal_relic_3",
                "obj.camdozaal_relic_4",
                "obj.camdozaal_relic_5",
            )

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
