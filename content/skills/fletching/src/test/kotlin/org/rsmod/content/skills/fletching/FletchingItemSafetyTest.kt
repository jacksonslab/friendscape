package org.rsmod.content.skills.fletching

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.Tuple2
import org.rsmod.api.table.slayer.SlayerUnlockRow
import org.rsmod.content.skills.Material
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
class FletchingItemSafetyTest {
    @Test
    fun `overflow rolls back all inputs and awards no xp`() {
        val f = Fixture()
        f.give(SHAFT, 30)
        f.give(FEATHER, 30)
        f.give(HEADLESS, Int.MAX_VALUE - 14)
        val before = f.snapshot()
        f.tick(assembly())
        assertEquals(before, f.snapshot())
        assertEquals(0, f.earnedXp())
    }

    @Test
    fun `full inventory cannot consume partially remaining input stacks`() {
        val f = Fixture()
        f.give(SHAFT, 30)
        f.give(FEATHER, 30)
        f.fill()
        val before = f.snapshot()
        f.tick(assembly())
        assertEquals(before, f.snapshot())
        assertEquals(0, f.earnedXp())
    }

    @Test
    fun `consuming the last input frees room in a full inventory`() {
        val f = Fixture()
        f.give(SHAFT, 15)
        f.give(FEATHER, 30)
        f.fill()
        f.tick(assembly())
        assertEquals(0, f.count(SHAFT))
        assertEquals(15, f.count(FEATHER))
        assertEquals(15, f.count(HEADLESS))
        assertEquals(26, f.count(FILLER))
        assertEquals(28, f.player.inv.occupiedSpace())
        assertEquals(150, f.earnedXp())
    }

    @Test
    fun `repeated ticks cannot produce or award xp after inputs are exhausted`() {
        val f = Fixture()
        f.give(SHAFT, 15)
        f.give(FEATHER, 30)
        f.tick(assembly())
        val after = f.snapshot()
        repeat(3) { f.tick(assembly()) }
        assertEquals(after, f.snapshot())
        assertEquals(0, f.count(SHAFT))
        assertEquals(15, f.count(FEATHER))
        assertEquals(15, f.count(HEADLESS))
        assertEquals(150, f.earnedXp())
    }

    @Test
    fun `material removed between ticks stops production without taking the other input`() {
        val f = Fixture()
        f.give(SHAFT, 30)
        f.give(FEATHER, 30)
        f.tick(assembly())
        f.remove(FEATHER)
        val before = f.snapshot()
        f.tick(assembly())
        assertEquals(before, f.snapshot())
        assertEquals(15, f.count(SHAFT))
        assertEquals(15, f.count(HEADLESS))
        assertEquals(150, f.earnedXp())
    }

    @Test
    fun `losing the held tool stops production and never consumes the tool`() {
        val f = Fixture()
        f.give(TOOL)
        f.give(SHAFT, 30)
        f.give(FEATHER, 30)
        val recipe = assembly().copy(tool = TOOL)
        f.tick(recipe)
        assertEquals(1, f.count(TOOL))
        f.remove(TOOL)
        val before = f.snapshot()
        f.tick(recipe)
        assertEquals(before, f.snapshot())
        assertEquals(15, f.count(SHAFT))
        assertEquals(15, f.count(FEATHER))
        assertEquals(15, f.count(HEADLESS))
        assertEquals(150, f.earnedXp())
    }

    @Test
    fun `non batch recipe with insufficient material leaves inventory and xp untouched`() {
        val f = Fixture()
        f.give(SHAFT, 15)
        f.give(FEATHER, 14)
        val before = f.snapshot()
        f.tick(assembly().copy(outputAmount = 1))
        assertEquals(before, f.snapshot())
        assertEquals(0, f.count(HEADLESS))
        assertEquals(0, f.earnedXp())
    }

    @Test
    fun `temporary boost meets requirement above base and awards xp`() {
        val f = Fixture(base = 49, current = 50)
        f.give(SHAFT, 15)
        f.give(FEATHER, 15)
        f.tick(requiredAssembly())
        assertEquals(0, f.count(SHAFT))
        assertEquals(0, f.count(FEATHER))
        assertEquals(15, f.count(HEADLESS))
        assertEquals(150, f.earnedXp())
    }

    @Test
    fun `drained current level blocks production despite sufficient base`() {
        val f = Fixture(base = 50, current = 49)
        f.give(SHAFT, 15)
        f.give(FEATHER, 15)
        val before = f.snapshot()
        f.tick(requiredAssembly(), expectMesbox = true)
        assertEquals(before, f.snapshot())
        assertEquals(0, f.earnedXp())
    }

