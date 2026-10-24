package org.rsmod.content.areas.tutorialisland.progression

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.protect.ProtectedAccess

internal fun ProtectedAccess.tutorialExchange(
    take: List<Pair<String, Int>>,
    give: List<Pair<String, Int>>,
): Boolean = player.invTransaction(inv) {
    val inventory = select(inv)
    for ((item, count) in take) {
        delete { from = inventory; obj = item.asRSCM(); strictCount = count }
    }
    for ((item, count) in give) {
        insert { into = inventory; obj = item.asRSCM(); strictCount = count }
    }
}.success

internal fun ProtectedAccess.replaceTutorialKit(kit: List<Pair<String, Int>>): Boolean =
    player.invTransaction(inv, worn) {
        val inventory = select(inv)
        val equipment = select(worn)
        val bankInventory = select(bank)
        for (item in inv.filterNotNull()) {
            delete { from = inventory; obj = item.id; strictCount = item.count }
        }
        for (item in worn.filterNotNull()) {
            delete { from = equipment; obj = item.id; strictCount = item.count }
        }
        for ((item, count) in kit) {
            insert { into = inventory; obj = item.asRSCM(); strictCount = count }
        }
        val coins = invTotal(bank, "obj.coins")
        if (player.isUltimateIronman && coins > 0) {
            delete { from = bankInventory; obj = "obj.coins".asRSCM(); strictCount = minOf(coins, 25) }
        } else if (!player.isUltimateIronman && coins < 25) {
            insert { into = bankInventory; obj = "obj.coins".asRSCM(); strictCount = 25 - coins }
        }
    }.success
