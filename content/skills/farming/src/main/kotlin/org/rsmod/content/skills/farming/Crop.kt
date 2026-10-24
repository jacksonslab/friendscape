package org.rsmod.content.skills.farming

enum class PatchKind {
    Allotment,
    Flower,
    Herb,
    Tree,
    FruitTree;

    val tree: Boolean
        get() = this == Tree || this == FruitTree

    companion object {
        fun of(category: String): PatchKind = entries.first { it.name.equals(category, true) }
    }
}

/**
 * A crop as `dbtable.farming_crop` describes it. [stages] counts every state the crop grows
 * through, seed and fully grown included, so [grownStage] is the last of them.
 */
data class Crop(
    val id: Int,
    val name: String,
    val kind: PatchKind,
    val planted: String,
    val plantedCount: Int,
    val produce: String,
    val level: Int,
    val plantXp: Double,
    val harvestXp: Double,
    val checkXp: Double,
    val growBase: Int,
    val stages: Int,
    val tickMinutes: Int,
    val diseaseChance: Int,
    val diseasedBase: Int = -1,
    val transmit: List<Int> = emptyList(),
    val saveLow: Int = 0,
    val saveHigh: Int = 0,
    val protection: Int = 0,
    val payment: String? = null,
    val paymentCount: Int = 0,
    val tree: String? = null,
    val seed: String? = null,
    val seedling: String? = null,
    val wateredSeedling: String? = null,
) {
    val grownStage: Int
        get() = stages - 1

    val waterable: Boolean
        get() = kind == PatchKind.Allotment || kind == PatchKind.Flower

    val usesLives: Boolean
        get() = kind == PatchKind.Allotment || kind == PatchKind.Herb
}
