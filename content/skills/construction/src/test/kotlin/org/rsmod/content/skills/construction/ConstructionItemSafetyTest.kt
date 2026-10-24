package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

/** Building furniture spends materials: every test counts each obj before and after. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ConstructionItemSafetyTest {
    @Test
    fun `a build takes exactly its materials`() {
        val f = Fixture()
        f.give(PLANK, 5)
        f.give(NAILS_IRON, 10)
        f.give(HAMMER, 1)
        assertTrue(f.pay(CHAIR))
        assertEquals(3, f.count(PLANK))
        assertEquals(8, f.count(NAILS_IRON))
        assertEquals(1, f.count(HAMMER))
    }

    @Test
    fun `missing one material takes none of the others`() {
        val f = Fixture()
        f.give(PLANK, 5)
        f.give(NAILS_IRON, 1)
        assertFalse(f.pay(CHAIR))
        assertEquals(5, f.count(PLANK))
        assertEquals(1, f.count(NAILS_IRON))
    }

    @Test
    fun `nails are drawn across tiers, cheapest first, and only as many as needed`() {
        val f = Fixture()
        f.give(PLANK, 2)
        f.give(NAILS_BRONZE, 1)
        f.give(NAILS_IRON, 1)
        f.give(NAILS_STEEL, 5)
        assertTrue(f.pay(CHAIR))
        assertEquals(0, f.count(PLANK))
        assertEquals(0, f.count(NAILS_BRONZE))
        assertEquals(0, f.count(NAILS_IRON))
        assertEquals(5, f.count(NAILS_STEEL))
    }

    @Test
    fun `nails split across tiers that still fall short take nothing`() {
        val f = Fixture()
        f.give(PLANK, 2)
        f.give(NAILS_BRONZE, 1)
        assertFalse(f.pay(CHAIR))
        assertEquals(2, f.count(PLANK))
        assertEquals(1, f.count(NAILS_BRONZE))
    }

    @Test
    fun `materials that vanish between the menu and the build take nothing`() {
        val f = Fixture()
        f.give(PLANK, 2)
        f.give(NAILS_IRON, 2)
        assertTrue(MaterialPayment.canAfford(f.player.inv, CHAIR))
        f.remove(PLANK)
        f.give(PLANK, 1)
        assertFalse(f.pay(CHAIR))
        assertEquals(1, f.count(PLANK))
        assertEquals(2, f.count(NAILS_IRON))
    }

    @Test
    fun `a material counted twice in one recipe is paid once per unit`() {
        val f = Fixture()
        f.give(PLANK, 3)
        assertFalse(f.pay(listOf(PLANK to 2, PLANK to 2)))
        assertEquals(3, f.count(PLANK))
        assertTrue(f.pay(listOf(PLANK to 1, PLANK to 2)))
        assertEquals(0, f.count(PLANK))
    }

    @Test
    fun `a full stack of nails at Int MAX_VALUE pays without overflow`() {
        val f = Fixture()
        f.give(PLANK, 2)
        f.give(NAILS_IRON, Int.MAX_VALUE)
        assertTrue(f.pay(CHAIR))
        assertEquals(Int.MAX_VALUE - 2, f.count(NAILS_IRON))
    }

    @Test
    fun `a second build queued on the same hotspot costs nothing`() {
        val f = Fixture()
        f.give(PLANK, 4)
        f.give(NAILS_IRON, 4)
        val layout = HouseLayout().apply { place(SLOT, room = 1, rotation = 0) }
        val first = layout.buildPaid(SLOT, 0, row = 10, variant = null, replaces = null) { f.pay(CHAIR) }
        val second = layout.buildPaid(SLOT, 0, row = 10, variant = null, replaces = null) { f.pay(CHAIR) }
        assertTrue(first)
        assertFalse(second)
        assertEquals(2, f.count(PLANK))
        assertEquals(2, f.count(NAILS_IRON))
        assertEquals(10, layout.built(SLOT, 0))
    }

    @Test
    fun `an upgrade only pays when the piece it upgrades is still there`() {
        val f = Fixture()
        f.give(PLANK, 4)
        f.give(NAILS_IRON, 4)
        val layout = HouseLayout().apply { place(SLOT, room = 1, rotation = 0) }
        assertFalse(layout.buildPaid(SLOT, 0, row = 11, variant = null, replaces = 10) { f.pay(CHAIR) })
        assertEquals(4, f.count(PLANK))
        layout.build(SLOT, 0, 10)
        assertTrue(layout.buildPaid(SLOT, 0, row = 11, variant = null, replaces = 10) { f.pay(CHAIR) })
        assertEquals(2, f.count(PLANK))
        assertEquals(11, layout.built(SLOT, 0))
    }

    @Test
    fun `a failed payment leaves the hotspot empty`() {
        val f = Fixture()
        f.give(PLANK, 1)
        val layout = HouseLayout().apply { place(SLOT, room = 1, rotation = 0) }
        assertFalse(layout.buildPaid(SLOT, 0, row = 10, variant = null, replaces = null) { f.pay(CHAIR) })
        assertNull(layout.built(SLOT, 0))
        assertEquals(1, f.count(PLANK))
    }

    @Test
    fun `a build into a room that was removed costs nothing`() {
        val f = Fixture()
        f.give(PLANK, 2)
        f.give(NAILS_IRON, 2)
        val layout = HouseLayout()
        assertFalse(layout.buildPaid(SLOT, 0, row = 10, variant = null, replaces = null) { f.pay(CHAIR) })
        assertEquals(2, f.count(PLANK))
    }

    private class Fixture {
        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
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

        fun count(obj: String): Int = player.inv.count(obj)

        fun pay(materials: List<Pair<String, Int>>): Boolean =
            MaterialPayment.pay(player, player.inv, materials)
    }

    companion object {
        private const val PLANK = "obj.woodplank"
        private const val HAMMER = "obj.hammer"
        private const val NAILS_BRONZE = "obj.nails_bronze"
        private const val NAILS_IRON = "obj.nails_iron"
        private const val NAILS_STEEL = "obj.nails"
        private val CHAIR = listOf(PLANK to 2, OBJ_ANY_NAILS to 2)
        private val SLOT = slotKey(LEVEL_GROUND, 4, 4)

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
