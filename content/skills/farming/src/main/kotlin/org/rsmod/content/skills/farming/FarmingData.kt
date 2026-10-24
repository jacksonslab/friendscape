package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.farming.FarmingAreaRow
import org.rsmod.api.table.farming.FarmingCropRow
import org.rsmod.api.table.farming.FarmingPatchRow
import org.rsmod.map.CoordGrid

/**
 * [transmit] is the `farming_transmit_*` varbit the patch loc's multiloc reads, taken from the loc
 * itself so the table never has to repeat it.
 */
data class Patch(
    val loc: String,
    val kind: PatchKind,
    val varp: String,
    val transmit: String?,
    val area: Int,
    val farmer: String?,
    val farmerOp: Int,
    val label: String,
)

data class FarmingArea(
    val index: Int,
    val name: String,
    val regions: Set<Int>,
    val bounds: Pair<CoordGrid, CoordGrid>?,
) {
    fun contains(coords: CoordGrid): Boolean {
        if (bounds != null && coords.inside(bounds.first, bounds.second)) {
            return true
        }
        return regionOf(coords) in regions
    }

    private fun CoordGrid.inside(min: CoordGrid, max: CoordGrid): Boolean =
        level == min.level && x in min.x..max.x && z in min.z..max.z
}

fun regionOf(coords: CoordGrid): Int = ((coords.x shr 6) shl 8) or (coords.z shr 6)

object FarmingData {
    val crops: List<Crop> by lazy { FarmingCropRow.all().map(FarmingCropRow::toCrop) }

    private val cropsById: Map<Int, Crop> by lazy { crops.associateBy(Crop::id) }
    private val cropsByPlanted: Map<String, Crop> by lazy { crops.associateBy(Crop::planted) }

    val areas: List<FarmingArea> by lazy {
        FarmingAreaRow.all().mapIndexed { index, row ->
            FarmingArea(
                index = index + 1,
                name = row.name,
                regions = row.regions.toSet(),
                bounds = row.bounds.takeIf { it.size == 2 }?.let { it[0] to it[1] },
            )
        }
    }

    val patches: List<Patch> by lazy {
        val areaRows = FarmingAreaRow.all().map(FarmingAreaRow::rowId)
        FarmingPatchRow.all().map { row ->
            val loc = row.loc
            Patch(
                loc = RSCM.getReverseMapping(RSCMType.LOC, loc.id),
                kind = PatchKind.of(row.kind),
                varp = RSCM.getReverseMapping(RSCMType.VARP, row.varp),
                transmit = transmitVarbit(loc.id),
                area = areaRows.indexOf(row.area.rowId) + 1,
                farmer = row.farmer?.let { RSCM.getReverseMapping(RSCMType.NPC, it.id) },
                farmerOp = row.farmerOp ?: 0,
                label = label(RSCM.getReverseMapping(RSCMType.DBROW, row.rowId)),
            )
        }
    }

    fun crop(id: Int): Crop? = if (id == 0) null else cropsById[id]

    fun planted(obj: String): Crop? = cropsByPlanted[obj]

    fun area(index: Int): FarmingArea? = areas.getOrNull(index - 1)

    fun patchesIn(area: Int): List<Patch> = patches.filter { it.area == area }

    /** Boxes win over map squares: a box is how a farm claims part of a square another farm owns. */
    fun areaAt(coords: CoordGrid): FarmingArea? =
        areas.firstOrNull { it.bounds != null && it.contains(coords) && coords.level == it.bounds.first.level }
            ?: areas.firstOrNull { regionOf(coords) in it.regions }

    private fun label(row: String): String =
        when (row.substringAfterLast('_')) {
            "n" -> "north"
            "s" -> "south"
            "ne" -> "north-east"
            "nw" -> "north-west"
            "se" -> "south-east"
            "sw" -> "south-west"
            else -> row.substringAfterLast('_')
        }

    private fun transmitVarbit(loc: Int): String? {
        val type = ServerCacheManager.getObject(loc) ?: return null
        if (type.multiVarBit <= 0) {
            return null
        }
        return RSCM.getReverseMapping(RSCMType.VARBIT, type.multiVarBit).takeIf { it.isNotBlank() }
    }
}

private fun FarmingCropRow.toCrop(): Crop {
    val payment = payment.firstOrNull()
    return Crop(
        id = id,
        name = name,
        kind = PatchKind.of(category),
        planted = input.internalName,
        plantedCount = inputAmount,
        produce = output.internalName,
        level = statReq.first().t1,
        plantXp = plantXp / 10.0,
        harvestXp = xp / 10.0,
        checkXp = checkXp / 10.0,
        growBase = growBase,
        stages = stages,
        tickMinutes = tickMinutes,
        diseaseChance = diseaseChance,
        diseasedBase = diseasedBase,
        transmit = transmit,
        saveLow = saveChance.getOrElse(0) { 0 },
        saveHigh = saveChance.getOrElse(1) { 0 },
        protection = protection?.id ?: 0,
        payment = payment?.t0?.internalName,
        paymentCount = payment?.t1 ?: 0,
        tree = tree?.let { RSCM.getReverseMapping(RSCMType.LOC, it.id) },
        seed = seed?.internalName,
        seedling = seedling?.internalName,
        wateredSeedling = wateredSeedling?.internalName,
    )
}
