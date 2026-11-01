package org.rsmod.content.skills.cooking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.cooking.CookingCombinationsRow
import org.rsmod.content.skills.validButtons
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
class CookingItemSafetyTest {
    @Test
    fun `every combination swaps exactly its inputs for its outputs`() {
        for (row in CookingCombinationsRow.all()) {
            val f = Fixture()
            val first = row.input[0].internalName
            val second = row.input[1].internalName
            f.give(first)
            f.give(second)
            if (row.needsKnife == true) f.give(KNIFE)
            val before = f.counts(row)
            f.use(first, second)
            val after = f.counts(row)
            val expected = before.toMutableMap()
            for (item in row.input) expected.merge(item.internalName, -1, Int::plus)
            for (item in row.output) expected.merge(item.internalName, 1, Int::plus)
            assertEquals(expected, after, "dbrow ${row.rowId}: $first + $second")
            if (row.needsKnife == true) assertEquals(1, f.count(KNIFE), "knife kept: ${row.rowId}")
        }
    }

    @Test
    fun `too low a level takes nothing`() {
        val f = Fixture(cooking = 50)
        f.give(POTATO_BUTTER)
        f.give(MUSHROOM_ONION)
        f.use(POTATO_BUTTER, MUSHROOM_ONION)
        assertEquals(1, f.count(POTATO_BUTTER))
        assertEquals(1, f.count(MUSHROOM_ONION))
        assertEquals(0, f.count(MUSHROOM_POTATO))
    }

    @Test
    fun `chopping without a knife takes nothing`() {
        val f = Fixture()
        f.give(BOWL)
        f.give(ONION)
        f.use(BOWL, ONION)
        assertEquals(1, f.count(BOWL))
        assertEquals(1, f.count(ONION))
        assertEquals(0, f.count(CHOPPED_ONION))
    }

    @Test
    fun `an ingredient that vanishes before the take leaves the other untouched`() {
        val f = Fixture()
        f.give(POTATO_BAKED)
        f.give(BUTTER)
        val slot = f.slotOf(BUTTER)
        f.player.inv[slot] = null
        f.use(POTATO_BAKED, BUTTER, secondSlot = slot)
        assertEquals(1, f.count(POTATO_BAKED))
        assertEquals(0, f.count(POTATO_WITH_BUTTER))
        assertEquals(0, f.count(POT))
    }

    @Test
    fun `repeated use with one set of ingredients makes one result`() {
        val f = Fixture()
        f.give(POTATO_BAKED)
        f.give(BUTTER)
        repeat(3) { f.use(POTATO_BAKED, BUTTER) }
        assertEquals(0, f.count(POTATO_BAKED))
        assertEquals(0, f.count(BUTTER))
        assertEquals(1, f.count(POTATO_WITH_BUTTER))
        assertEquals(1, f.count(POT))
    }

    @Test
    fun `a full inventory still swaps two for two`() {
        val f = Fixture()
        f.give(POTATO_BAKED)
        f.give(BUTTER)
        f.fill()
        f.use(POTATO_BAKED, BUTTER)
        assertEquals(1, f.count(POTATO_WITH_BUTTER))
        assertEquals(1, f.count(POT))
        assertEquals(28, f.player.inv.occupiedSpace())
    }

    @Test
    fun `churning turns each bucket of milk into one cheese and one bucket`() {
        val f = Fixture()
        repeat(5) { f.give(MILK) }
        f.churn(product = CHEESE, amount = 5)
        assertEquals(0, f.count(MILK))
        assertEquals(5, f.count(CHEESE))
        assertEquals(5, f.count(BUCKET))
    }

    @Test
    fun `churning stops when the milk runs out mid-batch`() {
        val f = Fixture()
        repeat(2) { f.give(MILK) }
        f.churn(product = CHEESE, amount = 5) { f.player.inv[f.slotOf(MILK)] = null }
        assertEquals(0, f.count(MILK))
        assertEquals(1, f.count(CHEESE))
        assertEquals(1, f.count(BUCKET))
    }

    @Test
    fun `roasting a skewer hands the spit back`() {
        val f = Fixture()
        f.give(SKEWERED_BIRD)
        f.cookOnFire()
        assertEquals(0, f.count(SKEWERED_BIRD))
        assertEquals(1, f.count(ROAST_BIRD) + f.count(BURNT_BIRD))
        assertEquals(1, f.count(SPIT))
    }

    @Test
    fun `roasting a skewer with no room for the spit keeps the skewer`() {
        val f = Fixture()
        f.give(SKEWERED_BIRD)
        f.fill()
        f.cookOnFire()
        assertEquals(1, f.count(SKEWERED_BIRD))
        assertEquals(0, f.count(ROAST_BIRD) + f.count(BURNT_BIRD))
        assertEquals(0, f.count(SPIT))
    }

