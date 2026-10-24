package org.rsmod.content.skills.farming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatchGrowthTest {
    private val never: (Int) -> Boolean = { false }
    private val always: (Int) -> Boolean = { true }

    @Test
    fun `packing round-trips every field`() {
        val state =
            PatchState(
                cropId = 41,
                stage = 12,
                raked = 3,
                health = Health.Diseased,
                watered = true,
                compost = Compost.Ultra,
                lives = 15,
                protected = true,
                checked = true,
                stump = true,
            )
        assertEquals(state, PatchState.unpack(state.pack()))
        assertEquals(PatchState(), PatchState.unpack(0))
    }

    @Test
    fun `potatoes take four ten-minute cycles and ripen with three lives`() {
        val planted = PatchState(cropId = POTATO.id, raked = 3)
        val start = cycleStart(POTATO)
        val afterThree = PatchGrowth.grow(planted, POTATO, start, start + 3 * 2, roll = never)
        assertEquals(3, afterThree.stage)
        val grown = PatchGrowth.grow(afterThree, POTATO, start + 6, start + 8, roll = never)
        assertEquals(POTATO.grownStage, grown.stage)
        assertEquals(3, grown.lives)
    }

    @Test
    fun `growth lands on the global clock, not on planting time`() {
        val planted = PatchState(cropId = POTATO.id, raked = 3)
        val start = cycleStart(POTATO)
        assertEquals(0, PatchGrowth.grow(planted, POTATO, start, start + 1, roll = never).stage)
        val midCycle = start + 1
        assertEquals(1, PatchGrowth.grow(planted, POTATO, midCycle, midCycle + 1, roll = never).stage)
    }

    @Test
    fun `a long absence still finishes the crop`() {
        val planted = PatchState(cropId = MAGIC.id, raked = 3)
        val start = cycleStart(MAGIC)
        val months = PatchGrowth.grow(planted, MAGIC, start, start + 60L * 24 * 12, roll = never)
        assertEquals(MAGIC.grownStage, months.stage)
        assertEquals(Health.Healthy, months.health)
    }

    @Test
    fun `compost grants extra harvest lives`() {
        for ((compost, lives) in listOf(Compost.Normal to 4, Compost.Super to 5, Compost.Ultra to 6)) {
            val state = PatchState(cropId = GUAM.id, raked = 3, compost = compost, stage = GUAM.grownStage - 1)
            val start = cycleStart(GUAM)
            assertEquals(lives, PatchGrowth.grow(state, GUAM, start, start + 4, roll = never).lives)
        }
    }

    @Test
    fun `seeds cannot catch disease but older crops can, and die a cycle later`() {
        val seed = PatchState(cropId = POTATO.id, raked = 3)
        val start = cycleStart(POTATO)
        val sprouted = PatchGrowth.grow(seed, POTATO, start, start + 2, roll = always)
        assertEquals(Health.Healthy, sprouted.health)
        assertEquals(1, sprouted.stage)

        val diseased = PatchGrowth.grow(sprouted, POTATO, start + 2, start + 4, roll = always)
        assertEquals(Health.Diseased, diseased.health)
        assertEquals(1, diseased.stage)

        val dead = PatchGrowth.grow(diseased, POTATO, start + 4, start + 6, roll = never)
        assertEquals(Health.Dead, dead.health)
        assertEquals(dead, PatchGrowth.grow(dead, POTATO, start + 6, start + 600, roll = always))
    }

    @Test
    fun `protected crops never catch disease`() {
        val state = PatchState(cropId = POTATO.id, raked = 3, stage = 1, protected = true)
        val start = cycleStart(POTATO)
        val grown = PatchGrowth.grow(state, POTATO, start, start + 20, roll = always)
        assertEquals(Health.Healthy, grown.health)
        assertEquals(POTATO.grownStage, grown.stage)

        val flowered = PatchGrowth.grow(state.copy(protected = false), POTATO, start, start + 20, true, always)
        assertEquals(Health.Healthy, flowered.health)
    }

    @Test
    fun `compost cuts disease the way the wiki table rounds it`() {
        val expected =
            mapOf(
                27 to listOf(27, 14, 6, 3),
                18 to listOf(18, 9, 4, 2),
                13 to listOf(13, 7, 3, 2),
                9 to listOf(9, 5, 2, 1),
            )
        for ((base, chances) in expected) {
            val crop = HERB.copy(diseaseChance = base)
            val actual = Compost.entries.map { PatchGrowth.diseaseChance(crop, PatchState(compost = it)) }
            assertEquals(chances, actual, "base $base")
        }
        val watered = PatchState(compost = Compost.None, watered = true)
        assertEquals(13, PatchGrowth.diseaseChance(POTATO, watered))
    }

    @Test
    fun `watering lasts one cycle`() {
        val state = PatchState(cropId = POTATO.id, raked = 3, stage = 1, watered = true)
        val start = cycleStart(POTATO)
        assertFalse(PatchGrowth.grow(state, POTATO, start, start + 2, roll = never).watered)
    }

    @Test
    fun `weeds creep back over an empty patch and take the compost with them`() {
        val raked = PatchState(raked = 3, compost = Compost.Super)
        val once = PatchGrowth.grow(raked, null, 100, 101, roll = never)
        assertEquals(2, once.raked)
        assertEquals(Compost.None, once.compost)
        assertEquals(0, PatchGrowth.grow(raked, null, 100, 110, roll = never).raked)
    }

    @Test
    fun `fruit trees bear six after a health check and regrow one every 45 minutes`() {
        val grown = PatchGrowth.ripen(PatchState(cropId = APPLE.id, raked = 3, stage = APPLE.grownStage), APPLE)
        assertEquals(6, grown.lives)
        assertFalse(grown.checked)

        val picked = grown.copy(checked = true, lives = 2)
        val start = (FRUIT_TICKS * 1000L)
        val regrown = PatchGrowth.grow(picked, APPLE, start, start + FRUIT_TICKS * 3, roll = never)
        assertEquals(5, regrown.lives)
        assertEquals(6, PatchGrowth.grow(picked, APPLE, start, start + FRUIT_TICKS * 10, roll = never).lives)
    }

    @Test
    fun `a chopped tree regrows from its stump on its next cycle`() {
        val stump = PatchState(cropId = OAK.id, raked = 3, stage = OAK.grownStage, checked = true, stump = true)
        val start = cycleStart(OAK)
        val regrown = PatchGrowth.grow(stump, OAK, start, start + OAK.tickMinutes / 5, roll = always)
        assertFalse(regrown.stump)
        assertTrue(regrown.checked)
        assertEquals(Health.Healthy, regrown.health)
    }

    private fun cycleStart(crop: Crop): Long = crop.tickMinutes / 5 * 10_000L

    companion object {
        private const val FRUIT_TICKS = PatchGrowth.FRUIT_REGROW_MINUTES / PatchGrowth.TICK_MINUTES

        val POTATO =
            Crop(
                id = 1,
                name = "potato",
                kind = PatchKind.Allotment,
                planted = "obj.potato_seed",
                plantedCount = 3,
                produce = "obj.potato",
                level = 1,
                plantXp = 8.0,
                harvestXp = 9.0,
                checkXp = 0.0,
                growBase = 6,
                stages = 5,
                tickMinutes = 10,
                diseaseChance = 27,
                saveLow = 101,
                saveHigh = 180,
            )
        val GUAM =
            POTATO.copy(
                id = 15,
                name = "guam",
                kind = PatchKind.Herb,
                tickMinutes = 20,
                growBase = 4,
                diseasedBase = 128,
            )
        val HERB = GUAM
        val OAK = POTATO.copy(id = 29, name = "oak", kind = PatchKind.Tree, growBase = 8, stages = 5, tickMinutes = 40)
        val MAGIC = OAK.copy(id = 33, name = "magic", growBase = 48, stages = 13)
        val APPLE =
            POTATO.copy(id = 34, name = "apple", kind = PatchKind.FruitTree, growBase = 8, stages = 7, tickMinutes = 160)
        val SNAPE_GRASS =
            POTATO.copy(
                id = 8,
                name = "snape grass",
                growBase = 128,
                stages = 8,
                transmit =
                    listOf(
                        128, 129, 130, 131, 132, 133, 134, 138,
                        63, 64, 65, 66, 67, 68, 69, 138,
                        196, 196, 197, 198, 202, 203, 204, 204,
                        193, 193, 194, 195, 209, 210, 211, 211,
                    ),
            )
        val MARIGOLD = POTATO.copy(id = 9, name = "marigold", kind = PatchKind.Flower, growBase = 8, tickMinutes = 5)
    }
}
