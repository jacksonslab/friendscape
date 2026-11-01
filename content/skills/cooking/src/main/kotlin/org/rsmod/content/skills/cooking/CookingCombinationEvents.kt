package org.rsmod.content.skills.cooking

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.table.cooking.CookingCombinationsRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CookingCombinationEvents : PluginScript() {

    override fun ScriptContext.startup() {
        for (combination in CookingCombinationsRow.all()) {
            onOpHeldU(combination.input[0], combination.input[1]) { combine(combination) }
        }
    }

    private fun ProtectedAccess.combine(combination: CookingCombinationsRow) {
        val level = combination.statReq.first().t1
        if (player.cookingLvl < level) {
            mes("You need a Cooking level of $level to do that.")
            return
        }
        if (combination.needsKnife == true && !inv.contains("obj.knife")) {
            mes("You need a knife to do that.")
            return
        }
        if (!combineItems(combination)) {
            return
        }
        if (combination.xp > 0) {
            statAdvance("stat.cooking", combination.xp / 10.0)
        }
        mes("You prepare the ${combination.output[0].name.lowercase()}.")
    }

    private fun ProtectedAccess.combineItems(combination: CookingCombinationsRow): Boolean =
        player.invTransaction(inv) {
            val inventory = select(inv)
            for (item in combination.input) {
                delete { from = inventory; obj = item.id; strictCount = 1 }
            }
            for (item in combination.output) {
                insert { into = inventory; obj = item.id; strictCount = 1 }
            }
        }.success
}
