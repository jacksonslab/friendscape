package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
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
class PandemoniumNativeMapTest {
    @Test
    fun `a player washed up on the Pandemonium can walk to Steve, Ribs, Old Grog and Junior Jim`() {
        for (npc in listOf(STEVE, RIBS, GROG, JIM)) {
            assertTrue(canReachBeside(WASHED_UP, npc), "Cannot reach the NPC at $npc from $WASHED_UP")
        }
    }

    @Test
    fun `the intro deck holds the player aboard Will and Anne's boat`() {
        assertTrue(canWalk(INTRO_DECK, INTRO_DECK))
        assertFalse(canWalk(INTRO_DECK, CoordGrid(1494, 4511, 1)))
    }

    private fun canReachBeside(from: CoordGrid, npc: CoordGrid): Boolean =
        OFFSETS.any { (dx, dz) -> canWalk(from, npc.translate(dx, dz)) }

    private fun canWalk(from: CoordGrid, to: CoordGrid): Boolean {
        val visited = mutableSetOf(from)
        val pending = ArrayDeque(listOf(from))
        while (pending.isNotEmpty()) {
            val tile = pending.removeFirst()
            if (tile == to) return true
            for ((dx, dz) in OFFSETS) {
                val next = tile.translate(dx, dz)
                if (MapSquareKey.from(next) !in SQUARES || next in visited) continue
                if (!steps.canTravel(tile.level, tile.x, tile.z, dx, dz)) continue
                visited += next
                pending.add(next)
            }
        }
        return false
    }

    companion object {
        private val WASHED_UP = CoordGrid(3060, 2987, 0)
        private val STEVE = CoordGrid(3047, 2979, 0)
        private val RIBS = CoordGrid(3051, 2974, 0)
        private val GROG = CoordGrid(3048, 2980, 0)
        private val JIM = CoordGrid(3064, 2987, 0)
        private val INTRO_DECK = CoordGrid(1499, 4511, 1)
        private val SQUARES = setOf(MapSquareKey.from(WASHED_UP), MapSquareKey.from(INTRO_DECK))
        private val OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val collision = CollisionFlagMap()
        private val steps = StepValidator(collision)

        @JvmStatic
        @BeforeAll
        fun cache() {
            val cache = ServerCacheManager.init(241)
            for (square in SQUARES) {
                val base = square.toCoords(0)
                for (level in 0..3) for (x in base.x until base.x + 64 step 8) for (z in base.z until base.z + 64 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1))))
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, locs)
            }
        }
    }
}
