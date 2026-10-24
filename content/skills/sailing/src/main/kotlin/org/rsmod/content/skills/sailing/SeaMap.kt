package org.rsmod.content.skills.sailing

import dev.openrune.cache.MAPS
import dev.openrune.definition.codec.OverlayCodec
import dev.openrune.filesystem.Cache
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileDefinition
import io.netty.buffer.Unpooled
import jakarta.inject.Singleton
import java.nio.file.Paths
import java.util.BitSet
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Which surface tiles are open water. Rev 241 leaves the open sea without any collision flags, so
 * walkable land and sailable sea look the same to the collision map; the terrain overlay tells
 * them apart. Each map square is decoded the first time a hull touches it.
 */
@Singleton
class SeaMap {
    private val cache: Cache? by lazy {
        runCatching { Cache.load(Paths.get(System.getProperty("user.dir"), ".data", "cache", "SERVER")) }.getOrNull()
    }

    private val squares = HashMap<Int, BitSet>()
    private val waterOverlays = HashMap<Int, Boolean>()

    fun isWater(x: Int, z: Int): Boolean {
        val square = MapSquareKey(x shr 6, z shr 6)
        val water = squares.getOrPut(square.id) { decode(square) }
        return water[((x and 63) shl 6) or (z and 63)]
    }

    private fun decode(square: MapSquareKey): BitSet {
        val water = BitSet(SQUARE_TILES)
        val data = cache?.data(MAPS, square.id, TERRAIN_FILE) ?: return water
        val terrain = runCatching { MapTileDecoder.decode(Unpooled.wrappedBuffer(data)) }.getOrNull() ?: return water
        for (x in 0 until MapSquareGrid.LENGTH) {
            for (z in 0 until MapSquareGrid.LENGTH) {
                if (isOpenWater(terrain, x, z)) {
                    water.set((x shl 6) or z)
                }
            }
        }
        return water
    }

    private fun isOpenWater(terrain: MapTileDefinition, x: Int, z: Int): Boolean {
        val bridged = (terrain.rules[MapSquareGrid(x, z, 1)]?.toInt() ?: 0) and MapTileDefinition.LINK_BELOW != 0
        if (bridged) {
            return false
        }
        val overlay = terrain.overlays[MapSquareGrid(x, z, 0)]?.id ?: return false
        return waterOverlays.getOrPut(overlay) { overlayTexture(overlay) in SEA_TEXTURES }
    }

    private fun overlayTexture(overlay: Int): Int {
        val data = cache?.data(CONFIGS, OVERLAY_GROUP, overlay) ?: return -1
        return runCatching { OverlayCodec().loadData(overlay, Unpooled.wrappedBuffer(data)).texture }.getOrDefault(-1)
    }

    private companion object {
        private const val SQUARE_TILES = 64 * 64
        private const val TERRAIN_FILE = 0
        private const val CONFIGS = 2
        private const val OVERLAY_GROUP = 4

        /** Water textures, as found under every open-sea shipwreck in the cache. */
        private val SEA_TEXTURES = setOf(1) + (130..138) + (160..164) + (170..176) + (182..189)
    }
}
