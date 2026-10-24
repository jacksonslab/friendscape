package org.rsmod.content.quest.manager

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
import org.rsmod.content.quest.area.corsaircove.CorsairCurseQuest
import org.rsmod.content.quest.area.crandor.DragonSlayerQuest
import org.rsmod.content.quest.area.icemountain.BelowIceMountainQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
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
class F2pQuestInventoryTest {
    @Test
    fun `museum exchanges each shield half for exactly two matching certificate halves`() {
        for ((shield, certificate) in listOf(
            ShieldOfArravQuest.PHOENIX_SHIELD to ShieldOfArravQuest.LEFT_CERTIFICATE,
            ShieldOfArravQuest.BLACK_ARM_SHIELD to ShieldOfArravQuest.RIGHT_CERTIFICATE,
        )) {
            val access = access(InvObj(shield, 1))
            assertTrue(ShieldOfArravQuest.exchangeShield(access, shield, certificate))
            assertEquals(0, access.inv.count(shield))
            assertEquals(2, access.inv.count(certificate))
            assertFalse(ShieldOfArravQuest.exchangeShield(access, shield, certificate))
            assertEquals(2, access.inv.count(certificate))
        }
    }

    @Test
    fun `certificate halves stack in the slot released by the shield`() {
        val access = fullInventory(ShieldOfArravQuest.PHOENIX_SHIELD)
        assertTrue(ShieldOfArravQuest.exchangeShield(access, ShieldOfArravQuest.PHOENIX_SHIELD, ShieldOfArravQuest.LEFT_CERTIFICATE))
        assertEquals(0, access.inv.count(ShieldOfArravQuest.PHOENIX_SHIELD))
        assertEquals(2, access.inv.count(ShieldOfArravQuest.LEFT_CERTIFICATE))
        assertEquals(27, access.inv.count("obj.ashes"))
    }

    @Test
    fun `both certificate halves become one certificate and cannot be reused`() {
        val access = access(InvObj(ShieldOfArravQuest.LEFT_CERTIFICATE, 1), InvObj(ShieldOfArravQuest.RIGHT_CERTIFICATE, 1))
        assertTrue(ShieldOfArravQuest.joinCertificate(access))
        assertEquals(0, access.inv.count(ShieldOfArravQuest.LEFT_CERTIFICATE))
        assertEquals(0, access.inv.count(ShieldOfArravQuest.RIGHT_CERTIFICATE))
        assertEquals(1, access.inv.count(ShieldOfArravQuest.CERTIFICATE))
        assertFalse(ShieldOfArravQuest.joinCertificate(access))
        assertEquals(1, access.inv.count(ShieldOfArravQuest.CERTIFICATE))
    }

    @Test
    fun `missing certificate halves never consume the remaining half`() {
        for (half in listOf(ShieldOfArravQuest.LEFT_CERTIFICATE, ShieldOfArravQuest.RIGHT_CERTIFICATE)) {
            val access = access(InvObj(half, 1))
            assertFalse(ShieldOfArravQuest.joinCertificate(access))
            assertEquals(1, access.inv.count(half))
            assertEquals(0, access.inv.count(ShieldOfArravQuest.CERTIFICATE))
        }
    }

    @Test
    fun `reward consumes one certificate and pays 600 coins once`() {
        val access = fullInventory(ShieldOfArravQuest.CERTIFICATE)
        assertTrue(ShieldOfArravQuest.redeemCertificate(access))
        assertEquals(600, access.inv.count("obj.coins"))
        assertEquals(0, access.inv.count(ShieldOfArravQuest.CERTIFICATE))
        assertFalse(ShieldOfArravQuest.redeemCertificate(access))
        assertEquals(600, access.inv.count("obj.coins"))
    }

    @Test
    fun `coin overflow preserves the certificate and the original coin stack`() {
        val access = access(InvObj(ShieldOfArravQuest.CERTIFICATE, 1), InvObj("obj.coins", Int.MAX_VALUE - 1))
        assertFalse(ShieldOfArravQuest.redeemCertificate(access))
        assertEquals(1, access.inv.count(ShieldOfArravQuest.CERTIFICATE))
        assertEquals(Int.MAX_VALUE - 1, access.inv.count("obj.coins"))
    }

    @Test
    fun `map assembly releases space in a full inventory and consumes every piece once`() {
        val access = access(*(DragonSlayerQuest.MAP_PARTS.map { InvObj(it, 1) } + List(25) { InvObj("obj.ashes", 1) }).toTypedArray())
        assertTrue(DragonSlayerQuest.assembleMap(access))
        DragonSlayerQuest.MAP_PARTS.forEach { assertEquals(0, access.inv.count(it)) }
        assertEquals(1, access.inv.count(DragonSlayerQuest.MAP))
        assertEquals(25, access.inv.count("obj.ashes"))
        assertFalse(DragonSlayerQuest.assembleMap(access))
        assertEquals(1, access.inv.count(DragonSlayerQuest.MAP))
    }

    @Test
    fun `each missing map piece rolls back assembly`() {
        for (missing in DragonSlayerQuest.MAP_PARTS) {
            val held = DragonSlayerQuest.MAP_PARTS.filter { it != missing }
            val access = access(*held.map { InvObj(it, 1) }.toTypedArray())
            assertFalse(DragonSlayerQuest.assembleMap(access))
            held.forEach { assertEquals(1, access.inv.count(it)) }
            assertEquals(0, access.inv.count(DragonSlayerQuest.MAP))
        }
    }

