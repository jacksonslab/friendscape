package org.rsmod.content.other.ironman

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.account.saver.CharacterSaveWriter
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.invtx.transfer
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.ironmanGroupId
import org.rsmod.api.player.ironman.isGroupIronman
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.ironman.syncGroupObserver
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

internal var Player.tutorialEnrolled by boolVarp("varp.friendscape_tutorial_started")
internal var Player.tutorialCompleted by boolVarp("varp.tut2_completed")

internal fun Player.canChooseIronman(): Boolean = tutorialEnrolled && !tutorialCompleted

internal data class IronmanGroup(val id: Int, val name: String, val leader: Int, val members: List<Pair<Int, String>>)

@Singleton
class GroupIronmanService @Inject constructor(
    private val database: GameDatabase,
    private val writer: CharacterSaveWriter,
) {
    private var schemaReady = false

    fun prepareLogin(player: Player) {
        val group = group(player)
        if (group != null) {
            player.ironmanGroupId = group.id
            player.setGamemode(PlayerGamemode.GROUP_IRONMAN)
        } else {
            player.ironmanGroupId = 0
            if (player.isGroupIronman) player.setGamemode(PlayerGamemode.NORMAL)
        }
        player.syncGroupObserver()
    }

    internal fun group(player: Player): IronmanGroup? = transaction(player) { connection ->
        membership(connection, player)
    }

    internal fun create(player: Player, requestedName: String): String? {
        if (!player.canChooseIronman() || player.ironmanGroupId != 0) return "Choose your group before leaving Tutorial Island."
        val name = requestedName.trim().replace(Regex("\\s+"), " ")
        if (!Regex("[A-Za-z0-9 ]{3,20}").matches(name)) return "Use 3 to 20 letters, numbers or spaces for the group name."
        val result = transaction(player) { connection ->
            if (membership(connection, player) != null) return@transaction null
            val id = connection.prepareStatement(
                "INSERT INTO public.friendscape_iron_groups(world_type,name,leader) VALUES (?,?,?) ON CONFLICT DO NOTHING RETURNING id"
            ).use { statement ->
                statement.setString(1, player.worldType.key)
                statement.setString(2, name)
                statement.setInt(3, player.characterId)
                statement.executeQuery().use { if (it.next()) it.getInt(1) else null }
            } ?: return@transaction null
            addMember(connection, player, id)
            id
        } ?: return "That group name is already in use, or you already belong to a group."
        player.ironmanGroupId = result
        player.setGamemode(PlayerGamemode.GROUP_IRONMAN)
        player.syncGroupObserver()
        return null
    }

    internal fun join(player: Player, groupId: Int): String? {
        if (!player.canChooseIronman() || player.ironmanGroupId != 0) return "Only fresh characters on Tutorial Island can join a group."
        val error = transaction(player) { connection ->
            val group = readGroup(connection, groupId, player.worldType.key, lock = true)
                ?: return@transaction "That group is no longer available."
            if (membership(connection, player) != null) return@transaction "You already belong to a group."
            if (group.members.size >= MAX_MEMBERS) return@transaction "That group already has five members."
            addMember(connection, player, groupId)
            null
        }
        if (error != null) return error
        player.ironmanGroupId = groupId
        player.setGamemode(PlayerGamemode.GROUP_IRONMAN)
        player.syncGroupObserver()
        return null
    }

    internal fun storage(player: Player): Inventory? {
        if (player.canChooseIronman()) return null
        return transaction(player) { connection ->
            val group = membership(connection, player) ?: return@transaction null
            loadStorage(connection, group.id)
        }
    }

    internal fun leave(player: Player): String? {
        val error = transaction(player) { connection ->
            val group = membership(connection, player) ?: return@transaction "You are not in a group."
            readGroup(connection, group.id, player.worldType.key, lock = true)
            if (group.members.size == 1 && loadStorage(connection, group.id).isNotEmpty()) {
                return@transaction "Withdraw all shared items before the last member leaves."
            }
            connection.prepareStatement("DELETE FROM public.friendscape_iron_members WHERE world_type=? AND character_id=?").use {
                it.setString(1, player.worldType.key)
                it.setInt(2, player.characterId)
                it.executeUpdate()
            }
            if (group.members.size == 1) {
                connection.prepareStatement("DELETE FROM public.friendscape_iron_groups WHERE id=?").use { it.setInt(1, group.id); it.executeUpdate() }
            } else if (group.leader == player.characterId) {
                connection.prepareStatement("UPDATE public.friendscape_iron_groups SET leader=? WHERE id=?").use {
                    it.setInt(1, group.members.first { member -> member.first != player.characterId }.first)
                    it.setInt(2, group.id)
                    it.executeUpdate()
                }
            }
            null
        }
        if (error != null) return error
        player.ironmanGroupId = 0
        player.setGamemode(PlayerGamemode.NORMAL)
        player.syncGroupObserver()
        return null
    }

    internal fun transfer(access: ProtectedAccess, deposit: Boolean, slot: Int, expectedId: Int, requested: Int, notes: Boolean, expectedVars: Int = 0): Boolean {
        val player = access.player
        if (!player.isGroupIronman || player.canChooseIronman() || requested <= 0) return false
        val before = player.inv.objs.copyOf()
        try {
            return transaction(player) { connection ->
            val group = membership(connection, player) ?: return@transaction false
            readGroup(connection, group.id, player.worldType.key, lock = true) ?: return@transaction false
            val storage = loadStorage(connection, group.id)
                val moved = if (deposit && slot == -1) GroupStorageTransfer.depositAll(access, storage)
                    else GroupStorageTransfer.move(access, storage, deposit, slot, expectedId, requested, notes, expectedVars)
                if (!moved) return@transaction false
                writer.save(connection, player)
                saveStorage(connection, group.id, storage)
                true
            }
        } catch (failure: Throwable) {
            for (index in before.indices) player.inv[index] = before[index]
            throw failure
        }
    }

    private fun membership(connection: DatabaseConnection, player: Player): IronmanGroup? {
        val id = connection.prepareStatement(
            "SELECT group_id FROM public.friendscape_iron_members WHERE world_type=? AND character_id=?"
        ).use { statement ->
            statement.setString(1, player.worldType.key)
            statement.setInt(2, player.characterId)
            statement.executeQuery().use { if (it.next()) it.getInt(1) else null }
        } ?: return null
        return readGroup(connection, id, player.worldType.key)
    }

    private fun readGroup(connection: DatabaseConnection, id: Int, worldType: String, lock: Boolean = false): IronmanGroup? {
        val group = connection.prepareStatement(
            "SELECT name,leader FROM public.friendscape_iron_groups WHERE id=? AND world_type=?" + if (lock) " FOR UPDATE" else ""
        ).use { statement ->
            statement.setInt(1, id)
            statement.setString(2, worldType)
            statement.executeQuery().use { if (it.next()) it.getString(1) to it.getInt(2) else null }
        } ?: return null
        val members = connection.prepareStatement(
            "SELECT character_id,display_name FROM public.friendscape_iron_members WHERE group_id=? ORDER BY character_id"
        ).use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.getInt(1) to result.getString(2)) } }
        }
        return IronmanGroup(id, group.first, group.second, members)
    }

    private fun addMember(connection: DatabaseConnection, player: Player, groupId: Int) {
        connection.prepareStatement(
            "INSERT INTO public.friendscape_iron_members(world_type,character_id,group_id,display_name) VALUES (?,?,?,?)"
        ).use { statement ->
            statement.setString(1, player.worldType.key)
            statement.setInt(2, player.characterId)
            statement.setInt(3, groupId)
            statement.setString(4, player.displayName)
            statement.executeUpdate()
        }
    }

    private fun loadStorage(connection: DatabaseConnection, groupId: Int): Inventory {
        val inventory = Inventory.create(STORAGE)
        connection.prepareStatement(
            "SELECT slot,obj_key,count,vars FROM public.friendscape_iron_storage WHERE group_id=? ORDER BY slot"
        ).use { statement ->
            statement.setInt(1, groupId)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val slot = result.getInt(1)
                    check(slot in inventory.indices) { "Invalid shared storage slot: $slot" }
                    inventory[slot] = InvObj(result.getString(2), result.getInt(3), result.getInt(4))
                }
            }
        }
        return inventory
    }

    private fun saveStorage(connection: DatabaseConnection, groupId: Int, inventory: Inventory) {
        connection.prepareStatement("DELETE FROM public.friendscape_iron_storage WHERE group_id=?").use {
            it.setInt(1, groupId)
            it.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO public.friendscape_iron_storage(group_id,slot,obj_key,count,vars) VALUES (?,?,?,?,?)"
        ).use { statement ->
            for (slot in inventory.indices) {
                val item = inventory[slot] ?: continue
                statement.setInt(1, groupId)
                statement.setInt(2, slot)
                statement.setString(3, RSCM.getReverseMapping(RSCMType.OBJ, item.id))
                statement.setInt(4, item.count)
                statement.setInt(5, item.vars)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun <T> transaction(player: Player, action: (DatabaseConnection) -> T): T {
        if (!schemaReady) {
            database.withTransactionBlocking { connection ->
                for (sql in SCHEMA) connection.prepareStatement(sql).use { it.execute() }
            }
            schemaReady = true
        }
        return database.withTransactionBlocking(player.worldType.key, action)
    }

    companion object {
        const val MAX_MEMBERS = 5
        const val STORAGE = "inv.inv_group_temp"
        private val SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS public.friendscape_iron_groups(id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,world_type TEXT NOT NULL,name VARCHAR(20) NOT NULL,leader INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS friendscape_iron_groups_name ON public.friendscape_iron_groups(world_type,lower(name))",
            "CREATE TABLE IF NOT EXISTS public.friendscape_iron_members(world_type TEXT NOT NULL,character_id INTEGER NOT NULL,group_id INTEGER NOT NULL REFERENCES public.friendscape_iron_groups(id),display_name TEXT NOT NULL,PRIMARY KEY(world_type,character_id))",
            "CREATE TABLE IF NOT EXISTS public.friendscape_iron_storage(group_id INTEGER NOT NULL REFERENCES public.friendscape_iron_groups(id),slot INTEGER NOT NULL CHECK(slot >= 0 AND slot < 1000),obj_key TEXT NOT NULL,count INTEGER NOT NULL CHECK(count > 0),vars INTEGER NOT NULL,PRIMARY KEY(group_id,slot))",
        )
    }
}

internal object GroupStorageTransfer {
    fun move(access: ProtectedAccess, storage: Inventory, deposit: Boolean, slot: Int, expectedId: Int, requested: Int, notes: Boolean = false, expectedVars: Int = 0): Boolean {
        val source = if (deposit) access.inv else storage
        val destination = if (deposit) storage else access.inv
        val item = source.objs.getOrNull(slot) ?: return false
        if (item.id != expectedId || item.vars != 0 || item.vars != expectedVars || requested <= 0) return false
        val type = ServerCacheManager.getItem(item.id) ?: return false
        if (deposit && !type.tradeable && !type.stockmarket) return false
        val matches = source.objs.withIndex().filter { it.value?.id == item.id && it.value?.vars == item.vars }
        val available = matches.sumOf { it.value!!.count.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        var remaining = requested.coerceAtMost(available)
        return access.player.invTransaction(source, destination) {
            val from = select(source)
            val into = select(destination)
            for ((index, obj) in matches) {
                val count = minOf(remaining, obj!!.count)
                if (count <= 0) break
                transfer(from, index, count, into, strict = true, cert = !deposit && notes, uncert = deposit)
                remaining -= count
            }
        }.success
    }

    fun depositAll(access: ProtectedAccess, storage: Inventory): Boolean {
        val held = access.inv.objs.withIndex().filter {
            val type = it.value?.let { item -> ServerCacheManager.getItem(item.id) }
            type != null && it.value?.vars == 0 && (type.tradeable || type.stockmarket)
        }
        if (held.isEmpty()) return false
        return access.player.invTransaction(access.inv, storage) {
            val from = select(access.inv)
            val into = select(storage)
            for ((slot, obj) in held) transfer(from, slot, obj!!.count, into, strict = true, uncert = true)
        }.success
    }
}
