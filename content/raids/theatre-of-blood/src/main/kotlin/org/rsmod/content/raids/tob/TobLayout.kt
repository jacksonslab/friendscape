package org.rsmod.content.raids.tob

import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

/**
 * Every Theatre room sits in the 4x4 block of map squares 49..52 x 66..69, so one large build slot
 * copies the whole block and a raid's coordinates are the static ones shifted by a fixed offset.
 */
internal object TobLayout {
    private const val MAP_SQUARE_X = 49
    private const val MAP_SQUARE_Z = 66
    private const val MAP_SQUARES = 4
    private const val ZONES_PER_SQUARE = 8

    val STATIC_SOUTH_WEST: CoordGrid = CoordGrid(MAP_SQUARE_X * 64, MAP_SQUARE_Z * 64)
    const val LENGTH: Int = MAP_SQUARES * 64

    val TEMPLATE: RegionStaticTemplate =
        RegionTemplate.createLarge {
            copyAllLevels(MAP_SQUARE_X * ZONES_PER_SQUARE, MAP_SQUARE_Z * ZONES_PER_SQUARE) {
                zoneWidth = MAP_SQUARES * ZONES_PER_SQUARE
                zoneLength = MAP_SQUARES * ZONES_PER_SQUARE
            }
        }

    fun toInstance(base: CoordGrid, static: CoordGrid): CoordGrid =
        CoordGrid(
            base.x + (static.x - STATIC_SOUTH_WEST.x),
            base.z + (static.z - STATIC_SOUTH_WEST.z),
            static.level,
        )

    fun toStatic(base: CoordGrid, coords: CoordGrid): CoordGrid =
        CoordGrid(
            STATIC_SOUTH_WEST.x + (coords.x - base.x),
            STATIC_SOUTH_WEST.z + (coords.z - base.z),
            coords.level,
        )

    fun contains(base: CoordGrid, coords: CoordGrid): Boolean =
        coords.x - base.x in 0 until LENGTH && coords.z - base.z in 0 until LENGTH
}
