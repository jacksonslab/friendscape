package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player

@Singleton
class BountyRewards
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) {
    /**
     * Rolls a kill of [target] for the first bounty that still needs its part. The part goes to the
     * inventory, or under the player when it is full.
     */
    fun rollKill(player: Player, target: Int): Boolean {
        val (_, bounty) = BountyTasks.collecting(player, target) ?: return false
        if (random.of(1, bounty.rarity) != 1) {
            return false
        }
        if (!player.invAddOrDrop(objRepo, bounty.item.internalName, coords = player.coords)) {
            player.mes("Your inventory is full, so the ${bounty.item.name.lowercase()} falls at your feet.")
        }
        return true
    }

    /** Hands in every held bounty whose parts are all in the inventory; returns how many paid out. */
    fun claim(access: ProtectedAccess): Int {
        val player = access.player
        var claimed = 0
        for (slot in TaskSlots.active(player)) {
            val bounty = slot.bounty ?: continue
            if (player.inv.count(bounty.item.internalName) < bounty.amount) {
                continue
            }
            val paid =
                player.invTransaction(player.inv) {
                    val inv = select(player.inv)
                    delete(inv, bounty.item.id, bounty.amount)
                    add(inv, COINS, bounty.xp)
                }
            if (!paid.success) {
                access.mes("You don't have room for the reward from the ${bounty.name}.")
                continue
            }
            TaskSlots.clear(player, slot.index)
            access.statAdvance(STAT_SAILING, bounty.xp * xpMods.get(player, STAT_SAILING))
            player.portTasksCompleted++
            access.mes("You hand in the ${bounty.name} and receive ${bounty.xp} coins.")
            if (random.of(1, SHARK_PAINT_CHANCE) == 1) {
                access.invAddOrDrop(objRepo, SHARK_PAINT)
                access.mes("The port master also hands you a pot of shark paint.")
            }
            claimed++
        }
        return claimed
    }

    private companion object {
        private const val SHARK_PAINT = "obj.sailing_paint_shark"
        private const val SHARK_PAINT_CHANCE = 36
        private val COINS: Int by lazy { RSCM.getRSCM("obj.coins") }
    }
}
