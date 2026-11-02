package org.rsmod.content.skills.thieving.pack

import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.dbtables.DBRowBuilder

internal class Drop(val obj: String, val min: Int, val max: Int, val weight: Int)

internal fun drop(obj: String, weight: Int = 1, min: Int = 1, max: Int = min) =
    Drop("obj.$obj", min, max, weight)

internal fun DBRowBuilder.dropColumn(column: Int, drops: List<Drop>) {
    if (drops.isEmpty()) return
    column(
        column,
        drops.flatMap { listOf(ConstantProvider.getMapping(it.obj), it.min, it.max, it.weight) },
    )
}

internal fun DBRowBuilder.npcColumn(column: Int, npcs: List<String>) {
    if (npcs.isEmpty()) return
    columnRSCM(column, npcs.map { "npc.$it" })
}
