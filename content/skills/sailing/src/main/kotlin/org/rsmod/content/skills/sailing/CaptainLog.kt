package org.rsmod.content.skills.sailing

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.sailing.SailingChartingCoreRow
import org.rsmod.map.CoordGrid

internal object CaptainLog {
    suspend fun open(access: ProtectedAccess, coords: CoordGrid = access.player.coords) = with(access) {
        SeaCharting.migrateLandmarks(player)
        val completed = SeaCharting.ALL.count { SeaCharting.isComplete(player, it) }
        when (choice3(
            "Nearby uncharted spyglass views", 0,
            "Browse charts by ocean", 1,
            "Close the log", 2,
            title = "Sea charting ($completed/${SeaCharting.ALL.size})",
        )) {
            0 -> {
                val nearby = SeaCharting.OBSERVATIONS
                    .filter { it.row.coords.level == coords.level && !SeaCharting.isComplete(player, it.core) }
                    .sortedBy { it.row.coords.chebyshevDistance(coords) }
                    .take(NEARBY_VIEWS)
                    .map { it.core }
                if (nearby.isEmpty()) {
                    mes("You've charted all supported spyglass views on this plane.")
                    return@with
                }
                val task = choose(nearby.map { label(it) }, nearby, title = "Nearest uncharted views") ?: return@with
                describe(this, task)
            }
            1 -> browse(this)
        }
    }

    private suspend fun browse(access: ProtectedAccess) = with(access) {
        val oceans = SeaCharting.ALL.groupBy { it.sailingSea.ocean?.name ?: it.sailingSea.name }.toSortedMap()
        val ocean = choose(oceans.keys.toList(), oceans.keys.toList(), title = "Choose an ocean") ?: return@with
        val seas = oceans.getValue(ocean).groupBy { it.sailingSea.name }.toSortedMap()
        val sea = choose(
            seas.map { (name, tasks) -> "$name (${tasks.count { SeaCharting.isComplete(player, it) }}/${tasks.size})" },
            seas.keys.toList(),
            title = ocean,
        ) ?: return@with
        val tasks = seas.getValue(sea)
        val task = choose(
            tasks.map { "${if (SeaCharting.isComplete(player, it)) "Done" else "To chart"}: ${label(it)}" },
            tasks,
            title = sea,
        ) ?: return@with
        describe(this, task)
    }

    private suspend fun describe(access: ProtectedAccess, task: SailingChartingCoreRow) = with(access) {
        val observation = SeaCharting.OBSERVATIONS.firstOrNull { it.core.uniqueId == task.uniqueId }
        mesbox(task.hint)
        if (SeaCharting.isComplete(player, task)) {
            mes("You've already charted this discovery.")
        } else {
            mes("Reward: ${SeaCharting.xp(task)} Sailing XP before experience modifiers.")
            if (observation != null) {
                val coords = observation.row.coords
                mes("Sailing level ${observation.row.level}. Observation point: ${coords.x}, ${coords.z}. Use your spyglass within ${observation.row.radius} tiles.")
            } else {
                mes("Find the landmark and choose Chart to record it.")
            }
        }
    }

    private fun label(task: SailingChartingCoreRow): String = task.hint
        .removePrefix("Use your spyglass to get a good view of ")
        .removePrefix("Use your spyglass to ")
        .removePrefix("Find ")
        .take(MAX_LABEL_LENGTH)

    fun tasks(access: ProtectedAccess) = with(access) {
        val active = TaskSlots.active(player)
        if (active.isEmpty()) {
            mes("You don't have any port tasks.")
            return@with
        }
        for (slot in active) {
            val task = slot.task ?: continue
            val source = Ports.byDock(task.cargoPort)?.name ?: "the collection port"
            val destination = Ports.byDock(task.destination)?.name ?: "the destination port"
            mes("${task.name}: ${slot.delivered}/${task.crates} delivered. Collect at $source; deliver to $destination.")
        }
    }

    private const val NEARBY_VIEWS = 6
    private const val MAX_LABEL_LENGTH = 65
}
