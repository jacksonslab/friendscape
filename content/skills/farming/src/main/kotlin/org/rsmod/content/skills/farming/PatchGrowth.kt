package org.rsmod.content.skills.farming

import kotlin.math.floor
import kotlin.math.max

/**
 * Growth runs on the global five-minute farming clock, counted in ticks since the epoch, so it
 * needs nothing but the tick a patch was last brought up to date at. A crop advances on every
 * tick that lands on a multiple of its own cycle length, exactly as live: a seed planted just
 * before a cycle boundary moves to its second stage almost at once.
 */
object PatchGrowth {
    const val TICK_MINUTES: Int = 5
    const val FRUIT_REGROW_MINUTES: Int = 45
    const val MAX_FRUIT: Int = 6
    const val BASE_LIVES: Int = 3

    /** Every state converges well within a week, so a longer absence replays only its last week. */
    const val MAX_CATCH_UP_TICKS: Long = 7L * 24 * 60 / TICK_MINUTES

    fun tickOf(epochMillis: Long): Long = epochMillis / (TICK_MINUTES * 60_000L)

    /**
     * Brings [state] from [fromTick] up to [toTick]. [roll] is asked for disease with a chance out
     * of 128 and answers whether the crop caught it.
     */
    fun grow(
        state: PatchState,
        crop: Crop?,
        fromTick: Long,
        toTick: Long,
        flowerProtected: Boolean = false,
        roll: (Int) -> Boolean,
    ): PatchState {
        var current = state
        val start = max(fromTick, toTick - MAX_CATCH_UP_TICKS)
        var tick = start + 1
        while (tick <= toTick) {
            current = tick(current, crop, tick * TICK_MINUTES, flowerProtected, roll)
            tick++
        }
        return current
    }

    fun tick(
        state: PatchState,
        crop: Crop?,
        minute: Long,
        flowerProtected: Boolean,
        roll: (Int) -> Boolean,
    ): PatchState {
        if (crop == null || state.empty) {
            return regrowWeeds(state)
        }
        if (state.health == Health.Dead) {
            return state
        }
        var current = state
        if (crop.kind == PatchKind.FruitTree && minute % FRUIT_REGROW_MINUTES == 0L) {
            current = regrowFruit(current, crop)
        }
        if (minute % crop.tickMinutes != 0L) {
            return current
        }
        if (current.health == Health.Diseased) {
            return current.copy(health = Health.Dead, watered = false)
        }
        if (current.stage < crop.grownStage) {
            val exposed = current.stage >= 1 && !current.protected && !flowerProtected
            if (exposed && roll(diseaseChance(crop, current))) {
                return current.copy(health = Health.Diseased, watered = false)
            }
            val next = current.copy(stage = current.stage + 1, watered = false)
            return if (next.stage == crop.grownStage) ripen(next, crop) else next
        }
        if (current.stump) {
            return current.copy(stump = false, lives = 0)
        }
        return current
    }

    /**
     * Compost cuts the per-cycle chance by 50/80/90%, rounded down to a 128th; the published
     * figures count the zero roll, which is the `- 1` / `+ 1`. Watering halves what is left.
     */
    fun diseaseChance(crop: Crop, state: PatchState): Int {
        val base = crop.diseaseChance
        var chance =
            if (state.compost == Compost.None || base <= 0) {
                base
            } else {
                floor((base - 1) * state.compost.diseaseFactor).toInt() + 1
            }
        if (state.watered) {
            chance /= 2
        }
        return chance
    }

    fun ripen(state: PatchState, crop: Crop): PatchState =
        when (crop.kind) {
            PatchKind.Allotment,
            PatchKind.Herb -> state.copy(lives = BASE_LIVES + state.compost.ordinal)
            PatchKind.Flower -> state.copy(lives = 1)
            PatchKind.FruitTree -> state.copy(lives = MAX_FRUIT, checked = false)
            PatchKind.Tree -> state.copy(checked = false)
        }

    private fun regrowFruit(state: PatchState, crop: Crop): PatchState {
        val bearing =
            state.stage == crop.grownStage &&
                state.health == Health.Healthy &&
                state.checked &&
                !state.stump
        if (!bearing || state.lives >= MAX_FRUIT) {
            return state
        }
        return state.copy(lives = state.lives + 1)
    }

    private fun regrowWeeds(state: PatchState): PatchState {
        if (state.raked <= 0) {
            return state
        }
        return PatchState(raked = state.raked - 1)
    }
}
