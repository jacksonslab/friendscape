package org.rsmod.content.skills.sailing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationTest {
    @Test
    fun `fast boats cannot jump a narrow strip of land`() {
        val water: (Int, Int) -> Boolean = { x, _ -> x != 11 }
        assertTrue(Navigation.fits(water, fine(10), fine(10), 0, 1, 1))
        assertTrue(Navigation.fits(water, fine(13), fine(10), 0, 1, 1))
        assertFalse(Navigation.passageClear(water, fine(10), fine(10), 384, 0, 0, 1, 1))
        assertFalse(Navigation.passageClear(water, fine(13), fine(10), -384, 0, 0, 1, 1))
    }

    @Test
    fun `open water allows movement in both directions`() {
        assertTrue(Navigation.passageClear({ _, _ -> true }, fine(10), fine(10), 384, -128, 0, 2, 5))
        assertTrue(Navigation.passageClear({ _, _ -> true }, fine(10), fine(10), -384, 128, 0, 2, 5))
    }

    @Test
    fun `a raft heading south covers three tiles along the z axis`() {
        val tiles = Navigation.hullTiles(fine(10), fine(10), angle = 0, width = 1, length = 3)
        assertEquals(setOf(tile(10, 9), tile(10, 10), tile(10, 11)), tiles)
    }

    @Test
    fun `a raft turned west covers three tiles along the x axis`() {
        val tiles = Navigation.hullTiles(fine(10), fine(10), angle = 512, width = 1, length = 3)
        assertEquals(setOf(tile(9, 10), tile(10, 10), tile(11, 10)), tiles)
    }

    @Test
    fun `a sloop is three tiles wide and eight long`() {
        val tiles = Navigation.hullTiles(fine(20), fine(20), angle = 0, width = 3, length = 8)
        assertEquals(3, tiles.map { Navigation.unpackX(it) }.distinct().size)
        assertEquals(8, tiles.map { Navigation.unpackZ(it) }.distinct().size)
    }

    @Test
    fun `a hull only fits when every tile under it is water`() {
        val sea = water(x = 0..20, z = 0..20) - tile(10, 11)
        assertFalse(Navigation.fits({ x, z -> sea.has(x, z) }, fine(10), fine(10), 0, 1, 3))
        assertTrue(Navigation.fits({ x, z -> sea.has(x, z) }, fine(12), fine(10), 0, 1, 3))
    }

    @Test
    fun `mooring lies alongside the pier on the water side`() {
        val pierEnd = tile(10, 10)
        val sea = water(x = 11..20, z = 0..20)
        val mooring = checkNotNull(Navigation.findMooring({ x, z -> sea.has(x, z) }, 10, 10, width = 1, length = 3))
        assertEquals(0, mooring.angle)
        assertTrue(mooring.fineX / FINE > 10)
        assertTrue(Navigation.fits({ x, z -> sea.has(x, z) }, mooring.fineX, mooring.fineZ, mooring.angle, 1, 3))
        assertFalse(pierEnd in Navigation.hullTiles(mooring.fineX, mooring.fineZ, mooring.angle, 1, 3))
    }

    @Test
    fun `mooring slides along a pier that juts out into the water`() {
        val pier = (5..12).map { tile(it, 10) }.toSet()
        val sea = water(x = 0..12, z = 0..20) - pier
        val mooring = checkNotNull(Navigation.findMooring({ x, z -> sea.has(x, z) }, 12, 10, width = 1, length = 3))
        val hull = Navigation.hullTiles(mooring.fineX, mooring.fineZ, mooring.angle, 1, 3)
        assertTrue(hull.none { it in pier })
        assertTrue(hull.all { it in sea })
    }

    @Test
    fun `no mooring is found on dry land`() {
        assertNull(Navigation.findMooring({ _, _ -> false }, 10, 10, width = 1, length = 3))
    }

    @Test
    fun `walls and locs make an otherwise sailable tile unclear`() {
        assertTrue(Navigation.isClear(0))
        assertTrue(Navigation.isClear(org.rsmod.routefinder.flag.CollisionFlag.BLOCK_WALK))
        assertFalse(Navigation.isClear(org.rsmod.routefinder.flag.CollisionFlag.LOC))
        assertFalse(Navigation.isClear(org.rsmod.routefinder.flag.CollisionFlag.WALL_EAST))
        assertFalse(Navigation.isClear(-1))
    }

    private fun water(x: IntRange, z: IntRange): Set<Long> =
        x.flatMap { tx -> z.map { tz -> tile(tx, tz) } }.toSet()

    private fun Set<Long>.has(x: Int, z: Int): Boolean = Navigation.pack(x, z) in this

    private fun tile(x: Int, z: Int): Long = Navigation.pack(x, z)

    private fun fine(tile: Int): Int = tile * FINE + FINE / 2

    private companion object {
        private const val FINE = 128
    }
}
