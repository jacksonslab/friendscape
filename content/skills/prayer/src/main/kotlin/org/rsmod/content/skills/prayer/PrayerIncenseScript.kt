package org.rsmod.content.skills.prayer

import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PrayerIncenseScript @Inject constructor(private val locs: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in 5..8) {
            for (symbol in listOf("loc.poh_torch_$tier", "loc.poh_torch_${tier}_lit")) {
                onOpLoc1(symbol) { light(it.vis, tier) }
                onOpLocU(symbol, "obj.tinderbox") { light(it.vis, tier) }
                onOpLocU(symbol, "obj.marentill") { light(it.vis, tier) }
            }
        }
    }

    private suspend fun ProtectedAccess.light(loc: BoundLocInfo, tier: Int) {
        arriveDelay()
        val level = stat("stat.firemaking")
        if (level < 30) {
            mes("You need a Firemaking level of 30 to light incense burners.")
            return
        }
        if ("obj.tinderbox" !in inv || "obj.marentill" !in inv) {
            mes("You need a tinderbox and a clean marrentill to light an incense burner.")
            return
        }
        val current = locs.findExact(loc.coords, loc.shape) ?: return
        if (current.id != loc.id) return
        val unlit = LocInfo(loc.layer, loc.coords, LocEntity(RSCM.getRSCM("loc.poh_torch_$tier"), loc.shape.id, loc.angle.id))
        val lit = LocInfo(loc.layer, loc.coords, LocEntity(RSCM.getRSCM("loc.poh_torch_${tier}_lit"), loc.shape.id, loc.angle.id))
        if (invDel(inv, "obj.marentill", 1).failure) return
        val duration = burnTicks(level, random.of(level))
        if (!locs.add(lit, duration) { locs.add(unlit, Int.MAX_VALUE) }) {
            invAdd(inv, "obj.marentill", 1)
            return
        }
        anim("seq.human_createfire")
        mes("You light the incense burner.")
    }

    companion object {
        val LIT_TYPES: Set<Int> by lazy { (5..8).map { RSCM.getRSCM("loc.poh_torch_${it}_lit") }.toSet() }

        fun burnTicks(level: Int, roll: Int): Int {
            require(level in 1..255 && roll in 0 until level)
            return 200 + level + roll
        }

        fun litCount(locs: LocRepository, altar: BoundLocInfo): Int =
            locs.findAll(ZoneKey.from(altar.coords)).count { it.id in LIT_TYPES }.coerceAtMost(2)
    }
}
