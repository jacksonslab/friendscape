package org.rsmod.content.skills.fletching

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.slayer.SlayerUnlockRow

private val BROADER_FLETCHING by lazy {
    SlayerUnlockRow.all().single { it.name.equals("Broader Fletching", ignoreCase = true) }
}

internal fun ProtectedAccess.hasFletchingUnlock(recipe: FletchingRecipe): Boolean {
    val required = recipe.inputs.any {
        it.internal == "obj.slayer_broad_arrowhead" ||
            it.internal == "obj.slayer_broad_bolt_unfinished"
    }
    if (!required) return true
    val bit = BROADER_FLETCHING.bit
    val varp = if (bit < 32) "varp.slayer_rewards_unlocks" else "varp.slayer_rewards_unlocks1"
    if (vars[varp] and (1 shl (bit % 32)) != 0) return true
    mes("You need to unlock Broader Fletching from a Slayer master to make that.")
    return false
}
