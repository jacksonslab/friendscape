package org.rsmod.content.skills.sailing

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.table.sailing.FriendscapeSailingShipwrecksRow
import org.rsmod.game.MapClock
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class WreckType(row: FriendscapeSailingShipwrecksRow) {
    val active: Int = row.active.id
    val stump: Int = row.stump.id
    val level: Int = row.level
    val salvageXp: Double = row.salvageXp / XP_SCALE
    val sortXp: Double = row.sortXp / XP_SCALE
    val salvage: ItemServerType = row.salvage
    val duration: Int = row.duration
    val lowChance: Int = row.lowChance
    val highChance: Int = row.highChance
    val spawns: List<Pair<CoordGrid, Int>> = row.spawnCoords.zip(row.spawnAngles)
    val loot: List<SalvageLoot> =
        row.lootObjs.indices.map { SalvageLoot(row.lootObjs[it], row.lootMin[it], row.lootMax[it], row.lootWeights[it]) }

    fun roll(random: GameRandom): Pair<ItemServerType, Int> {
        var pick = random.of(loot.sumOf { it.weight })
        for (entry in loot) {
            if (pick < entry.weight) {
                return entry.obj to random.of(entry.min, entry.max)
            }
            pick -= entry.weight
        }
        val last = loot.last()
        return last.obj to last.min
    }

    private companion object {
        private const val XP_SCALE = 10.0
    }
}

data class SalvageLoot(val obj: ItemServerType, val min: Int, val max: Int, val weight: Int)

object WreckTypes {
    val ALL: List<WreckType> by lazy { FriendscapeSailingShipwrecksRow.all().map(::WreckType) }

    private val byActive by lazy { ALL.associateBy { it.active } }
    private val bySalvage by lazy { ALL.associateBy { it.salvage.id } }

    fun byActiveLoc(loc: Int): WreckType? = byActive[loc]

    fun bySalvage(obj: Int): WreckType? = bySalvage[obj]
}

class Wreck(val type: WreckType, val coords: CoordGrid, val angle: Int) {
    var active: Boolean = false
    var depletesAt: Int = -1
    var respawnsAt: Int = -1
}

/**
 * Keeps the open-sea shipwrecks cycling: a wreck rises on its stump, lasts its salvage duration
 * once someone starts salvaging it, then sinks back to a stump for [RESPAWN_TICKS].
 */
@Singleton
class ShipwreckManager
@Inject
constructor(private val locRepo: LocRepository, private val mapClock: MapClock, private val random: GameRandom) {
    private val wrecks = HashMap<CoordGrid, Wreck>()

    fun start() {
        if (wrecks.isNotEmpty()) {
            return
        }
        for (type in WreckTypes.ALL) {
            for ((coords, angle) in type.spawns) {
                val wreck = Wreck(type, coords, angle)
                wrecks[coords] = wreck
                if (random.of(ACTIVE_ODDS) != 0) {
                    raise(wreck)
                } else {
                    wreck.respawnsAt = mapClock.cycle + random.of(RESPAWN_TICKS)
                }
            }
        }
    }

    fun tick() {
        val now = mapClock.cycle
        for (wreck in wrecks.values) {
            if (wreck.active && wreck.depletesAt in 0..now) {
                sink(wreck)
            } else if (!wreck.active && wreck.respawnsAt in 0..now) {
                raise(wreck)
            }
        }
    }

    operator fun get(coords: CoordGrid): Wreck? = wrecks[coords]

    fun nearestActive(center: CoordGrid, range: Int): Wreck? =
        wrecks.values
            .filter { it.active && it.coords.level == center.level && it.coords.chebyshevDistance(center) <= range }
            .minByOrNull { it.coords.chebyshevDistance(center) }

    fun startDepleting(wreck: Wreck) {
        if (wreck.depletesAt < 0) {
            wreck.depletesAt = mapClock.cycle + wreck.type.duration
        }
    }

    private fun raise(wreck: Wreck) {
        wreck.active = true
        wreck.depletesAt = -1
        wreck.respawnsAt = -1
        place(wreck, wreck.type.active)
    }

    private fun sink(wreck: Wreck) {
        wreck.active = false
        wreck.depletesAt = -1
        wreck.respawnsAt = mapClock.cycle + RESPAWN_TICKS
        place(wreck, wreck.type.stump)
    }

    private fun place(wreck: Wreck, loc: Int) {
        val name = dev.openrune.rscm.RSCM.getReverseMapping(dev.openrune.rscm.RSCMType.LOC, loc)
        locRepo.add(wreck.coords, name, Int.MAX_VALUE, LocAngle[wreck.angle], LocShape.CentrepieceStraight)
    }

    private companion object {
        private const val RESPAWN_TICKS = 100
        private const val ACTIVE_ODDS = 3
    }
}