    @Test
    fun `partial batch takes only matched inputs and awards proportional xp`() {
        val f = Fixture()
        f.give(SHAFT, 3)
        f.give(FEATHER, 7)
        assertEquals(1, assembly().maxProducible(f.player.inv))
        f.tick(assembly())
        assertEquals(0, f.count(SHAFT))
        assertEquals(4, f.count(FEATHER))
        assertEquals(3, f.count(HEADLESS))
        assertEquals(30, f.earnedXp())
        val after = f.snapshot()
        f.tick(assembly())
        assertEquals(after, f.snapshot())
        assertEquals(30, f.earnedXp())
        assertEquals(0, assembly().maxProducible(f.player.inv))
    }

    @Test
    fun `thirty inputs produce two full rounds without duplication`() {
        val f = Fixture()
        f.give(SHAFT, 30)
        f.give(FEATHER, 30)
        assertEquals(2, assembly().maxProducible(f.player.inv))
        f.tick(assembly())
        assertEquals(15, f.count(SHAFT))
        assertEquals(15, f.count(FEATHER))
        assertEquals(15, f.count(HEADLESS))
        assertEquals(150, f.earnedXp())
        assertEquals(1, assembly().maxProducible(f.player.inv))
        f.tick(assembly())
        assertEquals(0, f.count(SHAFT))
        assertEquals(0, f.count(FEATHER))
        assertEquals(30, f.count(HEADLESS))
        assertEquals(300, f.earnedXp())
        val after = f.snapshot()
        f.tick(assembly())
        assertEquals(after, f.snapshot())
        assertEquals(300, f.earnedXp())
    }

    @Test
    fun `full round followed by remainder rounds menu count up`() {
        val f = Fixture()
        f.give(SHAFT, 18)
        f.give(FEATHER, 22)
        assertEquals(2, assembly().maxProducible(f.player.inv))
        f.tick(assembly())
        f.tick(assembly())
        assertEquals(0, f.count(SHAFT))
        assertEquals(4, f.count(FEATHER))
        assertEquals(18, f.count(HEADLESS))
        assertEquals(180, f.earnedXp())
    }

    @Test
    fun `partial overflow rolls back both inputs and xp`() {
        val f = Fixture()
        f.give(SHAFT, 3)
        f.give(FEATHER, 7)
        f.give(HEADLESS, Int.MAX_VALUE - 2)
        val before = f.snapshot()
        f.tick(assembly())
        assertEquals(before, f.snapshot())
        assertEquals(0, f.earnedXp())
    }

    @Test
    fun `ten item batch also scales its remainder and xp`() {
        val f = Fixture()
        f.give(SHAFT, 3)
        f.give(FEATHER, 7)
        val recipe = assembly().copy(
            inputs = listOf(Material(SHAFT, 10), Material(FEATHER, 10)),
            outputAmount = 10,
            xp = 10.0,
        )
        f.tick(recipe)
        assertEquals(0, f.count(SHAFT))
        assertEquals(4, f.count(FEATHER))
        assertEquals(3, f.count(HEADLESS))
        assertEquals(30, f.earnedXp())
    }

    @Test
    fun `partial colored feathers are selected and consumed without normal feathers`() {
        for (preferred in listOf("obj.hunting_desert_feather", null)) {
            val f = Fixture()
            val colored = "obj.hunting_desert_feather"
            f.give(SHAFT, 7)
            f.give(colored, 3)
            val recipe = assembly().withHeldFeather(f.player.inv, preferred)
            assertEquals(1, recipe.maxProducible(f.player.inv))
            f.tick(recipe)
            assertEquals(4, f.count(SHAFT))
            assertEquals(0, f.count(colored))
            assertEquals(0, f.count(FEATHER))
            assertEquals(3, f.count(HEADLESS))
            assertEquals(30, f.earnedXp())
        }
    }

    @Test
    fun `broad materials require generated slayer unlock before production`() {
        for (material in listOf("obj.slayer_broad_arrowhead", "obj.slayer_broad_bolt_unfinished")) {
            val f = Fixture()
            f.give(material, 15)
            f.give(FEATHER, 15)
            val recipe = assembly().copy(inputs = listOf(Material(material, 15), Material(FEATHER, 15)))
            val before = f.snapshot()
            f.tick(recipe)
            assertEquals(before, f.snapshot(), material)
            assertEquals(0, f.earnedXp(), material)
            val bit = SlayerUnlockRow.all().single {
                it.name.equals("Broader Fletching", ignoreCase = true)
            }.bit
            val varp = if (bit < 32) "varp.slayer_rewards_unlocks" else "varp.slayer_rewards_unlocks1"
            VarPlayerIntMapSetter.set(f.player, varp, 1 shl (bit % 32))
            f.tick(recipe)
            assertEquals(0, f.count(material))
            assertEquals(0, f.count(FEATHER))
            assertEquals(15, f.count(HEADLESS))
            assertEquals(150, f.earnedXp())
        }
    }