    @Test
    fun `buying the map piece with exact payment releases the coin slot`() {
        val access = access(*(listOf(InvObj("obj.coins", 10000)) + List(27) { InvObj("obj.ashes", 1) }).toTypedArray())
        assertTrue(DragonSlayerQuest.buyMapPiece(access))
        assertEquals(0, access.inv.count("obj.coins"))
        assertEquals(1, access.inv.count(DragonSlayerQuest.MAP_PARTS[1]))
        assertEquals(27, access.inv.count("obj.ashes"))
    }

    @Test
    fun `buying with change and no map space preserves every coin`() {
        val access = access(*(listOf(InvObj("obj.coins", 10001)) + List(27) { InvObj("obj.ashes", 1) }).toTypedArray())
        assertFalse(DragonSlayerQuest.buyMapPiece(access))
        assertEquals(10001, access.inv.count("obj.coins"))
        assertEquals(0, access.inv.count(DragonSlayerQuest.MAP_PARTS[1]))
    }

    @Test
    fun `insufficient payment never buys a map piece`() {
        val access = access(InvObj("obj.coins", 9999))
        assertFalse(DragonSlayerQuest.buyMapPiece(access))
        assertEquals(9999, access.inv.count("obj.coins"))
        assertEquals(0, access.inv.count(DragonSlayerQuest.MAP_PARTS[1]))
    }

    @Test
    fun `three hull repairs consume exactly three planks and ninety nails`() {
        val access = access(InvObj("obj.woodplank", 1), InvObj("obj.woodplank", 1), InvObj("obj.woodplank", 1), InvObj("obj.nails", 90), InvObj("obj.hammer", 1))
        repeat(3) { assertTrue(DragonSlayerQuest.repairHull(access)) }
        assertEquals(0, access.inv.count("obj.woodplank"))
        assertEquals(0, access.inv.count("obj.nails"))
        assertEquals(1, access.inv.count("obj.hammer"))
        assertFalse(DragonSlayerQuest.repairHull(access))
    }

    @Test
    fun `too few nails preserve both the nails and the plank`() {
        val access = access(InvObj("obj.woodplank", 1), InvObj("obj.nails", 29))
        assertFalse(DragonSlayerQuest.repairHull(access))
        assertEquals(1, access.inv.count("obj.woodplank"))
        assertEquals(29, access.inv.count("obj.nails"))
    }

    @Test
    fun `vanished plank does not consume nails`() {
        val access = access(InvObj("obj.nails", 90))
        assertFalse(DragonSlayerQuest.repairHull(access))
        assertEquals(90, access.inv.count("obj.nails"))
    }

    @Test
    fun `sandwich consumes bread and meat but keeps the knife even in a full inventory`() {
        val access = access(*(listOf(InvObj("obj.bread", 1), InvObj("obj.cooked_meat", 1), InvObj("obj.knife", 1)) + List(25) { InvObj("obj.ashes", 1) }).toTypedArray())
        assertTrue(BelowIceMountainQuest.sandwich(access))
        assertEquals(0, access.inv.count("obj.bread"))
        assertEquals(0, access.inv.count("obj.cooked_meat"))
        assertEquals(1, access.inv.count("obj.knife"))
        assertEquals(1, access.inv.count(BelowIceMountainQuest.SANDWICH))
        assertFalse(BelowIceMountainQuest.sandwich(access))
        assertEquals(1, access.inv.count(BelowIceMountainQuest.SANDWICH))
    }

    @Test
    fun `missing sandwich ingredients preserve the one that remains`() {
        for (held in listOf("obj.bread", "obj.cooked_meat")) {
            val access = access(InvObj(held, 1))
            assertFalse(BelowIceMountainQuest.sandwich(access))
            assertEquals(1, access.inv.count(held))
            assertEquals(0, access.inv.count(BelowIceMountainQuest.SANDWICH))
        }
    }

    @Test
    fun `returning Tess relic removes exactly one and cannot repeat`() {
        val access = fullInventory(CorsairCurseQuest.RELIC)
        assertTrue(CorsairCurseQuest.returnRelic(access))
        assertEquals(0, access.inv.count(CorsairCurseQuest.RELIC))
        assertEquals(27, access.inv.count("obj.ashes"))
        assertFalse(CorsairCurseQuest.returnRelic(access))
    }

    @Test
    fun `giving two crossbows consumes both or neither`() {
        val access = access(InvObj(ShieldOfArravQuest.CROSSBOW, 1))
        assertFalse(access.questItemExchange(listOf(ShieldOfArravQuest.CROSSBOW to 2), emptyList()))
        assertEquals(1, access.inv.count(ShieldOfArravQuest.CROSSBOW))
        access.inv[1] = InvObj(ShieldOfArravQuest.CROSSBOW, 1)
        assertTrue(access.questItemExchange(listOf(ShieldOfArravQuest.CROSSBOW to 2), emptyList()))
        assertEquals(0, access.inv.count(ShieldOfArravQuest.CROSSBOW))
    }
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
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
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