    private class Fixture(cooking: Int = 99) {
        private val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("cooking-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(getEventBus = { events }, getAlignment = { TextAlignment() })

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 912L
                slotId = 1
                assignUid()
                coords = CoordGrid(3208, 3214, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
                statMap.setBaseLevel("stat.cooking", cooking.toByte())
                statMap.setCurrentLevel("stat.cooking", cooking.toByte())
            }

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(CookingCombinationEvents()) { scripts.startup() }
            with(DairyChurnEvents()) { scripts.startup() }
            with(CookingEvents(XpModifiers(emptySet()))) { scripts.startup() }
        }

        fun give(obj: String) {
            val slot = (0 until 28).first { player.inv[it] == null }
            player.inv[slot] = InvObj(obj, 1)
        }

        fun fill() {
            for (slot in 0 until 28) if (player.inv[slot] == null) player.inv[slot] = InvObj(FILLER, 1)
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun slotOf(obj: String): Int =
            (0 until 28).firstOrNull { player.inv[it]?.id == obj.asRSCM(RSCMType.OBJ) } ?: 0

        fun counts(row: CookingCombinationsRow): Map<String, Int> =
            (row.input + row.output).map { it.internalName }.distinct().associateWith(::count)

        fun use(first: String, second: String, secondSlot: Int = slotOf(second)) {
            val firstType = checkNotNull(ServerCacheManager.getItem(first.asRSCM(RSCMType.OBJ)))
            val secondType = checkNotNull(ServerCacheManager.getItem(second.asRSCM(RSCMType.OBJ)))
            start { assertTrue(events.publish(this, HeldUEvents.Type(firstType, slotOf(first), secondType, secondSlot))) }
            finish()
        }

        fun cookOnFire() {
            val type = checkNotNull(ServerCacheManager.getObject("loc.fire".asRSCM(RSCMType.LOC)))
            val bound = BoundLocInfo(LocInfo(10, player.coords.translateZ(1), LocEntity(type.id, 10, 0)), type)
            start { assertTrue(events.publish(this, LocEvents.Op1(bound, bound, type))) }
            finish()
        }

        fun churn(product: String, amount: Int, afterFirst: () -> Unit = {}) {
            val type = checkNotNull(ServerCacheManager.getObject("loc.dairy_churn".asRSCM(RSCMType.LOC)))
            val bound = BoundLocInfo(LocInfo(10, player.coords.translateZ(1), LocEntity(type.id, 10, 0)), type)
            start { assertTrue(events.publish(this, LocEvents.Op1(bound, bound, type))) }
            val button = validButtons[listOf(CREAM, BUTTER, CHEESE).indexOf(product)]
            coroutine.resumeWith(ResumePauseButtonInput(button, amount))
            result?.getOrThrow()
            var hooked = false
            repeat(500) {
                if (coroutine.isIdle) return
                if (!hooked && count(product) == 1) {
                    afterFirst()
                    hooked = true
                }
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
                result?.getOrThrow()
            }
            fail<Unit>("Churning did not finish")
        }

        private fun finish() {
            repeat(50) {
                if (coroutine.isIdle) return
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            fail<Unit>("Interaction did not finish")
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
        private const val KNIFE = "obj.knife"
        private const val BOWL = "obj.bowl_empty"
        private const val ONION = "obj.onion"
        private const val CHOPPED_ONION = "obj.bowl_onion"
        private const val POTATO_BAKED = "obj.potato_baked"
        private const val POTATO_BUTTER = "obj.potato_butter"
        private const val POTATO_WITH_BUTTER = "obj.potato_butter"
        private const val MUSHROOM_ONION = "obj.bowl_mushroom+onion"
        private const val MUSHROOM_POTATO = "obj.potato_mushroom+onion"
        private const val BUTTER = "obj.pot_of_butter"
        private const val CREAM = "obj.pot_of_cream"
        private const val CHEESE = "obj.cheese"
        private const val MILK = "obj.bucket_milk"
        private const val BUCKET = "obj.bucket_empty"
        private const val POT = "obj.pot_empty"
        private const val FILLER = "obj.bronze_dagger"
        private const val SPIT = "obj.spit_iron"
        private const val SKEWERED_BIRD = "obj.spit_skewered_bird_meat"
        private const val ROAST_BIRD = "obj.spit_roasted_bird_meat"
        private const val BURNT_BIRD = "obj.spit_burned_bird_meat"

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
