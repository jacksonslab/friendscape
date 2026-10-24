package org.rsmod.content.other.ironman

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.game.entity.Player

internal fun Player.giveIronmanArmour(pieces: List<String>): Boolean = invTransaction(inv) {
    val inventory = select(inv)
    for (piece in pieces) insert { into = inventory; obj = piece.asRSCM(); strictCount = 1 }
}.success
