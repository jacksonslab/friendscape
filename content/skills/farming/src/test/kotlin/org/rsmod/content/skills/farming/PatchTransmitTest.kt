package org.rsmod.content.skills.farming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.farming.PatchGrowthTest.Companion.APPLE
import org.rsmod.content.skills.farming.PatchGrowthTest.Companion.GUAM
import org.rsmod.content.skills.farming.PatchGrowthTest.Companion.MARIGOLD
import org.rsmod.content.skills.farming.PatchGrowthTest.Companion.OAK
import org.rsmod.content.skills.farming.PatchGrowthTest.Companion.POTATO
import org.rsmod.content.skills.farming.PatchGrowthTest.Companion.SNAPE_GRASS

/** Expected values are the multiloc indices the patch locs map each state to. */
class PatchTransmitTest {
    private fun value(crop: Crop?, state: PatchState) = PatchTransmit.value(state, crop)

    @Test
    fun `weeds count rake strokes`() {
        assertEquals(0, value(null, PatchState()))
        assertEquals(3, value(null, PatchState.EMPTY_CLEAR))
    }

    @Test
    fun `allotment states`() {
        val planted = PatchState(cropId = POTATO.id, raked = 3)
        assertEquals(6, value(POTATO, planted))
        assertEquals(10, value(POTATO, planted.copy(stage = 4, lives = 3)))
        assertEquals(71, value(POTATO, planted.copy(stage = 1, watered = true)))
        assertEquals(135, value(POTATO, planted.copy(stage = 1, health = Health.Diseased)))
        assertEquals(199, value(POTATO, planted.copy(stage = 1, health = Health.Dead)))
    }

    @Test
    fun `snape grass uses its scattered slots`() {
        val planted = PatchState(cropId = SNAPE_GRASS.id, raked = 3)
        assertEquals(128, value(SNAPE_GRASS, planted))
        assertEquals(138, value(SNAPE_GRASS, planted.copy(stage = 7)))
        assertEquals(63, value(SNAPE_GRASS, planted.copy(watered = true)))
        assertEquals(202, value(SNAPE_GRASS, planted.copy(stage = 4, health = Health.Diseased)))
        assertEquals(209, value(SNAPE_GRASS, planted.copy(stage = 4, health = Health.Dead)))
    }

    @Test
    fun `flower states`() {
        val planted = PatchState(cropId = MARIGOLD.id, raked = 3)
        assertEquals(12, value(MARIGOLD, planted.copy(stage = 4)))
        assertEquals(72, value(MARIGOLD, planted.copy(watered = true)))
        assertEquals(137, value(MARIGOLD, planted.copy(stage = 1, health = Health.Diseased)))
        assertEquals(204, value(MARIGOLD, planted.copy(stage = 4, health = Health.Dead)))
    }

    @Test
    fun `herb states`() {
        val planted = PatchState(cropId = GUAM.id, raked = 3)
        assertEquals(4, value(GUAM, planted))
        assertEquals(8, value(GUAM, planted.copy(stage = 4)))
        assertEquals(129, value(GUAM, planted.copy(stage = 2, health = Health.Diseased)))
        assertEquals(171, value(GUAM, planted.copy(stage = 2, health = Health.Dead)))
    }

    @Test
    fun `tree states`() {
        val planted = PatchState(cropId = OAK.id, raked = 3)
        assertEquals(8, value(OAK, planted))
        assertEquals(12, value(OAK, planted.copy(stage = 4)))
        assertEquals(13, value(OAK, planted.copy(stage = 4, checked = true)))
        assertEquals(14, value(OAK, planted.copy(stage = 4, checked = true, stump = true)))
        assertEquals(73, value(OAK, planted.copy(stage = 1, health = Health.Diseased)))
        assertEquals(137, value(OAK, planted.copy(stage = 1, health = Health.Dead)))
    }

    @Test
    fun `fruit tree states`() {
        val grown = PatchState(cropId = APPLE.id, raked = 3, stage = 6)
        assertEquals(9, value(APPLE, grown.copy(stage = 1)))
        assertEquals(34, value(APPLE, grown.copy(lives = 6)))
        assertEquals(20, value(APPLE, grown.copy(lives = 6, checked = true)))
        assertEquals(14, value(APPLE, grown.copy(lives = 0, checked = true)))
        assertEquals(33, value(APPLE, grown.copy(checked = true, stump = true)))
        assertEquals(21, value(APPLE, grown.copy(stage = 1, health = Health.Diseased)))
        assertEquals(27, value(APPLE, grown.copy(stage = 1, health = Health.Dead)))
    }
}
