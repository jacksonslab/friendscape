package org.rsmod.content.raids.tob

import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class TobRaider(val uuid: Long, val name: String) {
    var deaths: Int = 0
    var dead: Boolean = false
    var mvpPoints: Int = 0
    var left: Boolean = false
    var chestSlot: Int = -1
    val bossDamage: MutableMap<Int, Int> = HashMap()
}

internal class TobRaid(
    val partyLeader: Long,
    val session: InstanceSession,
    val base: CoordGrid,
    private val manager: TobRaids,
) {
    val raiders: MutableMap<Long, TobRaider> = LinkedHashMap()
    lateinit var rooms: List<TobRoom>

    var scaleSize: Int = 0
        private set

    var currentRoom: Int = 0
    var ended: Boolean = false
    var wipeDeadline: Int = -1
    val startedTick: Int = manager.ctx.cycle

    val hpFactor: Double
        get() = TobScaling.hpFactor(scaleSize.coerceAtLeast(1))

    /** The roster locks when the Maiden fight begins; nobody new may enter after that. */
    val rosterLocked: Boolean
        get() = scaleSize > 0

    fun coord(static: CoordGrid): CoordGrid = TobLayout.toInstance(base, static)

    fun static(coords: CoordGrid): CoordGrid = TobLayout.toStatic(base, coords)

    fun contains(coords: CoordGrid): Boolean = TobLayout.contains(base, coords)

    fun lockRoster() {
        if (scaleSize == 0) scaleSize = raiders.values.count { !it.left }.coerceAtLeast(1)
    }

    fun raider(player: Player): TobRaider? = player.uuid?.let(raiders::get)

    fun players(): List<Player> =
        raiders.values.filter { !it.left }.mapNotNull { manager.playerFor(it.uuid) }

    fun alivePlayers(): List<Player> =
        raiders.values.filter { !it.left && !it.dead }.mapNotNull { manager.playerFor(it.uuid) }

    fun isAlive(player: Player): Boolean = raider(player)?.let { !it.left && !it.dead } == true

    fun room(): TobRoom = rooms[currentRoom]

    fun roomAt(static: CoordGrid): TobRoom? =
        rooms.firstOrNull { it.arena.contains(static) || it.hasPassage(static) }

    fun attach(npc: Npc) = manager.attach(this, npc)

    fun broadcast(text: String) {
        players().forEach { it.mes(text) }
    }

    fun recordBossDamage(player: Player, roomIndex: Int, damage: Int) {
        val raider = raider(player) ?: return
        raider.bossDamage.merge(roomIndex, damage, Int::plus)
    }

    fun onRoomComplete(room: TobRoom) = manager.onRoomComplete(this, room)

    fun mvpOf(roomIndex: Int): TobRaider? =
        raiders.values.filter { (it.bossDamage[roomIndex] ?: 0) > 0 }.maxByOrNull { it.bossDamage[roomIndex] ?: 0 }
}
