package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Singleton
class CargoService {
    fun hold(access: ProtectedAccess): Inventory = access.inv(CARGO_HOLD_INV)

    fun carriedCrate(player: Player): InvObj? = player.inv.firstOrNull { it != null && it.id in CourierTasks.crateIds }

    fun refreshCarrying(player: Player) {
        player.carryingCargo = if (carriedCrate(player) != null) 1 else 0
    }

    /**
     * Removes every courier crate from the player's hands and cargo hold, as happens on live when a
     * boat is relocated, and hands each crate back to its task so it can be collected again.
     */
    fun confiscate(access: ProtectedAccess): Boolean {
        var removed = false
        for (inv in listOf(access.player.inv, hold(access))) {
            for (crateId in CourierTasks.crateIds) {
                val type = RSCM.getReverseMapping(RSCMType.OBJ, crateId)
                val count = access.invTotal(inv, type)
                if (count == 0) {
                    continue
                }
                if (access.invDel(inv, type, count).success) {
                    returnToTasks(access.player, crateId, count)
                    removed = true
                }
            }
        }
        refreshCarrying(access.player)
        return removed
    }

    private fun returnToTasks(player: Player, crateId: Int, count: Int) {
        var remaining = count
        for (slot in TaskSlots.active(player)) {
            if (remaining == 0) {
                break
            }
            val task = slot.task ?: continue
            if (task.crate.id != crateId) {
                continue
            }
            val returned = minOf(slot.taken - slot.delivered, remaining)
            if (returned > 0) {
                TaskSlots.set(player, slot.copy(taken = slot.taken - returned))
                remaining -= returned
            }
        }
    }

    companion object {
        const val CARGO_HOLD_INV = "inv.sailing_boat_1_cargohold"
    }
}
