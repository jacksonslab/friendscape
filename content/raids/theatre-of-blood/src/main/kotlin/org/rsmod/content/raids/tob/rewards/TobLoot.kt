package org.rsmod.content.raids.tob.rewards

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Player

/**
 * A raider's unclaimed Monumental chest loot. It is written the moment Verzik dies, so leaving the
 * vault, logging out or a server restart never loses it; both the vault chest and the Ver Sinhaza
 * rewards chest claim from here.
 */
object TobLoot {
    private val UNCLAIMED = AttributeKey<MutableMap<String, Int>>(persistenceKey = "tob_unclaimed_loot")
    private const val SHOULD_HAVE_LOOT = "varbit.tob_should_have_loot"

    fun unclaimed(player: Player): Map<String, Int> = player.attr[UNCLAIMED].orEmpty()

    fun hasUnclaimed(player: Player): Boolean = unclaimed(player).isNotEmpty()

    fun store(player: Player, loot: List<Pair<String, Int>>) {
        if (loot.isEmpty()) return
        val stored = player.attr.getOrPut(UNCLAIMED) { mutableMapOf() }
        for ((obj, count) in loot) {
            if (count <= 0) continue
            val current = stored[obj] ?: 0
            stored[obj] = (current.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        VarPlayerIntMapSetter.set(player, SHOULD_HAVE_LOOT, 1)
    }

    sealed class Claim {
        data object Empty : Claim()

        data class NoRoom(val slotsNeeded: Int) : Claim()

        data class Claimed(val loot: Map<String, Int>) : Claim()
    }

    fun ProtectedAccess.claimLoot(): Claim {
        val loot = unclaimed(player).toMap()
        if (loot.isEmpty()) {
            VarPlayerIntMapSetter.set(player, SHOULD_HAVE_LOOT, 0)
            return Claim.Empty
        }
        val result =
            player.invTransaction(player.inv) {
                val target = select(player.inv)
                for ((obj, count) in loot) {
                    add(target, obj.asRSCM(RSCMType.OBJ), count)
                }
            }
        if (result.failure) {
            return Claim.NoRoom(loot.size)
        }
        player.attr.remove(UNCLAIMED)
        VarPlayerIntMapSetter.set(player, SHOULD_HAVE_LOOT, 0)
        for ((obj, count) in loot) {
            CollectionLog.grant(player, obj, count)
        }
        return Claim.Claimed(loot)
    }
}
