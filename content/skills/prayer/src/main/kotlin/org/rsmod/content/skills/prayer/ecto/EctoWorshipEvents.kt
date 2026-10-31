package org.rsmod.content.skills.prayer.ecto

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.prayer.items.ZealotRobes.shouldConsume
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class EctoWorshipEvents : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.ahoy_ectofuntus") { worshipEctofuntus() }
    }

    private suspend fun ProtectedAccess.worshipEctofuntus() {
        if (!inv.contains("obj.bucket_ectoplasm")) {
            doubleobjbox(
                "obj.bucket_ectoplasm",
                "obj.pot_bonemeal",
                "You need a bucket of slime and some bonemeal to do this.",
            )
            return
        }
        val recipe = ECTO_RECIPES.firstOrNull { inv.contains(it.bonemeal) }
        if (recipe == null) {
            doubleobjbox(
                "obj.bucket_ectoplasm",
                "obj.pot_bonemeal",
                "You need a bucket of slime and some bonemeal to do this.",
            )
            return
        }

        if (statBase("stat.prayer") < recipe.prayerLevel) {
            mes("You need a Prayer level of ${recipe.prayerLevel} to use this bonemeal.")
            return
        }
        val tokenVar = checkNotNull(ServerCacheManager.getVarbit("varbit.ecto_tokens".asRSCM(RSCMType.VARBIT)))
        val maxTokens = ((1L shl (tokenVar.endBit - tokenVar.startBit + 1)) - 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (ectoTokens > maxTokens - 5) {
            mes("Claim your ecto-tokens before worshipping again.")
            return
        }

        val shouldConsumeInputs = player.shouldConsume(random)
        if (shouldConsumeInputs) {
            val offered = player.invTransaction(inv) {
                val target = select(inv)
                delete(target, "obj.bucket_ectoplasm".asRSCM(RSCMType.OBJ), 1)
                delete(target, recipe.bonemeal.asRSCM(RSCMType.OBJ), 1)
                add(target, "obj.bucket_empty".asRSCM(RSCMType.OBJ), 1)
                add(target, "obj.pot_empty".asRSCM(RSCMType.OBJ), 1)
            }
            if (!offered.success) return
        }

        anim("seq.ahoy_prayer")
        statAdvance("stat.prayer", recipe.xp)
        ectoTokens += 5
    }
}
