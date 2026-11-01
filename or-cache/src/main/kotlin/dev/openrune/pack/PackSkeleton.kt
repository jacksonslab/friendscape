package dev.openrune.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache
import java.io.File

class PackSkeleton(private val file: File, private val skeletonId: Int) : CacheTask() {
    override fun init(cache: Cache) {
        cache.write(1, skeletonId, 0, file.readBytes())
    }
}
