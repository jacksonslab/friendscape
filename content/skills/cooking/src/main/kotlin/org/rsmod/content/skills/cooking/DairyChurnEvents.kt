package org.rsmod.content.skills.cooking

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.table.cooking.CookingChurningRow
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DairyChurnEvents : PluginScript() {

    private val recipes = CookingChurningRow.all()

    override fun ScriptContext.startup() {
        val dairy = recipes.map { it.input.internalName }.distinct()
        for (churn in CHURNS) {
            onOpLoc1(churn) { openChurnMenu() }
            for (item in dairy) {
                onOpLocU(churn, item) { openChurnMenu() }
            }
        }
    }

    private suspend fun ProtectedAccess.openChurnMenu() {
        val products =
            recipes
                .filter { player.cookingLvl >= it.level && inv.contains(it.input.internalName) }
                .groupBy { it.product.internalName }
        if (products.isEmpty()) {
            mes("You need some milk, cream or butter to use the churn.")
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                verb = "churn",
                entries = products.map { (product, options) ->
                    SkillMultiEntry(product, listOf(Material(options.first().input.internalName)))
                },
            )
        ) { selection ->
            repeat(selection.amount) {
                val recipe = bestRecipe(selection.entry.internal) ?: return@repeat
                anim(churnAnim(recipe.ticks))
                delay(recipe.ticks)
                if (!churn(recipe)) return@repeat
                statAdvance("stat.cooking", recipe.xp / 10.0)
            }
        }
    }

    /** Uses the most-processed dairy on hand, which churns fastest. */
    private fun ProtectedAccess.bestRecipe(product: String): CookingChurningRow? =
        recipes
            .filter {
                it.product.internalName == product &&
                    player.cookingLvl >= it.level &&
                    inv.contains(it.input.internalName)
            }
            .minByOrNull { it.ticks }

    private fun ProtectedAccess.churn(recipe: CookingChurningRow): Boolean =
        player.invTransaction(inv) {
            val inventory = select(inv)
            delete { from = inventory; obj = recipe.input.id; strictCount = 1 }
            for (item in recipe.output) {
                insert { into = inventory; obj = item.id; strictCount = 1 }
            }
        }.success

    private fun churnAnim(ticks: Int): String =
        when {
            ticks >= 26 -> "seq.player_churns_milk_long"
            ticks >= 19 -> "seq.player_churns_milk_medium"
            else -> "seq.player_churns_milk_short"
        }

    private val CookingChurningRow.level get() = statReq.first().t1
    private val CookingChurningRow.product get() = output.first()

    private companion object {
        val CHURNS =
            listOf(
                "loc.dairy_churn",
                "loc.dairy_churn_metal",
                "loc.kr_sin_dairy_churn",
                "loc.kr_sin_dairy_churn_north",
            )
    }
}
