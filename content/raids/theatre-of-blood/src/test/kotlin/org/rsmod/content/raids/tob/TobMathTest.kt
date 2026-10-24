package org.rsmod.content.raids.tob

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.content.raids.tob.rewards.TobPoints
import org.rsmod.map.CoordGrid

class TobMathTest {
    @Test
    fun `hitpoints scale to 75 percent at three or fewer and 87_5 at four`() {
        assertEquals(1.0, TobScaling.hpFactor(5))
        assertEquals(0.875, TobScaling.hpFactor(4))
        assertEquals(0.75, TobScaling.hpFactor(3))
        assertEquals(0.75, TobScaling.hpFactor(1))
        assertEquals(2625, TobScaling.scaledHitpoints(3500, 0.75))
        assertEquals(3062, TobScaling.scaledHitpoints(3500, 0.875))
        assertEquals(1, TobScaling.scaledHitpoints(1, 0.75))
    }

    @Test
    fun `matomenos are two per raider capped at ten`() {
        assertEquals(2, TobScaling.matomenosCount(1))
        assertEquals(6, TobScaling.matomenosCount(3))
        assertEquals(10, TobScaling.matomenosCount(5))
        assertEquals(10, TobScaling.matomenosCount(7))
    }

    @Test
    fun `deathless team gets the full one in nine point one unique chance`() {
        for (size in 1..5) {
            val team = TobPoints.personal(0, 0) * size + TobPoints.MVP_TOTAL
            assertEquals(1 / 9.1, TobPoints.uniqueChance(team, size), 1e-9)
        }
    }

    @Test
    fun `each death costs four points and lowers the unique chance`() {
        assertEquals(14, TobPoints.personal(deaths = 1, mvpPoints = 0))
        assertEquals(0, TobPoints.personal(deaths = 5, mvpPoints = 0))
        assertEquals(4, TobPoints.personal(deaths = 9, mvpPoints = 4))
        val clean = TobPoints.uniqueChance(18 * 3 + 14, 3)
        val oneDeath = TobPoints.uniqueChance(18 * 3 + 14 - 4, 3)
        assertTrue(oneDeath < clean)
        assertEquals(0.0, TobPoints.uniqueChance(0, 3))
        assertEquals(0.0, TobPoints.uniqueChance(10, 0))
    }

    @Test
    fun `performance scales quantities but never below one`() {
        assertEquals(1.0, TobPoints.performance(18))
        assertEquals(1.0, TobPoints.performance(30))
        assertEquals(0.5, TobPoints.performance(9))
        assertEquals(30, TobPoints.scaledCount(60, 0.5))
        assertEquals(1, TobPoints.scaledCount(1, 0.1))
        assertEquals(1, TobPoints.scaledCount(3, 0.0))
    }

    @Test
    fun `unique recipient is weighted by points and skips raiders without any`() {
        val random = DefaultGameRandom(seed = 7L)
        val counts = HashMap<String, Int>()
        repeat(20_000) {
            val pick = TobPoints.pickRecipient(random, mapOf("a" to 30, "b" to 10, "c" to 0))!!
            counts.merge(pick, 1, Int::plus)
        }
        assertEquals(null, counts["c"])
        val shareA = counts.getValue("a") / 20_000.0
        assertEquals(0.75, shareA, 0.02)
        assertNull(TobPoints.pickRecipient(random, mapOf("a" to 0, "b" to 0)))
    }

    @Test
    fun `layout translates static coordinates into the instance and back`() {
        val base = CoordGrid(6400, 1000, 0)
        val maidenEntry = CoordGrid(3219, 4456, 0)
        val inside = TobLayout.toInstance(base, maidenEntry)
        assertEquals(CoordGrid(6400 + 83, 1000 + 232, 0), inside)
        assertEquals(maidenEntry, TobLayout.toStatic(base, inside))
        assertTrue(TobLayout.contains(base, inside))
        assertFalse(TobLayout.contains(base, CoordGrid(6399, 1000, 0)))
        assertEquals(CoordGrid(3170, 4376, 1), TobLayout.toStatic(base, TobLayout.toInstance(base, CoordGrid(3170, 4376, 1))))
    }

    @Test
    fun `barriers know which side is the arena`() {
        val maiden = TobBarrier(vertical = true, line = 3185, insideStep = -1)
        assertTrue(maiden.isInside(CoordGrid(3184, 4446)))
        assertFalse(maiden.isInside(CoordGrid(3186, 4446)))
        assertEquals(CoordGrid(3184, 4447), maiden.across(CoordGrid(3185, 4447), inward = true))
        assertEquals(CoordGrid(3186, 4447), maiden.across(CoordGrid(3185, 4447), inward = false))
        val sotetseg = TobBarrier(vertical = false, line = 4307, insideStep = 1)
        assertTrue(sotetseg.isInside(CoordGrid(3279, 4308)))
        assertEquals(CoordGrid(3279, 4306), sotetseg.across(CoordGrid(3279, 4307), inward = false))
    }
}
