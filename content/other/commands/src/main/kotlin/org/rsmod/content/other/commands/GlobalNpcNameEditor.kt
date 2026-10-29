package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Path
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.GlobalNpcNames

@Singleton
class GlobalNpcNameEditor internal constructor(private val npcs: NpcList, private val store: NpcNameStore) {
    @Inject constructor(npcs: NpcList) : this(npcs, NpcNameStore(Path.of("config", "npc-names.properties")))
    private val logger = InlineLogger()
    private var overrides: Map<Int, String> = emptyMap()
    private var writable = true

    fun load() {
        try {
            overrides = store.load()
            GlobalNpcNames.replace(overrides)
        } catch (e: Exception) {
            writable = false
            logger.error(e) { "Cannot read config/npc-names.properties; NPC name editing is disabled until it is repaired." }
        }
    }

    fun savedIds(): Set<Int> = overrides.keys

    fun rename(id: Int, name: String) = update(id, NpcNameStore.validateName(name))

    fun restore(id: Int) = update(id, null)

    private fun update(id: Int, name: String?) {
        check(writable) { "The NPC name settings file needs repair before editing." }
        requireNotNull(ServerCacheManager.getNpc(id)) { "That NPC type does not exist." }
        val next = overrides.toMutableMap()
        if (name == null) next.remove(id) else next[id] = name
        store.save(next)
        overrides = next
        GlobalNpcNames.replace(next)
        for (npc in npcs) if (npc.id == id) npc.infoProtocol.setName(npc.name)
    }
}
