package org.rsmod.content.generic.locs.webs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WebScript @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((web, slashed) in SLASHABLE_WEBS) {
            val type = ServerCacheManager.getObject(web.asRSCM(RSCMType.LOC)) ?: continue
            val op = (1..5).firstOrNull { type.actions.getOpOrNull(it - 1).equals(SLASH, true) }
            when (op) {
                1 -> onOpLoc1(web) { slash(it.loc, slashed) }
                2 -> onOpLoc2(web) { slash(it.loc, slashed) }
                3 -> onOpLoc3(web) { slash(it.loc, slashed) }
                4 -> onOpLoc4(web) { slash(it.loc, slashed) }
                5 -> onOpLoc5(web) { slash(it.loc, slashed) }
            }
        }
    }

    private suspend fun ProtectedAccess.slash(web: BoundLocInfo, slashed: String) {
        val anim = bladeAnim()
        if (anim == null) {
            mes("Only a sharp blade can cut through this sticky web.")
            return
        }
        anim(anim)
        delay(1)
        if (random.of(maxExclusive = 2) == 0) {
            mes("You fail to cut through it.")
            return
        }
        mes("You slash the web apart.")
        locRepo.del(web, RESPAWN_TICKS)
        locRepo.add(web.coords, slashed, RESPAWN_TICKS, web.angle, web.shape)
    }

    private fun ProtectedAccess.bladeAnim(): String? {
        val weapon = player.worn[Wearpos.RightHand.slot]
        val weaponType = weapon?.let { ServerCacheManager.getItem(it.id) }
        if (weaponType != null && (weaponType.paramOrNull(params.attack_slash) ?: 0) > 0) {
            return WEAPON_ANIM
        }
        if (inv.any { it.isType(KNIFE) }) {
            return KNIFE_ANIM
        }
        return null
    }

    private companion object {
        const val SLASH = "Slash"
        const val KNIFE = "obj.knife"
        const val WEAPON_ANIM = "seq.human_sword_slash"
        const val KNIFE_ANIM = "seq.human_knife_slash"
        const val RESPAWN_TICKS = 100
        val SLASHABLE_WEBS =
            listOf(
                "loc.bigweb_slashable" to "loc.bigweb_slashed",
                "loc.bigweb_slashable_shape1" to "loc.bigweb_slashed_shape1",
                "loc.webwall" to "loc.sliced_web",
                "loc.mm2_web" to "loc.mm2_sliced_web",
                "loc.sarachnis_web_peek" to "loc.sarachnis_web_peek_slashed",
            )
    }
}
