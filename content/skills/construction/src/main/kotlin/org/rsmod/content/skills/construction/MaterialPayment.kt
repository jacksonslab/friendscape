package org.rsmod.content.skills.construction

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

object MaterialPayment {
    /**
     * The objs, and how many of each, that pay for [materials] out of what [held] says the player
     * has, or null when they cannot afford all of it. A material that can be paid in several objs
     * (nails) draws from them in [materialOptions] order.
     */
    fun plan(materials: List<Pair<String, Int>>, held: (String) -> Int): Map<String, Int>? {
        val spent = LinkedHashMap<String, Int>()
        for ((material, count) in materials) {
            var remaining = count
            for (option in materialOptions(material)) {
                if (remaining <= 0) {
                    break
                }
                val available = held(option) - (spent[option] ?: 0)
                if (available <= 0) {
                    continue
                }
                val take = minOf(available, remaining)
                spent[option] = (spent[option] ?: 0) + take
                remaining -= take
            }
            if (remaining > 0) {
                return null
            }
        }
        return spent
    }

    fun canAfford(inv: Inventory, materials: List<Pair<String, Int>>): Boolean =
        plan(materials, inv::count) != null

    /** Takes every material in one transaction: either all of it goes, or none of it does. */
    fun pay(player: Player, inv: Inventory, materials: List<Pair<String, Int>>): Boolean {
        val spend = plan(materials, inv::count) ?: return false
        if (spend.isEmpty()) {
            return true
        }
        val result =
            player.invTransaction(inv) {
                val from = select(inv)
                for ((obj, count) in spend) {
                    delete {
                        this.from = from
                        this.obj = obj.asRSCM(RSCMType.OBJ)
                        this.strictCount = count
                    }
                }
            }
        return result.success
    }
}
