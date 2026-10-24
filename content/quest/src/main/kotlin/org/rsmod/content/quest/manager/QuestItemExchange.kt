package org.rsmod.content.quest.manager

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess

internal fun ProtectedAccess.questItemExchange(
    take: List<Pair<String, Int>>,
    give: List<Pair<String, Int>>,
): Boolean = player.invTransaction(inv) {
    val inventory = select(inv)
    for ((item, count) in take) {
        delete {
            from = inventory
            obj = item.asRSCM()
            strictCount = count
        }
    }
    for ((item, count) in give) {
        insert {
            into = inventory
            obj = item.asRSCM()
            strictCount = count
        }
    }
}.success
