package org.rsmod.content.skills.thieving

import dev.openrune.types.ItemServerType
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.Tuple4
import org.rsmod.api.table.thieving.FriendscapeThievingPickpocketRow

internal data class Stolen(val obj: ItemServerType, val count: Int)

internal object ThievingLoot {
    fun roll(random: GameRandom, table: List<Tuple4<ItemServerType, Int, Int, Int>>): Stolen? {
        if (table.isEmpty()) return null
        var pick = random.of(0, table.sumOf { it.t3 } - 1)
        for ((obj, min, max, weight) in table) {
            if (pick < weight) return Stolen(obj, random.of(min, max))
            pick -= weight
        }
        return null
    }

    fun everything(
        random: GameRandom,
        table: List<Tuple4<ItemServerType, Int, Int, Int>>,
    ): List<Stolen> =
        table.map { (obj, min, max) -> Stolen(obj, random.of(min, max)) }

    /**
     * One successful pick. A pouch weight puts the coin pouch on the same roll as the loot table;
     * without one the pouch comes on top of any loot. Double loot doubles every item, but a pouch
     * stays single and its twin is paid straight out in coins, as rogue equipment does in game.
     */
    fun pickpocket(
        random: GameRandom,
        row: FriendscapeThievingPickpocketRow,
        double: Boolean,
        coins: ItemServerType,
    ): List<Stolen> {
        val pouch = row.pouch
        val pouchWeight = row.pouchWeight
        val multiplier = if (double) 2 else 1
        val stolen = mutableListOf<Stolen>()

        fun takePouch() {
            if (pouch == null) return
            stolen += Stolen(pouch, 1)
            if (double) stolen += Stolen(coins, pouchCoins(random, row))
        }

        if (pouch != null && pouchWeight != null) {
            val total = pouchWeight + row.loot.sumOf { it.t3 }
            if (random.of(0, total - 1) < pouchWeight) {
                takePouch()
            } else {
                roll(random, row.loot)?.let { stolen += Stolen(it.obj, it.count * multiplier) }
            }
        } else {
            takePouch()
            roll(random, row.loot)?.let { stolen += Stolen(it.obj, it.count * multiplier) }
        }
        for ((obj, count) in row.always) stolen += Stolen(obj, count * multiplier)
        for ((obj, oneIn) in row.tertiary) {
            if (random.of(0, oneIn - 1) == 0) stolen += Stolen(obj, 1)
        }
        return merge(stolen)
    }

    fun pouchCoins(random: GameRandom, row: FriendscapeThievingPickpocketRow): Int {
        val min = row.pouchMin ?: 0
        return random.of(min, row.pouchMax ?: min)
    }

    fun merge(stolen: List<Stolen>): List<Stolen> =
        stolen
            .groupBy { it.obj.id }
            .map { (_, same) -> Stolen(same.first().obj, same.sumOf { it.count }) }
}
