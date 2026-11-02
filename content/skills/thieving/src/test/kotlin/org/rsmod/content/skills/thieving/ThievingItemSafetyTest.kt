package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
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
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.table.thieving.FriendscapeThievingChestRow
import org.rsmod.api.table.thieving.FriendscapeThievingPickpocketRow
import org.rsmod.api.table.thieving.FriendscapeThievingStallRow
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
class ThievingItemSafetyTest {
    @Test
    fun `open-all swaps every pouch for its coins`() {
        val f = Fixture()
        f.give(CITIZEN_POUCH, 28)
        assertEquals(28, f.access.openPouches(citizen(), all = true))
        assertEquals(0, f.count(CITIZEN_POUCH))
        assertEquals(84, f.count(COINS))
    }

    @Test
    fun `open takes exactly one pouch`() {
        val f = Fixture()
        f.give(CITIZEN_POUCH, 5)
        f.give(COINS, 10)
        assertEquals(1, f.access.openPouches(citizen(), all = false))
        assertEquals(4, f.count(CITIZEN_POUCH))
        assertEquals(13, f.count(COINS))
    }

    @Test
    fun `ranged pouches pay inside their range`() {
        val f = Fixture()
        f.give(ROGUE_POUCH, 20)
        f.access.openPouches(row("rogue"), all = true)
        assertEquals(0, f.count(ROGUE_POUCH))
        val coins = f.count(COINS)
        assertTrue(coins in 20 * 25..20 * 40, "$coins coins from 20 rogue pouches")
    }

    @Test
    fun `pouches stop opening at the max coin stack`() {
        val f = Fixture()
        f.give(COINS, Int.MAX_VALUE - 5)
        f.give(CITIZEN_POUCH, 3)
        assertEquals(1, f.access.openPouches(citizen(), all = true))
        assertEquals(Int.MAX_VALUE - 2, f.count(COINS))
        assertEquals(2, f.count(CITIZEN_POUCH))
    }

    @Test
    fun `a full coin stack opens nothing and keeps every pouch`() {
        val f = Fixture()
        f.give(COINS, Int.MAX_VALUE)
        f.give(CITIZEN_POUCH, 4)
        assertEquals(0, f.access.openPouches(citizen(), all = true))
        assertEquals(Int.MAX_VALUE, f.count(COINS))
        assertEquals(4, f.count(CITIZEN_POUCH))
    }

    @Test
    fun `opening one pouch into a full pack without coins keeps the pouch`() {
        val f = Fixture()
        f.give(CITIZEN_POUCH, 3)
        f.fill()
        assertEquals(0, f.access.openPouches(citizen(), all = false))
        assertEquals(3, f.count(CITIZEN_POUCH))
        assertEquals(0, f.count(COINS))
    }

    @Test
    fun `opening every pouch in a full pack reuses the pouch slot`() {
        val f = Fixture()
        f.give(CITIZEN_POUCH, 3)
        f.fill()
        assertEquals(3, f.access.openPouches(citizen(), all = true))
        assertEquals(0, f.count(CITIZEN_POUCH))
        assertEquals(9, f.count(COINS))
        assertEquals(28, f.player.inv.occupiedSpace())
    }

    @Test
    fun `opening twice with one pouch pays once`() {
        val f = Fixture()
        f.give(CITIZEN_POUCH, 1)
        f.access.openPouches(citizen(), all = true)
        assertEquals(0, f.access.openPouches(citizen(), all = true))
        assertEquals(3, f.count(COINS))
    }

    @Test
    fun `pickpocketing stops at 28 pouches`() {
        val f = Fixture()
        f.give(CITIZEN_POUCH, 27)
        assertNull(f.access.pickpocketBlocker(citizen()))
        assertTrue(f.access.giveAll(listOf(Stolen(item(CITIZEN_POUCH), 1))))
        assertEquals(28, f.count(CITIZEN_POUCH))
        assertEquals(POUCHES_FULL, f.access.pickpocketBlocker(citizen()))
    }

    @Test
    fun `the Ardougne cloak raises the pouch cap`() {
        val f = Fixture()
        assertEquals(28, f.access.pouchCap())
        f.give("obj.ardy_cape_medium", 1)
        assertEquals(56, f.access.pouchCap())
        f.player.worn[1] = InvObj("obj.ardy_cape_elite", 1)
        assertEquals(140, f.access.pouchCap())
    }

    @Test
    fun `a full pack blocks pickpocketing unless a pouch stack is held`() {
        val f = Fixture()
        f.fill()
        assertEquals(NO_SPACE, f.access.pickpocketBlocker(citizen()))
        val g = Fixture()
        g.give(CITIZEN_POUCH, 1)
        g.fill()
        assertNull(g.access.pickpocketBlocker(citizen()))
    }

