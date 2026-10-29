package org.rsmod.content.areas.tutorialisland.pack

import dev.openrune.cache.tools.TaskPriority
import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDefinition
import dev.openrune.map.npc.MapNpcListEncoder
import dev.openrune.map.packing.MapNpcPacker
import dev.openrune.rscm.RSCM.asRSCM
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.io.File
import java.nio.ByteBuffer
import java.util.Properties
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey

class AccountGuideSpawnTask(private val config: File) : CacheTask() {
    override val priority = TaskPriority.GAMEVALS_ENCODE

    override fun init(cache: Cache) {
        val props = Properties().apply { config.reader().use { load(it) } }
        val type = checkNotNull(props.getProperty("npc")).asRSCM()
        val coords = MapNpcPacker.parseCoordGrid(checkNotNull(props.getProperty("coords")))
        val key = MapSquareKey.from(coords)
        val local = MapSquareGrid.from(coords)
        val spawns = IntArrayList()
        cache.data(5, (key.x shl 8) or key.z, 5)?.let { bytes ->
            val buffer = ByteBuffer.wrap(bytes)
            repeat(buffer.short.toInt() and 0xffff) { spawns.add(buffer.int) }
        }
        if (spawns.any { MapNpcDefinition(it).id == type }) return
        spawns.add(MapNpcDefinition(type, local.x, local.z, coords.level).packed)
        MapNpcListEncoder.encodeAll(cache, mapOf(key to MapNpcListDefinition(spawns)))
    }
}
