package org.rsmod.content.skills.farming

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
import org.rsmod.api.player.events.interact.LocUDefaultEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
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

/**
 * Every flow that takes or gives items: planting, harvesting, the tool leprechaun and farmer
 * payment. Each test counts every obj involved before and after.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FarmingItemSafetyTest {
    @Test
    fun `planting takes exactly three seeds and keeps the tools`() {
        val f = Fixture()
        f.give(DIBBER, 1)
        f.give(POTATO_SEED, 7)
        f.rakedEmpty(FALADOR_NW)
        f.useOn(FALADOR_NW, POTATO_SEED)
        f.finish()
        assertEquals(4, f.count(POTATO_SEED))
        assertEquals(1, f.count(DIBBER))
        assertEquals(cropId("potato"), f.state(FALADOR_NW).cropId)
    }

    @Test
    fun `too few seeds takes none and plants nothing`() {
        val f = Fixture()
        f.give(DIBBER, 1)
        f.give(POTATO_SEED, 2)
        f.rakedEmpty(FALADOR_NW)
        f.useOn(FALADOR_NW, POTATO_SEED)
        f.finish()
        assertEquals(2, f.count(POTATO_SEED))
        assertTrue(f.state(FALADOR_NW).empty)
    }

    @Test
    fun `seeds that vanish mid-planting leave the patch empty`() {
        val f = Fixture()
        f.give(DIBBER, 1)
        f.give(POTATO_SEED, 3)
        f.rakedEmpty(FALADOR_NW)
        f.useOn(FALADOR_NW, POTATO_SEED)
        f.remove(POTATO_SEED)
        f.finish()
        assertEquals(0, f.count(POTATO_SEED))
        assertTrue(f.state(FALADOR_NW).empty)
    }

    @Test
    fun `planting twice in one tick only plants once`() {
        val f = Fixture()
        f.give(DIBBER, 1)
        f.give(POTATO_SEED, 9)
        f.rakedEmpty(FALADOR_NW)
        f.useOn(FALADOR_NW, POTATO_SEED)
        f.useOn(FALADOR_NW, POTATO_SEED)
        f.finish()
        f.useOn(FALADOR_NW, POTATO_SEED)
        f.finish()
        assertEquals(6, f.count(POTATO_SEED))
    }

    @Test
    fun `planting a sapling hands the plant pot back`() {
        val f = Fixture(farming = 15)
        f.give(SPADE, 1)
        f.give(OAK_SAPLING, 1)
        f.rakedEmpty(FALADOR_TREE)
        f.useOn(FALADOR_TREE, OAK_SAPLING)
        f.finish()
        assertEquals(0, f.count(OAK_SAPLING))
        assertEquals(1, f.count(PLANTPOT))
        assertEquals(cropId("oak"), f.state(FALADOR_TREE).cropId)
    }

    @Test
    fun `harvesting gives one crop per pick until the lives run out`() {
        val f = Fixture()
        f.give(SPADE, 1)
        f.grown(FALADOR_NW, "potato", lives = 3)
        f.op1(FALADOR_NW)
        f.finish()
        assertEquals(3, f.count(POTATO))
        assertTrue(f.state(FALADOR_NW).let { it.empty && it.clear })
        f.op1(FALADOR_NW)
        f.finish()
        assertEquals(3, f.count(POTATO))
    }

    @Test
    fun `harvesting into a full inventory gives nothing and keeps every life`() {
        val f = Fixture()
        f.give(SPADE, 1)
        f.fill()
        f.grown(FALADOR_NW, "potato", lives = 3)
        f.op1(FALADOR_NW)
        f.finish()
        assertEquals(0, f.count(POTATO))
        assertEquals(3, f.state(FALADOR_NW).lives)
    }

    @Test
    fun `harvesting stops at the pick that fills the inventory`() {
        val f = Fixture()
        f.give(SPADE, 1)
        f.fill(keepFree = 1)
        f.grown(FALADOR_NW, "potato", lives = 3)
        f.op1(FALADOR_NW)
        f.finish()
        assertEquals(1, f.count(POTATO))
        assertEquals(2, f.state(FALADOR_NW).lives)
    }

    @Test
    fun `crops keep growing while the player is logged out`() {
        val f = Fixture()
        f.give(DIBBER, 1)
        f.give(POTATO_SEED, 3)
        f.rakedEmpty(FALADOR_NW)
        f.useOn(FALADOR_NW, POTATO_SEED)
        f.finish()
        f.clock.millis += 60 * 60_000L
        f.patches.update(f.player)
        val state = f.state(FALADOR_NW)
        assertEquals(4, state.stage)
        assertEquals(3, state.lives)
    }

    @Test
    fun `the leprechaun stores and returns tools one for one`() {
        val f = Fixture()
        f.give(RAKE, 1)
        f.give(BUCKET, 5)
        assertTrue(ToolStoreFlows.store(f.access(), FarmingTool.Rake, 1))
        assertTrue(ToolStoreFlows.store(f.access(), FarmingTool.Bucket, Int.MAX_VALUE))
        assertEquals(0, f.count(RAKE))
        assertEquals(0, f.count(BUCKET))
        assertEquals(1, ToolStore.stored(f.player, FarmingTool.Rake))
        assertEquals(5, ToolStore.stored(f.player, FarmingTool.Bucket))

        assertTrue(ToolStoreFlows.withdraw(f.access(), FarmingTool.Bucket, 3))
        assertEquals(3, f.count(BUCKET))
        assertEquals(2, ToolStore.stored(f.player, FarmingTool.Bucket))
        assertTrue(ToolStoreFlows.withdraw(f.access(), FarmingTool.Rake, 1))
        assertEquals(1, f.count(RAKE))
        assertEquals(0, ToolStore.stored(f.player, FarmingTool.Rake))
        assertFalse(ToolStoreFlows.withdraw(f.access(), FarmingTool.Rake, 1))
        assertEquals(1, f.count(RAKE))
    }

    @Test
    fun `withdrawing into a full inventory leaves the store alone`() {
        val f = Fixture()
        ToolStore.setStored(f.player, FarmingTool.Spade, 1)
        f.fill()
        assertFalse(ToolStoreFlows.withdraw(f.access(), FarmingTool.Spade, 1))
        assertEquals(1, ToolStore.stored(f.player, FarmingTool.Spade))
        assertEquals(0, f.count(SPADE))
    }

    @Test
    fun `the store never takes more than it can hold`() {
        val f = Fixture()
        val capacity = ToolStore.capacity(FarmingTool.PlantCure)
        ToolStore.setStored(f.player, FarmingTool.PlantCure, capacity - 2)
        f.give(PLANT_CURE, 5)
        assertTrue(ToolStoreFlows.store(f.access(), FarmingTool.PlantCure, Int.MAX_VALUE))
        assertEquals(capacity, ToolStore.stored(f.player, FarmingTool.PlantCure))
        assertEquals(3, f.count(PLANT_CURE))
    }

    @Test
    fun `split counts encode the way the store interface adds them up`() {
        val f = Fixture()
        for (count in listOf(0, 1, 31, 32, 33, 255, 256, 257, 999)) {
            ToolStore.setStored(f.player, FarmingTool.Bucket, count)
            assertEquals(count, ToolStore.stored(f.player, FarmingTool.Bucket), "buckets $count")
        }
    }

    @Test
    fun `a stored watering can comes back with its charges`() {
        val f = Fixture()
        f.give("obj.watering_can_5", 1)
        assertTrue(ToolStoreFlows.store(f.access(), FarmingTool.WateringCan, 1))
        assertEquals(0, f.count("obj.watering_can_5"))
        f.give("obj.watering_can_8", 1)
        assertFalse(ToolStoreFlows.store(f.access(), FarmingTool.WateringCan, 1))
        assertEquals(1, f.count("obj.watering_can_8"))
        f.remove("obj.watering_can_8")
        assertTrue(ToolStoreFlows.withdraw(f.access(), FarmingTool.WateringCan, 1))
        assertEquals(1, f.count("obj.watering_can_5"))
        assertEquals(null, ToolStore.storedCan(f.player))
    }

    @Test
    fun `noting produce swaps every crop for one banknote`() {
        val f = Fixture()
        f.give(POTATO, 6)
        assertTrue(ToolStoreFlows.note(f.access(), POTATO))
        assertEquals(0, f.count(POTATO))
        assertEquals(6, f.count(certOf(POTATO)))
    }

    @Test
    fun `noting into a full stack of banknotes changes nothing`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(certOf(POTATO), Int.MAX_VALUE)
        f.give(POTATO, 2)
        assertFalse(ToolStoreFlows.note(f.access(), POTATO))
        assertEquals(2, f.count(POTATO))
        assertEquals(Int.MAX_VALUE, f.count(certOf(POTATO)))
    }

    @Test
    fun `items that are not produce are never noted`() {
        val f = Fixture()
        f.give(RAKE, 1)
        assertFalse(ToolStoreFlows.note(f.access(), RAKE))
        assertEquals(1, f.count(RAKE))
    }

    @Test
    fun `a farmer takes the payment once and protects the patch`() {
        val f = Fixture()
        f.growing(FALADOR_NW, "potato")
        f.give(COMPOST, 3)
        val patch = f.patch(FALADOR_NW)
        assertTrue(ToolStoreFlows.takePayment(f.access(), f.patches, patch, COMPOST, 2))
        assertEquals(1, f.count(COMPOST))
        assertTrue(f.state(FALADOR_NW).protected)
        assertFalse(ToolStoreFlows.takePayment(f.access(), f.patches, patch, COMPOST, 1))
        assertEquals(1, f.count(COMPOST))
    }

    @Test
    fun `a farmer accepts noted payment and takes nothing when it falls short`() {
        val f = Fixture()
        f.growing(FALADOR_NW, "onion")
        val sack = "obj.sack_potato_10"
        f.player.inv[0] = InvObj(certOf(sack), 1)
        val patch = f.patch(FALADOR_NW)
        assertFalse(ToolStoreFlows.takePayment(f.access(), f.patches, patch, sack, 2))
        assertEquals(1, f.count(certOf(sack)))
        assertFalse(f.state(FALADOR_NW).protected)
        assertTrue(ToolStoreFlows.takePayment(f.access(), f.patches, patch, sack, 1))
        assertEquals(0, f.count(certOf(sack)))
        assertTrue(f.state(FALADOR_NW).protected)
    }

    @Test
    fun `every patch knows its transmit varbit, farm and farmer`() {
        for (patch in FarmingData.patches) {
            assertTrue(patch.transmit != null, patch.loc)
            assertTrue(patch.area > 0, patch.loc)
        }
        val elstan = FarmingData.patches.filter { it.farmer == "npc.elstan" }.map { it.label }
        assertEquals(listOf("north-west", "south-east"), elstan)
        assertEquals("Falador", FarmingData.areaAt(CoordGrid(3055, 3307, 0))?.name)
        assertEquals("Catherby", FarmingData.areaAt(CoordGrid(2830, 3445, 0))?.name)
        assertEquals(FarmingData.areas.first { it.regions == setOf(11317) }, FarmingData.areaAt(CoordGrid(2860, 3433, 0)))
    }

    private fun cropId(name: String): Int = FarmingData.crops.first { it.name == name }.id

    private fun certOf(obj: String): String {
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))!!
        return ServerCacheManager.getItem(type.certlink)!!.internalName
    }

    private class TestClock(var millis: Long) : FarmingClock() {
        override fun millis(): Long = millis
    }

    /** Never saves a harvest life and never rolls disease. */
    private object WorstLuck : GameRandom {
        override fun of(maxExclusive: Int): Int = maxExclusive - 1

        override fun of(minInclusive: Int, maxInclusive: Int): Int = maxInclusive

        override fun randomDouble(): Double = 0.999
    }

    private class Fixture(farming: Int = 1) {
        private val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("farming-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(getEventBus = { events }, getAlignment = { TextAlignment() }, getRandom = { WorstLuck })

        val clock = TestClock(START)
        val patches = FarmingPatches(clock, WorstLuck)

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 911L
                slotId = 1
                assignUid()
                coords = CoordGrid(3055, 3307, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                statMap.setBaseLevel("stat.farming", farming.toByte())
                statMap.setCurrentLevel("stat.farming", farming.toByte())
            }

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(FarmingScript(patches, XpModifiers(emptySet()), InvisibleLevels(emptySet()))) { scripts.startup() }
            patches.update(player)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun patch(loc: String): Patch = FarmingData.patches.first { it.loc == loc }

        fun state(loc: String): PatchState = patches.state(player, patch(loc))

        fun rakedEmpty(loc: String) = VarPlayerIntMapSetter.set(player, patch(loc).varp, PatchState.EMPTY_CLEAR.pack())

        fun growing(loc: String, crop: String) {
            val id = FarmingData.crops.first { it.name == crop }.id
            VarPlayerIntMapSetter.set(player, patch(loc).varp, PatchState(cropId = id, raked = 3, stage = 1).pack())
        }

        fun grown(loc: String, crop: String, lives: Int) {
            val type = FarmingData.crops.first { it.name == crop }
            val state = PatchState(cropId = type.id, raked = 3, stage = type.grownStage, lives = lives)
            VarPlayerIntMapSetter.set(player, patch(loc).varp, state.pack())
        }

        fun give(obj: String, count: Int) {
            val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))!!
            if (type.isStackable) {
                val slot = (0 until 28).first { player.inv[it] == null }
                player.inv[slot] = InvObj(obj, count)
                return
            }
            repeat(count) {
                val slot = (0 until 28).first { player.inv[it] == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun remove(obj: String) {
            val id = obj.asRSCM(RSCMType.OBJ)
            for (slot in 0 until 28) if (player.inv[slot]?.id == id) player.inv[slot] = null
        }

        fun fill(keepFree: Int = 0) {
            val free = (0 until 28).filter { player.inv[it] == null }
            for (slot in free.drop(keepFree)) player.inv[slot] = InvObj(FILLER, 1)
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun op1(loc: String) {
            val bound = bound(loc)
            start { assertTrue(events.publish(this, LocEvents.Op1(bound, bound, bound.type()))) }
        }

        fun useOn(loc: String, obj: String) {
            val bound = bound(loc)
            val item = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))!!
            val slot = (0 until 28).firstOrNull { player.inv[it]?.id == item.id } ?: 0
            start { assertTrue(events.publish(this, LocUDefaultEvents.OpType(bound, bound, bound.type(), item, slot))) }
        }

        fun finish() {
            repeat(300) {
                if (coroutine.isIdle) return
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            fail<Unit>("Interaction did not finish: ${client.messages.joinToString("\n")}")
        }

        private fun bound(loc: String): BoundLocInfo {
            val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!
            return BoundLocInfo(LocInfo(10, player.coords.translateZ(1), LocEntity(type.id, 10, 0)), type)
        }

        private fun BoundLocInfo.type() = ServerCacheManager.getObject(id)!!

        private fun start(block: suspend ProtectedAccess.() -> Unit) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { access().block() }
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
        val messages = mutableListOf<Any>()

        override fun write(message: Any) {
            messages += message
        }

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val START = 1_790_000_000_000L
        private const val FALADOR_NW = "loc.farming_veg_patch_1"
        private const val FALADOR_TREE = "loc.farming_tree_patch_2"
        private const val DIBBER = "obj.dibber"
        private const val SPADE = "obj.spade"
        private const val RAKE = "obj.rake"
        private const val BUCKET = "obj.bucket_empty"
        private const val COMPOST = "obj.bucket_compost"
        private const val PLANT_CURE = "obj.plant_cure"
        private const val PLANTPOT = "obj.plantpot_empty"
        private const val POTATO_SEED = "obj.potato_seed"
        private const val POTATO = "obj.potato"
        private const val OAK_SAPLING = "obj.plantpot_oak_sapling"
        private const val FILLER = "obj.bronze_dagger"

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
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
