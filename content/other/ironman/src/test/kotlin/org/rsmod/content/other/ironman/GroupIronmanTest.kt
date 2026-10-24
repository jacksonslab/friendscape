package org.rsmod.content.other.ironman

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.inject.Provider
import java.time.LocalDateTime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.account.character.inv.CharacterInventoryApplier
import org.rsmod.api.account.character.inv.CharacterInventoryPipeline
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.saver.CharacterSaveWriter
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.jdbc.GameConnection
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.ironman.*
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.type.cert
import org.rsmod.game.world.WorldType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GroupIronmanTest {
    @Test
    fun `mainland modes only downgrade and group membership blocks solo changes`() {
        for (current in 0..6) for (desired in 0..6) {
            val expected = current == desired && desired <= 3 ||
                current == PlayerGamemode.IRONMAN && desired == PlayerGamemode.NORMAL ||
                current in listOf(PlayerGamemode.ULTIMATE_IRONMAN, PlayerGamemode.HARDCORE_IRONMAN) &&
                    desired in listOf(PlayerGamemode.NORMAL, PlayerGamemode.IRONMAN)
            assertEquals(expected, IronmanModeSelection.allowed(current, desired, false, 0))
            assertEquals(desired <= 3, IronmanModeSelection.allowed(current, desired, true, 0))
            assertFalse(IronmanModeSelection.allowed(current, desired, true, 1))
        }
        assertFalse(IronmanModeSelection.allowed(0, -1, true, 0))
    }

    @Test
    fun `armour grant is atomic when only part of the bundle fits`() {
        val access = access(*Array(27) { InvObj("obj.ashes", 1) })
        val pieces = listOf("obj.bronze_sword", "obj.bronze_dagger")
        assertFalse(access.player.giveIronmanArmour(pieces))
        assertEquals(27, access.inv.count("obj.ashes"))
        assertEquals(0, access.inv.count("obj.bronze_sword"))
        assertEquals(0, access.inv.count("obj.bronze_dagger"))
        access.inv[0] = null
        assertTrue(access.player.giveIronmanArmour(pieces))
        assertFalse(access.player.giveIronmanArmour(pieces))
        assertEquals(1, access.inv.count("obj.bronze_sword"))
        assertEquals(1, access.inv.count("obj.bronze_dagger"))
    }

    @Test
    fun `deposit conserves items and repeated deposit cannot reuse the source`() {
        val access = access(InvObj("obj.coins", 100))
        val storage = Inventory.create(GroupIronmanService.STORAGE)
        assertTrue(GroupStorageTransfer.move(access, storage, true, 0, "obj.coins".asRSCM(), 100))
        assertFalse(GroupStorageTransfer.move(access, storage, true, 0, "obj.coins".asRSCM(), 100))
        assertEquals(0, access.inv.count("obj.coins"))
        assertEquals(100, storage.count("obj.coins"))
    }

    @Test
    fun `full inventory rejects a withdrawal without consuming shared items`() {
        val access = fullInventory("obj.ashes")
        val storage = Inventory.create(GroupIronmanService.STORAGE)
        storage[0] = InvObj("obj.bronze_sword", 1)
        assertFalse(GroupStorageTransfer.move(access, storage, false, 0, "obj.bronze_sword".asRSCM(), 1))
        assertEquals(1, storage.count("obj.bronze_sword"))
        assertEquals(28, access.inv.count("obj.ashes"))
    }

    @Test
    fun `overflow preserves both source and destination stacks`() {
        val access = access(InvObj("obj.coins", 2))
        val storage = Inventory.create(GroupIronmanService.STORAGE)
        storage[0] = InvObj("obj.coins", Int.MAX_VALUE)
        assertFalse(GroupStorageTransfer.move(access, storage, true, 0, "obj.coins".asRSCM(), 2))
        assertEquals(2, access.inv.count("obj.coins"))
        assertEquals(Int.MAX_VALUE, storage.count("obj.coins"))
    }

    @Test
    fun `stale slots and stale item variables cannot withdraw replacement items`() {
        val access = access()
        val storage = Inventory.create(GroupIronmanService.STORAGE)
        storage[0] = InvObj("obj.bronze_sword", 1, 123)
        assertFalse(GroupStorageTransfer.move(access, storage, false, 0, "obj.coins".asRSCM(), 1))
        assertFalse(GroupStorageTransfer.move(access, storage, false, 0, "obj.bronze_sword".asRSCM(), 1))
        assertTrue(access.inv.isEmpty())
        assertEquals(123, storage[0]!!.vars)
        assertFalse(GroupStorageTransfer.move(access, storage, false, 0, "obj.bronze_sword".asRSCM(), 1, expectedVars = 123))
        assertTrue(access.inv.isEmpty())
    }

    @Test
    @OptIn(dev.openrune.types.util.UncheckedType::class)
    fun `notes deposit as real items and can be withdrawn as notes`() {
        val noted = cert("obj.bronze_sword")
        val access = access(InvObj(noted.id, 5))
        val storage = Inventory.create(GroupIronmanService.STORAGE)
        assertTrue(GroupStorageTransfer.move(access, storage, true, 0, noted.id, 5))
        assertEquals(5, storage.count("obj.bronze_sword"))
        assertTrue(GroupStorageTransfer.move(access, storage, false, 0, "obj.bronze_sword".asRSCM(), 5, notes = true))
        assertEquals(5, access.inv[0]!!.count)
        assertEquals(noted.id, access.inv[0]!!.id)
        assertTrue(storage.isEmpty())
    }

    @Test
    fun `deposit all rolls back every source when any destination overflows`() {
        val access = access(InvObj("obj.bronze_sword", 1), InvObj("obj.coins", 2))
        val storage = Inventory.create(GroupIronmanService.STORAGE)
        storage[0] = InvObj("obj.coins", Int.MAX_VALUE)
        assertFalse(GroupStorageTransfer.depositAll(access, storage))
        assertEquals(1, access.inv.count("obj.bronze_sword"))
        assertEquals(2, access.inv.count("obj.coins"))
        assertEquals(0, storage.count("obj.bronze_sword"))
        assertEquals(Int.MAX_VALUE, storage.count("obj.coins"))
    }

    @Test
    fun `group identity excludes strangers solo ironmen zero ids and other world types`() {
        val first = player().apply { setGamemode(PlayerGamemode.GROUP_IRONMAN); ironmanGroupId = 7 }
        val second = player().apply { setGamemode(PlayerGamemode.GROUP_IRONMAN); ironmanGroupId = 7 }
        assertTrue(first.sameIronmanGroup(second))
        second.ironmanGroupId = 8
        assertFalse(first.sameIronmanGroup(second))
        second.ironmanGroupId = 0
        assertFalse(first.sameIronmanGroup(second))
        second.ironmanGroupId = 7
        second.setGamemode(PlayerGamemode.IRONMAN)
        assertFalse(first.sameIronmanGroup(second))
        second.setGamemode(PlayerGamemode.GROUP_IRONMAN)
        second.worldType = WorldType.GRIDMASTER
        assertFalse(first.sameIronmanGroup(second))
    }

    @Test
    fun `groups persist across service recreation and reject a sixth or duplicate member`() {
        val db = db()
        val service = GroupIronmanService(db, writer)
        val leader = registered("teamleader")
        assertNull(service.create(leader, "Test Crew"))
        val groupId = leader.ironmanGroupId
        for (index in 1..4) assertNull(service.join(registered("teammate$index"), groupId))
        val extra = registered("team_extra")
        assertNotNull(service.join(extra, groupId))
        assertEquals(0, extra.ironmanGroupId)
        assertNotNull(service.join(leader, groupId))
        val reloaded = player().apply { characterId = leader.characterId; uuid = leader.uuid }
        val restarted = GroupIronmanService(db, writer)
        restarted.prepareLogin(reloaded)
        assertEquals(groupId, reloaded.ironmanGroupId)
        assertEquals(PlayerGamemode.GROUP_IRONMAN, reloaded.gamemode)
        assertEquals(5, restarted.group(reloaded)!!.members.size)
        assertEquals(leader.observerUUID, reloaded.observerUUID)
    }

    @Test
    fun `database failure rolls personal inventory and shared storage back together`() {
        val db = db()
        val service = GroupIronmanService(db, writer)
        val leader = registered("rollbacklead")
        assertNull(service.create(leader, "Rollback Crew"))
        assertNull(service.storage(leader))
        leader.tutorialCompleted = true
        leader.inv[0] = InvObj("obj.coins", 20)
        db.withTransactionBlocking("main") { writer.save(it, leader) }
        db.withTransactionBlocking { connection ->
            connection.prepareStatement("ALTER TABLE public.friendscape_iron_storage ADD CONSTRAINT gim_test_count CHECK(count <= 10)").use { it.execute() }
        }
        try {
            val access = ProtectedAccess(leader, GameCoroutine("gim-rollback"), CONTEXT)
            assertThrows(Exception::class.java) { service.transfer(access, true, 0, "obj.coins".asRSCM(), 20, false) }
            assertEquals(20, leader.inv.count("obj.coins"))
            assertEquals(0, service.storage(leader)!!.count("obj.coins"))
        } finally {
            db.withTransactionBlocking { connection ->
                connection.prepareStatement("ALTER TABLE public.friendscape_iron_storage DROP CONSTRAINT gim_test_count").use { it.execute() }
            }
        }
        val access = ProtectedAccess(leader, GameCoroutine("gim-success"), CONTEXT)
        assertTrue(service.transfer(access, true, 0, "obj.coins".asRSCM(), 20, false))
        val restarted = GroupIronmanService(db, writer)
        assertEquals(20, restarted.storage(leader)!!.count("obj.coins"))
        val loaded = db.withTransactionBlocking("main") { connection ->
            val metadata = repository.selectAndCreateMetadataList(connection, leader.username)!!
            inventoryPipeline.append(connection, metadata)
            player().also { target -> metadata.forEach { it.apply(target) } }
        }
        assertEquals(0, loaded.invMap.getValue("inv.inv").count("obj.coins"))
        assertTrue(loaded.tutorialCompleted)
        assertTrue(loaded.tutorialEnrolled)
        assertNotNull(service.leave(leader))
    }

    private fun registered(name: String): Player {
        val db = db()
        return player().apply {
            username = name
            displayName = name
            tutorialEnrolled = true
            coords = CoordGrid(3221, 3218)
            lastLogin = LocalDateTime.now()
            db.withTransactionBlocking("main") { connection ->
                accountId = repository.insertOrSelectAccountId(connection, name, "test-only")!!
                characterId = repository.insertAndSelectCharacterId(connection, accountId)!!
                repository.ensureProgressRow(connection, characterId)
            }
            uuid = characterId.toLong()
            observerUUID = uuid
        }
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
            worldType = WorldType.MAIN
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            invMap["inv.inv"] = inv
            invMap["inv.worn"] = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            worn = invMap.getValue("inv.worn")
        }

    companion object {
        private var postgres: EmbeddedPostgres? = null
        private var database: GameDatabase? = null
        private lateinit var repository: CharacterAccountRepository
        private val inventoryPipeline = CharacterInventoryPipeline(CharacterInventoryApplier())
        private val serverConfig = ServerConfig("GIM tests", 0, 241, "DEV", 255)
        private lateinit var writer: CharacterSaveWriter

        private fun db(): GameDatabase {
            database?.let { return it }
            val pg = EmbeddedPostgres.builder().setPort(0).start()
            postgres = pg
            val config = Provider { DatabaseConfig(pg.getJdbcUrl("postgres", "postgres"), "postgres", "", false) }
            val db = GameDatabase(config, Provider { serverConfig })
            db.connect(GameConnection(config))
            repository = CharacterAccountRepository(jacksonObjectMapper(), CharacterAccountApplier())
            writer = CharacterSaveWriter(repository, setOf(inventoryPipeline), serverConfig)
            database = db
            return db
        }

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
            database?.close()
            postgres?.close()
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
