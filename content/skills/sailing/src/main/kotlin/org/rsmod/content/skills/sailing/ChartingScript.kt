package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ChartingScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for (loc in Charts.GENERIC) {
            onOpLoc1(RSCM.getReverseMapping(RSCMType.LOC, loc)) { chart(loc) }
        }
    }

    private fun ProtectedAccess.chart(loc: Int) {
        val name = ServerCacheManager.getObject(loc)?.name?.lowercase() ?: "landmark"
        val core = SeaCharting.GENERIC[loc]
        if (Charts.isCharted(player, loc)) {
            if (core != null) SeaCharting.complete(player, core)
            mes("You've already charted this $name.")
            return
        }
        val xp = (core?.let(SeaCharting::xp) ?: GENERIC_XP) * xpMods.get(player, STAT_SAILING)
        Charts.markCharted(player, loc)
        if (core == null || SeaCharting.complete(player, core)) {
            statAdvance(STAT_SAILING, xp)
        }
        mes("You chart the $name in your captain's log. (${Charts.chartedCount(player)}/${Charts.GENERIC.size})")
    }

    private companion object {
        private const val GENERIC_XP = 35.0
    }
}

/**
 * Generic sea-charting landmarks, identified by their position among the cache's
 * `sailing_charting_generic_*` locs. Completion is a bitset over [VARPS] server varps.
 */
internal object Charts {
    private const val VARPS = 12
    private const val BITS_PER_VARP = 32
    private const val PREFIX = "loc.sailing_charting_generic_"

    val GENERIC: List<Int> by lazy {
        ServerCacheManager.getObjects().keys
            .filter { runCatching { RSCM.getReverseMapping(RSCMType.LOC, it) }.getOrNull()?.startsWith(PREFIX) == true }
            .filter { ServerCacheManager.getObject(it)?.actions?.ops?.isNotEmpty() == true }
            .sorted()
            .take(VARPS * BITS_PER_VARP)
    }

    private val index by lazy { GENERIC.withIndex().associate { it.value to it.index } }

    fun isCharted(player: Player, loc: Int): Boolean {
        val bit = index[loc] ?: return false
        return player.vars[varp(bit)] and (1 shl (bit % BITS_PER_VARP)) != 0
    }

    fun markCharted(player: Player, loc: Int) {
        val bit = index[loc] ?: return
        val current = player.vars[varp(bit)]
        VarPlayerIntMapSetter.set(player, varp(bit), current or (1 shl (bit % BITS_PER_VARP)))
    }

    fun chartedCount(player: Player): Int =
        (0 until VARPS).sumOf { Integer.bitCount(player.vars["varp.friendscape_sailing_charted_$it"]) }

    private fun varp(bit: Int): String = "varp.friendscape_sailing_charted_${bit / BITS_PER_VARP}"
}
