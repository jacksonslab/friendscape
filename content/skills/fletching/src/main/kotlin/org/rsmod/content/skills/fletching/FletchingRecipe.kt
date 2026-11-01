package org.rsmod.content.skills.fletching

import dev.openrune.types.ItemServerType
import dev.openrune.types.StatType
import org.rsmod.api.table.Tuple2
import org.rsmod.content.skills.Material
import org.rsmod.game.inv.Inventory

/**
 * One fletching recipe, normalised from whichever generated row type produced it.
 *
 * All five tables share this shape, which is what lets a single queued action drive every flow
 * instead of one loop per family.
 */
data class FletchingRecipe(
    val inputs: List<Material>,
    val output: ItemServerType,
    val outputAmount: Int,
    val statReq: List<Tuple2<StatType, Int>>,
    val xp: Double,
    val ticks: Int,
    val category: String,
    /** Held, never consumed, so deliberately not one of [inputs]. Null when none is needed. */
    val tool: String? = null,
)

fun FletchingRecipe.hasMaterials(inv: Inventory): Boolean =
    inputs.all { inv.count(it.obj.internalName) >= if (isAmmunitionBatch()) 1 else it.count }

fun FletchingRecipe.maxProducible(inv: Inventory): Int {
    if (isAmmunitionBatch()) {
        val available = inputs.minOf { inv.count(it.obj.internalName) }
        return if (available <= 0) 0 else (available - 1) / outputAmount + 1
    }
    return inputs.minOfOrNull { inv.count(it.obj.internalName) / it.count } ?: 0
}

internal fun FletchingRecipe.isAmmunitionBatch(): Boolean =
    outputAmount in 10..15 && output.stackable && inputs.size == 2 &&
        inputs.all { it.count == outputAmount && it.obj.stackable }

internal fun FletchingRecipe.availableBatch(inv: Inventory): FletchingRecipe {
    if (!isAmmunitionBatch()) return this
    val amount = minOf(outputAmount, inputs.minOf { inv.count(it.obj.internalName) })
    if (amount <= 0 || amount == outputAmount) return this
    return copy(
        inputs = inputs.map { Material(it.internal, amount) },
        outputAmount = amount,
        xp = xp * amount / outputAmount,
    )
}

/**
 * The tool is held rather than consumed, so it is a presence check and never limits how many can be
 * made - which is why it is not folded into [maxProducible].
 */
fun FletchingRecipe.hasTool(inv: Inventory): Boolean {
    val tool = tool ?: return true
    return FletchingDefinitions.toolVariants(tool).any { inv.count(it) > 0 }
}
