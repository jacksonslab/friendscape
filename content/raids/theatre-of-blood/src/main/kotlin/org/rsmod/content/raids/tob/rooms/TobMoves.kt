package org.rsmod.content.raids.tob.rooms

import kotlin.math.sign
import org.rsmod.content.raids.tob.TobContext
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Small movement and targeting helpers shared by the rooms' hand-driven npcs. */
internal object TobMoves {
    fun stepToward(ctx: TobContext, npc: Npc, target: CoordGrid) {
        val from = npc.coords
        val dx = (target.x - from.x).sign
        val dz = (target.z - from.z).sign
        if (dx == 0 && dz == 0) return
        val options =
            listOf(from.translate(dx, dz), from.translate(dx, 0), from.translate(0, dz)).filter { it != from }
        val next = options.firstOrNull { ctx.walkable(it) } ?: return
        npc.walk(next)
    }

    fun centre(npc: Npc): CoordGrid = npc.coords.translate(npc.size / 2, npc.size / 2)

    fun distance(npc: Npc, player: Player): Int = npc.distanceTo(player)

    fun closest(npc: Npc, players: List<Player>): Player? = players.minByOrNull { npc.distanceTo(it) }

    fun furthest(npc: Npc, players: List<Player>): Player? = players.maxByOrNull { npc.distanceTo(it) }

    fun adjacent(npc: Npc, player: Player): Boolean = npc.distanceTo(player) <= 1

    fun under(npc: Npc, coords: CoordGrid): Boolean =
        coords.level == npc.coords.level &&
            coords.x in npc.coords.x until npc.coords.x + npc.size &&
            coords.z in npc.coords.z until npc.coords.z + npc.size
}
