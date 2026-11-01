package org.rsmod.content.skills.woodcutting

import org.rsmod.api.stats.xpmod.StatXpMod
import org.rsmod.content.skills.woodcutting.configs.WoodcuttingParams
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

class WoodcuttingOutfitXpModifier : StatXpMod("stat.woodcutting") {
    override fun Player.modifier(): Double {
        val pieces = worn.filterNotNull().map { getInvObj(it).param(WoodcuttingParams.outfit_bonus) }
        return outfitBonus(pieces)
    }

    companion object {
        internal fun outfitBonus(pieces: List<Int>): Double =
            (pieces.sum() + if (pieces.count { it > 0 } == 4) 5 else 0) / 1000.0
    }
}
