package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.DBTableType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.enums.EnumTypeMap
import dev.openrune.types.enums.`enum`
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.sailing.FriendscapeSailingObservationsRow
import org.rsmod.api.table.sailing.SailingChartingCoreRow
import org.rsmod.api.table.sailing.SailingChartingGenericRow
import org.rsmod.api.table.sailing.SailingChartingSpyglassRow
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class Observation(val row: FriendscapeSailingObservationsRow, val core: SailingChartingCoreRow) {
    fun contains(coords: CoordGrid): Boolean =
        coords.level == row.coords.level && coords.chebyshevDistance(row.coords) <= row.radius
}

internal object SeaCharting {
    val GENERIC: Map<Int, SailingChartingCoreRow> by lazy {
        val rows = SailingChartingGenericRow.all().associateBy {
            RSCM.getReverseMapping(RSCMType.DBROW, it.rowId).removePrefix("dbrow.")
        }
        Charts.GENERIC.mapNotNull { loc ->
            val name = RSCM.getReverseMapping(RSCMType.LOC, loc).removePrefix("loc.")
            val rowName = if (name == "sailing_charting_generic_rowboat_strait_of_khazard") {
                "sailing_charting_generic_rowboat_port_khazard"
            } else {
                name
            }
            rows[rowName]?.sailingChartingCore?.let { loc to it }
        }.toMap()
    }

    val OBSERVATIONS: List<Observation> by lazy {
        val cores = SailingChartingSpyglassRow.all().map { it.sailingChartingCore }.associateBy { it.uniqueId }
        FriendscapeSailingObservationsRow.all().map { Observation(it, cores.getValue(it.uniqueId)) }
    }

    val ALL: List<SailingChartingCoreRow> by lazy {
        (GENERIC.values + OBSERVATIONS.map { it.core }).distinctBy { it.uniqueId }.sortedBy { it.uniqueId }
    }

    fun complete(player: Player, core: SailingChartingCoreRow): Boolean {
        val id = core.uniqueId
        val word = player.vars[word(id)]
        if (isSet(word, id)) return false
        VarPlayerIntMapSetter.set(player, word(id), word or mask(id))
        return true
    }

    fun isComplete(player: Player, core: SailingChartingCoreRow): Boolean =
        isSet(player.vars[word(core.uniqueId)], core.uniqueId)

    fun migrateLandmarks(player: Player) {
        for ((loc, core) in GENERIC) {
            if (Charts.isCharted(player, loc)) complete(player, core)
        }
    }

    fun xp(core: SailingChartingCoreRow): Double =
        FIRST_XP.getValue(checkNotNull(ServerCacheManager.getDbtable(core.chartingType))) / XP_SCALE

    fun word(id: Int): String {
        require(id in 0 until CAPACITY)
        return "varp.sailing_charting_completion_${id / WORD_BITS}"
    }

    fun mask(id: Int): Int {
        require(id in 0 until CAPACITY)
        return 1 shl (id % WORD_BITS)
    }

    fun isSet(word: Int, id: Int): Boolean = word and mask(id) != 0

    private const val WORD_BITS = 32
    private const val CAPACITY = 12 * WORD_BITS
    private const val XP_SCALE = 10.0
    private val FIRST_XP: EnumTypeMap<DBTableType, Int> by lazy { enum("friendscape_charting_first_xp") }
}