    @Test
    fun `loot that does not fit is not handed over at all`() {
        val f = Fixture()
        f.give(COINS, 1)
        f.fill(leave = 1)
        val stolen = listOf(Stolen(item("obj.silk"), 1), Stolen(item("obj.spicespot"), 1), Stolen(item(COINS), 5))
        assertFalse(f.access.giveAll(stolen))
        assertEquals(0, f.count("obj.silk"))
        assertEquals(0, f.count("obj.spicespot"))
        assertEquals(1, f.count(COINS))
    }

    @Test
    fun `coin loot that would overflow the stack is refused`() {
        val f = Fixture()
        f.give(COINS, Int.MAX_VALUE - 1)
        assertFalse(f.access.giveAll(listOf(Stolen(item(COINS), 5))))
        assertEquals(Int.MAX_VALUE - 1, f.count(COINS))
    }

    @Test
    fun `a citizen pick gives one pouch, and rogue double loot adds its coins directly`() {
        val random = DefaultGameRandom(7)
        assertEquals(mapOf(CITIZEN_POUCH to 1), counts(ThievingLoot.pickpocket(random, citizen(), false, coinsType())))
        assertEquals(
            mapOf(CITIZEN_POUCH to 1, COINS to 3),
            counts(ThievingLoot.pickpocket(random, citizen(), true, coinsType())),
        )
    }

    @Test
    fun `a paladin pick always includes its chaos runes and doubles them`() {
        val random = DefaultGameRandom(11)
        val paladin = row("paladin")
        assertEquals(
            mapOf("obj.pickpocket_coin_pouch_paladin" to 1, "obj.chaosrune" to 2),
            counts(ThievingLoot.pickpocket(random, paladin, false, coinsType())),
        )
        assertEquals(
            mapOf("obj.pickpocket_coin_pouch_paladin" to 1, "obj.chaosrune" to 4, COINS to 80),
            counts(ThievingLoot.pickpocket(random, paladin, true, coinsType())),
        )
    }

    @Test
    fun `weighted picks give a pouch or one loot item, never both`() {
        val random = DefaultGameRandom(3)
        val elf = row("elf")
        repeat(2000) {
            val stolen = ThievingLoot.pickpocket(random, elf, false, coinsType())
            val main = stolen.filterNot { it.obj.internalName in ELF_TERTIARIES }
            assertEquals(1, main.size, "elf pick: $stolen")
        }
    }

    @Test
    fun `every table resolves to real objs with sane quantities`() {
        val tables =
            FriendscapeThievingStallRow.all().map { it.loot } +
                FriendscapeThievingPickpocketRow.all().map { it.loot } +
                FriendscapeThievingChestRow.all().flatMap { listOf(it.loot, it.always) }
        for (table in tables) {
            for ((obj, min, max, weight) in table) {
                assertTrue(min in 1..max && weight > 0, "${obj.internalName} $min-$max w$weight")
            }
        }
        assertTrue(FriendscapeThievingStallRow.all().all { it.loot.isNotEmpty() })
    }

    private fun citizen() = row("citizen")

    private fun row(name: String) = FriendscapeThievingPickpocketRow.getRow("dbrow.thieving_pickpocket_$name")

    private fun item(obj: String): ItemServerType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))

    private fun counts(stolen: List<Stolen>): Map<String, Int> = stolen.associate { it.obj.internalName to it.count }

    private class Fixture {
        private val events = EventBus()
        private val coroutine = GameCoroutine("thieving-test")
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getRandom = { DefaultGameRandom(42) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                client = SilentClient()
                uuid = 913L
                slotId = 1
                assignUid()
                coords = CoordGrid(3208, 3214, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            }

        val access: ProtectedAccess
            get() {
                player.activeCoroutine = coroutine
                return ProtectedAccess(player, coroutine, context)
            }

        fun give(obj: String, count: Int) {
            val slot = (0 until 28).first { player.inv[it] == null }
            player.inv[slot] = InvObj(obj, count)
        }

        fun fill(leave: Int = 0) {
            var left = leave
            for (slot in 0 until 28) {
                if (player.inv[slot] != null) continue
                if (left > 0) {
                    left--
                    continue
                }
                player.inv[slot] = InvObj(FILLER, 1)
            }
        }

        fun count(obj: String): Int = player.inv.count(obj)
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
        private const val CITIZEN_POUCH = "obj.pickpocket_coin_pouch_citizen"
        private const val ROGUE_POUCH = "obj.pickpocket_coin_pouch_rogue"
        private const val FILLER = "obj.bronze_dagger"
        private val ELF_TERTIARIES = setOf("obj.prif_crystal_shard", "obj.prif_teleport_seed")

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
