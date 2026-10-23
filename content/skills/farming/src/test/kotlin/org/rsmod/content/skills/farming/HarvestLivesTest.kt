package org.rsmod.content.skills.farming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Wiki percentages: potato 39.8% to 70.7%, guam 10.2% to 31.6%, between levels 1 and 99. */
class HarvestLivesTest {
    @Test
    fun `chance to save interpolates between the crop's level 1 and 99 constants`() {
        assertEquals(102, HarvestLives.saveChance(1, 101, 180, magicSecateurs = false))
        assertEquals(181, HarvestLives.saveChance(99, 101, 180, magicSecateurs = false))
        assertEquals(26, HarvestLives.saveChance(1, 25, 80, magicSecateurs = false))
        assertEquals(81, HarvestLives.saveChance(99, 25, 80, magicSecateurs = false))
        assertEquals(54, HarvestLives.saveChance(50, 25, 80, magicSecateurs = false))
    }

    @Test
    fun `magic secateurs raise both constants by a tenth, rounded down`() {
        assertEquals(28, HarvestLives.saveChance(1, 25, 80, magicSecateurs = true))
        assertEquals(89, HarvestLives.saveChance(99, 25, 80, magicSecateurs = true))
    }
}
