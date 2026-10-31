package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class SeaMapTest {
    @Test
    fun `open sea is water and the Port Sarim pier is not`() {
        assertTrue(sea.isWater(3085, 2958))
        assertTrue(sea.isWater(3060, 3193))
        assertFalse(sea.isWater(3045, 3193))
        assertFalse(sea.isWater(3022, 3208))
    }

    @Test
    fun `shoreline tiles only partly covered by water are not sailable`() {
        assertFalse(sea.isWater(3057, 3062))
        assertFalse(sea.isWater(3065, 3062))
        assertTrue(sea.isWater(3060, 3062))
    }

    @Test
    fun `every port has a mooring on open water for every boat`() {
        for (port in Ports.ALL) {
            val plank = port.gangplankCoord
            for (type in BoatTypes.ALL) {
                assertNotNull(Navigation.findMooring({ x, z -> sea.isWater(x, z) }, plank.x, plank.z, type.hullWidth, type.hullLength)) {
                    "No ${type.key} mooring at ${port.name}"
                }
            }
        }
    }

    @Test
    fun `a raft can sail from Port Sarim to the Pandemonium and Musa Point`() {
        val reachable = floodFrom(port("Port Sarim"))
        assertTrue(mooringTile(port("The Pandemonium")) in reachable)
        assertTrue(mooringTile(port("Musa Point")) in reachable)
        assertTrue(mooringTile(port("Rimmington")) in reachable)
    }

    private fun port(name: String): Port = Ports.ALL.first { it.name == name }

    private fun mooringTile(port: Port): Long {
        val plank = port.gangplankCoord
        val mooring = Navigation.findMooring({ x, z -> sea.isWater(x, z) }, plank.x, plank.z, 1, 3)!!
        return Navigation.pack(mooring.fineX / FINE, mooring.fineZ / FINE)
    }

    private fun floodFrom(port: Port): Set<Long> {
        val start = mooringTile(port)
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty() && seen.size < FLOOD_LIMIT) {
            val tile = queue.removeFirst()
            val x = Navigation.unpackX(tile)
            val z = Navigation.unpackZ(tile)
            for ((dx, dz) in STEPS) {
                val next = Navigation.pack(x + dx, z + dz)
                if (next !in seen && sea.isWater(x + dx, z + dz)) {
                    seen += next
                    queue += next
                }
            }
        }
        return seen
    }

    companion object {
        private const val FINE = 128
        private const val FLOOD_LIMIT = 250_000
        private val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        private lateinit var sea: SeaMap

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(241)
            sea = SeaMap()
        }
    }
}
