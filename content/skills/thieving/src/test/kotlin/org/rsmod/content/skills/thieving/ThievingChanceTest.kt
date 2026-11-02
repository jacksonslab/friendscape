package org.rsmod.content.skills.thieving

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThievingChanceTest {
    private val gloves = PickpocketBoosts(glovesOfSilence = true)
    private val diary = PickpocketBoosts(ardougneDiary = true)
    private val capeAndDiary = PickpocketBoosts(ardougneDiary = true, thievingCape = true)

    @Test
    fun `guard boosts match the wiki chart`() {
        assertEquals(50 to 240, boosted(50, 240, PickpocketBoosts()))
        assertEquals(52 to 252, boosted(50, 240, gloves))
        assertEquals(55 to 264, boosted(50, 240, diary))
        assertEquals(60 to 290, boosted(50, 240, capeAndDiary))
    }

    @Test
    fun `gnome boosts truncate like the wiki chart`() {
        assertEquals(45 to 183, boosted(43, 175, gloves))
        assertEquals(47 to 192, boosted(43, 175, diary))
    }

    @Test
    fun `negative tzhaar rolls scale away from zero`() {
        assertEquals(-210 to 210, boosted(-200, 200, gloves))
        assertEquals(-220 to 220, boosted(-200, 200, diary))
    }

    @Test
    fun `level 1 man succeeds 181 in 256`() {
        assertEquals(181 / 256.0, ThievingChance.successChance(180, 240, 1, PickpocketBoosts()), 1e-9)
    }

    @Test
    fun `level 99 uses the high roll`() {
        assertEquals(241 / 256.0, ThievingChance.successChance(50, 240, 99, PickpocketBoosts()), 1e-9)
    }

    @Test
    fun `interpolation rounds half up between the rolls`() {
        val expected = (1 + Math.floor(50 * 49 / 98.0 + 240 * 49 / 98.0 + 0.5)) / 256.0
        assertEquals(expected, ThievingChance.successChance(50, 240, 50, PickpocketBoosts()), 1e-9)
    }

    @Test
    fun `cape and diary guard is a certainty at 99 and never above one`() {
        assertEquals(1.0, ThievingChance.successChance(50, 240, 99, capeAndDiary), 1e-9)
    }

    @Test
    fun `a negative roll clamps to zero rather than going negative`() {
        assertEquals(0.0, ThievingChance.successChance(-200, 200, 1, PickpocketBoosts()), 1e-9)
        assertTrue(ThievingChance.successChance(-200, 200, 90, PickpocketBoosts()) > 0.0)
    }

    @Test
    fun `boosted levels above 99 are treated as 99`() {
        assertEquals(
            ThievingChance.successChance(39, 160, 99, PickpocketBoosts()),
            ThievingChance.successChance(39, 160, 105, PickpocketBoosts()),
            1e-9,
        )
    }

    private fun boosted(low: Int, high: Int, boosts: PickpocketBoosts): Pair<Int, Int> =
        ThievingChance.boosted(low, boosts) to ThievingChance.boosted(high, boosts)
}
