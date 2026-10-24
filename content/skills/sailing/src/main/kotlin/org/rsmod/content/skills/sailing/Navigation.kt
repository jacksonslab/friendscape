package org.rsmod.content.skills.sailing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.rsmod.routefinder.flag.CollisionFlag

internal data class Mooring(val fineX: Int, val fineZ: Int, val angle: Int)

/**
 * Hull geometry against a sailable-tile predicate (see [SeaMap] and [BoatManager.navigableAt]).
 */
internal object Navigation {
    fun isClear(flags: Int): Boolean = flags != -1 && flags and SOLID_MASK == 0

    fun hullTiles(fineX: Int, fineZ: Int, angle: Int, width: Int, length: Int): Set<Long> {
        val theta = angle * (2.0 * PI / ANGLE_FULL)
        val headX = -sin(theta)
        val headZ = -cos(theta)
        val sideX = -headZ
        val sideZ = headX
        val tiles = HashSet<Long>()
        var along = -(length - 1) / 2.0
        while (along <= (length - 1) / 2.0 + EPSILON) {
            var across = -(width - 1) / 2.0
            while (across <= (width - 1) / 2.0 + EPSILON) {
                val x = fineX + (headX * along + sideX * across) * FINE
                val z = fineZ + (headZ * along + sideZ * across) * FINE
                tiles += pack(floor(x / FINE).toInt(), floor(z / FINE).toInt())
                across += SAMPLE_STEP
            }
            along += SAMPLE_STEP
        }
        return tiles
    }

    fun fits(
        sailable: (x: Int, z: Int) -> Boolean,
        fineX: Int,
        fineZ: Int,
        angle: Int,
        width: Int,
        length: Int,
    ): Boolean = hullTiles(fineX, fineZ, angle, width, length).all { sailable(unpackX(it), unpackZ(it)) }

    fun passageClear(
        sailable: (x: Int, z: Int) -> Boolean,
        fineX: Int,
        fineZ: Int,
        dx: Int,
        dz: Int,
        angle: Int,
        width: Int,
        length: Int,
    ): Boolean {
        val steps = maxOf(1, (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) + 31) / 32)
        return (0..steps).all { step ->
            fits(sailable, fineX + (dx.toLong() * step / steps).toInt(), fineZ + (dz.toLong() * step / steps).toInt(), angle, width, length)
        }
    }

    /**
     * Searches outward from a gangplank for the closest spot a hull can lie alongside it, with the
     * hull's long side facing the gangplank.
     */
    fun findMooring(
        sailable: (x: Int, z: Int) -> Boolean,
        plankX: Int,
        plankZ: Int,
        width: Int,
        length: Int,
    ): Mooring? {
        for (reach in 1..MAX_MOORING_DISTANCE) {
            for (distance in 1..reach) {
                val slide = reach - distance
                for ((dx, dz, angle) in SIDES) {
                    for (offset in if (slide == 0) intArrayOf(0) else intArrayOf(-slide, slide)) {
                        val x = plankX + dx * distance + dz * offset
                        val z = plankZ + dz * distance + dx * offset
                        val fineX = x * FINE + FINE / 2
                        val fineZ = z * FINE + FINE / 2
                        if (fits(sailable, fineX, fineZ, angle, width, length)) {
                            return Mooring(fineX, fineZ, angle)
                        }
                    }
                }
            }
        }
        return null
    }

    fun pack(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    fun unpackX(packed: Long): Int = (packed shr 32).toInt()

    fun unpackZ(packed: Long): Int = packed.toInt()

    private const val FINE = 128
    private const val ANGLE_FULL = 2048
    private const val SAMPLE_STEP = 0.5
    private const val EPSILON = 1e-6
    private const val MAX_MOORING_DISTANCE = 8

    private const val SOLID_MASK =
        CollisionFlag.LOC or
            CollisionFlag.WALL_NORTH or
            CollisionFlag.WALL_EAST or
            CollisionFlag.WALL_SOUTH or
            CollisionFlag.WALL_WEST or
            CollisionFlag.WALL_NORTH_EAST or
            CollisionFlag.WALL_NORTH_WEST or
            CollisionFlag.WALL_SOUTH_EAST or
            CollisionFlag.WALL_SOUTH_WEST

    private val SIDES =
        listOf(
            Triple(1, 0, 0),
            Triple(-1, 0, 0),
            Triple(0, 1, 512),
            Triple(0, -1, 512),
        )
}