    @Test
    fun `cape search with full inventory spends no use and grants nothing`() {
        val f = Fixture()
        f.setCapeDay()
        f.fill()
        val before = f.snapshot()
        f.searchCape()
        assertEquals(before, f.snapshot())
        assertEquals(0, f.player.vars["varbit.fletching_cape_uses"])
    }

    @Test
    fun `cape grants exactly three reward pairs and fourth search grants nothing`() {
        val f = Fixture()
        f.setCapeDay()
        repeat(3) { index ->
            f.searchCape()
            assertEquals(index + 1, f.count("obj.xbows_grapple_tip_bolt_mithril_rope"))
            assertEquals(index + 1, f.count("obj.xbows_crossbow_bronze"))
            assertEquals(index + 1, f.player.vars["varbit.fletching_cape_uses"])
        }
        val after = f.snapshot()
        f.searchCape()
        assertEquals(after, f.snapshot())
        assertEquals(3, f.player.vars["varbit.fletching_cape_uses"])
    }

    private fun requiredAssembly() = assembly().copy(
        statReq = listOf(Tuple2(
            checkNotNull(ServerCacheManager.getStats("stat.fletching".asRSCM(RSCMType.STAT))),
            50,
        )),
    )

    private fun assembly() =
        FletchingRecipe(
            inputs = listOf(Material(SHAFT, 15), Material(FEATHER, 15)),
            output = Material(HEADLESS).obj,
            outputAmount = 15,
            statReq = emptyList(),
            xp = 15.0,
            ticks = 0,
            category = "item-safety-test",
        )

    private class Fixture(base: Int = 99, current: Int = base) {
        private val coroutine = GameCoroutine("fletching-test")
        private val context = ProtectedAccessContextFactory.empty().copy(
            getAlignment = { TextAlignment() },
            getEventBus = { EventBus() },
        )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                client = SilentClient()
                uuid = 915L
                slotId = 1
                assignUid()
                coords = CoordGrid(3222, 3218, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                    arrayOfNulls(28),
                )
                statMap.setBaseLevel("stat.fletching", base.toByte())
                statMap.setCurrentLevel("stat.fletching", current.toByte())
            }

        private val startXp = player.statMap.getFineXP("stat.fletching")

        fun give(obj: String, count: Int = 1) {
            val slot = (0 until 28).first { player.inv[it] == null }
            player.inv[slot] = InvObj(obj, count)
        }

        fun fill() {
            for (slot in 0 until 28) if (player.inv[slot] == null) player.inv[slot] = InvObj(FILLER, 1)
        }

        fun remove(obj: String) {
            for (slot in 0 until 28) {
                if (player.inv[slot]?.id == obj.asRSCM(RSCMType.OBJ)) player.inv[slot] = null
            }
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun snapshot(): List<Pair<Int, Int>?> =
            (0 until 28).map { slot -> player.inv[slot]?.let { it.id to it.count } }

        fun earnedXp(): Int = player.statMap.getFineXP("stat.fletching") - startXp

        fun setCapeDay() {
            VarPlayerIntMapSetter.set(player, "varbit.current_runeday", 100)
            VarPlayerIntMapSetter.set(player, "varbit.fletching_cape_day", 100)
            VarPlayerIntMapSetter.set(player, "varbit.fletching_cape_uses", 0)
        }

        fun searchCape() {
            with(FletchingCapeScript()) { ProtectedAccess(player, coroutine, context).search() }
        }

        fun tick(recipe: FletchingRecipe, expectMesbox: Boolean = false) {
            var result: Result<Unit>? = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = {
                ProtectedAccess(player, coroutine, context)
                    .processFletchTick(FletchingTask(recipe, amount = 1, created = 0))
            }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(value: Result<Unit>) {
                        result = value
                    }
                }
            )
            result?.getOrThrow()
            if (expectMesbox) {
                assertNull(result, "A failed level check must wait at the message box")
                assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
                assertNotNull(player.ui.getModalOrNull("component.chatbox:chatmodal"))
                return
            }
            assertNotNull(result, "A single fletching tick must finish without suspension")
            result!!.getOrThrow()
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
        private const val SHAFT = "obj.arrow_shaft"
        private const val FEATHER = "obj.feather"
        private const val HEADLESS = "obj.headless_arrow"
        private const val TOOL = "obj.knife"
        private const val FILLER = "obj.bronze_dagger"

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
