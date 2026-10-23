package org.rsmod.content.skills.hunter.traps

import jakarta.inject.Singleton
import org.rsmod.api.table.hunter.HunterTrapCreatureRow
import org.rsmod.api.table.hunter.HunterTrapRow
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

@Singleton
class HunterTraps {
    private val traps = HashMap<CoordGrid, Trap>()

    operator fun get(coords: CoordGrid): Trap? = traps[coords]

    fun add(trap: Trap) {
        check(trap.coords !in traps) { "Trap already laid at ${trap.coords}: ${traps[trap.coords]}" }
        traps[trap.coords] = trap
    }

    fun remove(trap: Trap): Boolean = traps.remove(trap.coords, trap)

    fun isActive(trap: Trap): Boolean = traps[trap.coords] === trap

    fun ownedBy(owner: PlayerUid): List<Trap> = traps.values.filter { it.owner == owner }

    fun count(owner: PlayerUid): Int = traps.values.count { it.owner == owner }
}

class Trap(
    val owner: PlayerUid,
    val coords: CoordGrid,
    val row: HunterTrapRow,
    var loc: LocInfo,
    var state: TrapState,
    var stateCycle: Int,
) {
    var creature: HunterTrapCreatureRow? = null

    override fun toString(): String =
        "Trap(owner=$owner, coords=$coords, item=${row.item.internalName}, state=$state)"
}

enum class TrapState {
    Laid,
    Catching,
    Full,
    Failing,
    Failed,
}
