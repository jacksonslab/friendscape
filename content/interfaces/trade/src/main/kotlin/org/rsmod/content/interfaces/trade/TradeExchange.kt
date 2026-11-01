package org.rsmod.content.interfaces.trade

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.invtx.transfer
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

internal const val OFFER_INV = "inv.tradeoffer"

internal val Player.tradeOffer: Inventory
    get() = invMap.getOrPut(OFFER_INV)

/**
 * Every item move in a trade. Each call is one transaction, so a move either lands whole or leaves
 * both inventories untouched.
 */
internal object TradeExchange {
    /** Moves up to [count] of the obj in [slot] between [from] and [into]; returns how many moved. */
    fun move(player: Player, from: Inventory, into: Inventory, slot: Int, count: Int): Int {
        val obj = from[slot] ?: return 0
        if (count <= 0) {
            return 0
        }
        val slots = listOf(slot) + from.indices.filter { it != slot && from[it]?.id == obj.id }
        var moved = 0
        val result =
            player.invTransaction(from, into) {
                val source = select(from)
                val target = select(into)
                for (index in slots) {
                    val remaining = count - moved
                    if (remaining <= 0) {
                        break
                    }
                    val stack = from[index] ?: continue
                    val amount = minOf(remaining, stack.count)
                    transfer(from = source, fromSlot = index, count = amount, into = target)
                    moved += amount
                }
            }
        return if (result.success) moved else 0
    }

    /** Gives each player the other's offer in one transaction, or changes nothing. */
    fun complete(first: Player, second: Player, commit: Boolean = true): Boolean {
        val firstOffer = first.tradeOffer
        val secondOffer = second.tradeOffer
        return first.invTransaction(first.inv, null, autoCommit = commit) {
            val firstInv = select(first.inv)
            val secondInv = select(second.inv)
            val firstSource = select(firstOffer)
            val secondSource = select(secondOffer)
            for (slot in firstOffer.indices) {
                val obj = firstOffer[slot] ?: continue
                transfer(from = firstSource, fromSlot = slot, count = obj.count, into = secondInv)
            }
            for (slot in secondOffer.indices) {
                val obj = secondOffer[slot] ?: continue
                transfer(from = secondSource, fromSlot = slot, count = obj.count, into = firstInv)
            }
        }.success
    }

    /** Puts a player's whole offer back in their inventory, or their bank if it no longer fits. */
    fun returnOffer(player: Player) {
        val offer = player.tradeOffer
        for (slot in offer.indices) {
            val obj = offer[slot] ?: continue
            if (move(player, offer, player.inv, slot, obj.count) == 0) {
                move(player, offer, player.invMap.getOrPut(BANK_INV), slot, obj.count)
            }
        }
    }

    private const val BANK_INV = "inv.bank"
}
