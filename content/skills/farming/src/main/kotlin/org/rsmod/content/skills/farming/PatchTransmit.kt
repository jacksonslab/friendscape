package org.rsmod.content.skills.farming

/**
 * The value the client reads from a patch's `farming_transmit_*` varbit to pick which multiloc it
 * draws. Each patch kind lays its states out differently in its loc's multiloc table; these
 * offsets are read straight off those tables.
 */
object PatchTransmit {
    private const val WATERED = 64
    private const val DISEASED = 128
    private const val DEAD = 192
    private const val TREE_DISEASED = 64
    private const val TREE_DEAD = 128
    private const val HERB_DEAD_BASE = 170
    private const val HERB_GROWING_STAGES = 3

    fun value(state: PatchState, crop: Crop?): Int {
        if (crop == null || state.empty) {
            return state.raked
        }
        return when (crop.kind) {
            PatchKind.Allotment,
            PatchKind.Flower -> plant(state, crop)
            PatchKind.Herb -> herb(state, crop)
            PatchKind.Tree -> tree(state, crop)
            PatchKind.FruitTree -> fruitTree(state, crop)
        }
    }

    private fun plant(state: PatchState, crop: Crop): Int {
        val stage = state.stage.coerceIn(0, crop.grownStage)
        if (crop.transmit.isNotEmpty()) {
            val bank =
                when {
                    state.health == Health.Dead -> 3
                    state.health == Health.Diseased -> 2
                    state.watered -> 1
                    else -> 0
                }
            return crop.transmit[bank * crop.stages + stage]
        }
        val base = crop.growBase + stage
        return when {
            state.health == Health.Dead -> base + DEAD
            state.health == Health.Diseased -> base + DISEASED
            state.watered -> base + WATERED
            else -> base
        }
    }

    private fun herb(state: PatchState, crop: Crop): Int {
        val growing = (state.stage - 1).coerceIn(0, HERB_GROWING_STAGES - 1)
        return when (state.health) {
            Health.Dead -> HERB_DEAD_BASE + growing
            Health.Diseased -> crop.diseasedBase + growing
            Health.Healthy -> crop.growBase + state.stage.coerceIn(0, crop.grownStage)
        }
    }

    private fun tree(state: PatchState, crop: Crop): Int {
        val grown = state.stage >= crop.grownStage
        val stage = if (grown && state.checked) crop.stages else state.stage
        return when {
            state.stump -> crop.growBase + crop.stages + 1
            state.health == Health.Dead -> crop.growBase + TREE_DEAD + stage
            state.health == Health.Diseased -> crop.growBase + TREE_DISEASED + stage
            else -> crop.growBase + stage
        }
    }

    /** Fruit trees pack each crop into 27 slots: growth, fruit 0-6, diseased, dead, stump, check. */
    private fun fruitTree(state: PatchState, crop: Crop): Int {
        val base = crop.growBase
        val grown = state.stage >= crop.grownStage
        return when {
            state.stump -> base + 25
            state.health == Health.Dead -> if (grown) base + 24 else base + 18 + state.stage
            state.health == Health.Diseased -> if (grown) base + 18 else base + 12 + state.stage
            grown && !state.checked -> base + 26
            grown -> base + crop.grownStage + state.lives.coerceIn(0, PatchGrowth.MAX_FRUIT)
            else -> base + state.stage
        }
    }
}
