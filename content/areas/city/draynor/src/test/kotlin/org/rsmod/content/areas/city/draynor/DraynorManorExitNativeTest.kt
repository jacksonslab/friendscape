package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.loc.MapLocListDefinition
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DraynorManorExitNativeTest {
    @Test
    fun `the manor is sealed while its inner doors and back door are shut`() {
        val manor = Manor(openable = PANELLED_DOORS)
        assertTrue(manor.canWalk(HALL, SPADE_ROOM))
        assertFalse(manor.canWalk(HALL, GARDEN))
    }

    @Test
    fun `the back door is the way out of the hall to the compost heap`() {
        val manor = Manor(openable = PANELLED_DOORS + BACK_DOOR)
        assertTrue(manor.canWalk(HALL, GARDEN))
        assertTrue(manor.canWalk(GARDEN, manor.besideCompostHeap()))
    }

    private class Manor(openable: Set<String>) {
        private val collision = CollisionFlagMap()
        private val steps = StepValidator(collision)
        private val openIds = openable.map { it.asRSCM() }.toSet()

        init {
            val base = SQUARE.toCoords(0)
            for (x in base.x until base.x + 64 step 8) {
                for (z in base.z until base.z + 64 step 8) {
                    collision.allocateIfAbsent(x, z, 0)
                }
            }
            val kept = LongArrayList()
            for (packed in mapLocs.spawns.longIterator()) {
                if (MapLocDefinition(packed).id !in openIds) kept.add(packed)
            }
            GameMapDecoder.putMaps(collision, SQUARE, mapTiles)
            GameMapDecoder.putLocs(GameMapBuilder(), collision, SQUARE, mapTiles, MapLocListDefinition(kept))
        }

        fun besideCompostHeap(): CoordGrid {
            val heap =
                mapLocs.spawns.map(::MapLocDefinition).single {
                    it.level == 0 && it.id == "loc.hauntedcompostheap".asRSCM()
                }
            val tile = SQUARE.toCoords(0).translate(heap.localX, heap.localZ)
            return OFFSETS.map { (dx, dz) -> tile.translate(dx, dz) }.first { canWalk(GARDEN, it) }
        }

        fun canWalk(from: CoordGrid, to: CoordGrid): Boolean {
            val visited = mutableSetOf(from)
            val pending = ArrayDeque(listOf(from))
            while (pending.isNotEmpty()) {
                val tile = pending.removeFirst()
                if (tile == to) return true
                for ((dx, dz) in OFFSETS) {
                    val next = tile.translate(dx, dz)
                    if (MapSquareKey.from(next) != SQUARE || next in visited) continue
                    if (!steps.canTravel(tile.level, tile.x, tile.z, dx, dz)) continue
                    visited += next
                    pending.add(next)
                }
            }
            return false
        }
    }

    companion object {
        private val HALL = CoordGrid(3108, 3354)
        private val SPADE_ROOM = CoordGrid(3123, 3360)
        private val GARDEN = CoordGrid(3123, 3362)
        private val SQUARE = MapSquareKey.from(HALL)
        private val OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val PANELLED_DOORS = setOf("loc.draynor_panelled_door", "loc.draynor_panelled_door_open")
        private const val BACK_DOOR = "loc.hauntedbackdoor"
        private lateinit var mapTiles: MapTileSimpleDefinition
        private lateinit var mapLocs: MapLocListDefinition

        @JvmStatic
        @BeforeAll
        fun cache() {
            val cache = ServerCacheManager.init(240)
            try {
                mapTiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, SQUARE.id, 0))))
                mapLocs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, SQUARE.id, 1))))
            } finally {
                cache.close()
            }
        }
    }
}
