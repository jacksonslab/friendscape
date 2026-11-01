package org.rsmod.content.bosses.gemstonecrab.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.cache.tools.tasks.impl.PackAnims
import dev.openrune.pack.PackSkeleton
import dev.openrune.pack.PluginPack
import java.io.File

class GemstoneCrabPluginPack : PluginPack() {
    override fun extraTasks(): List<CacheTask> {
        val directory = resourceRoot()?.resolve("animations") ?: return emptyList()
        return listOf(
            PackSkeleton(File(directory, "devito_boss.skeleton"), SKELETON_ID),
            PackAnims(directory),
        )
    }

    private companion object {
        const val SKELETON_ID = 63070
    }
}
