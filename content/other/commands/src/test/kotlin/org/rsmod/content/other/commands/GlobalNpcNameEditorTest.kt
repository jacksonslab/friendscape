package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import java.nio.file.Path
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.GlobalNpcNames
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GlobalNpcNameEditorTest {
    @TempDir lateinit var directory: Path

    @AfterEach fun clearNames() = GlobalNpcNames.replace(emptyMap())

    @Test
    fun `renaming updates every existing copy future copies and a restored editor`() {
        val type = checkNotNull(ServerCacheManager.getNpc(9484))
        val first = Npc(type, CoordGrid(1712, 6133))
        val second = Npc(type, CoordGrid(1714, 6133))
        val sent = mutableListOf<String>()
        first.infoProtocol = object : NpcInfoProtocol by NoopNpcInfo {
            override fun setName(name: String) { sent.add(name) }
        }
        second.infoProtocol = object : NpcInfoProtocol by NoopNpcInfo {
            override fun setName(name: String) { sent.add(name) }
        }
        val npcs = NpcList().apply { this[1] = first; this[2] = second }
        val file = directory.resolve("names.properties")
        val editor = GlobalNpcNameEditor(npcs, NpcNameStore(file))
        editor.load()
        editor.rename(9484, "Friendscape Banker")
        assertEquals(listOf("Friendscape Banker", "Friendscape Banker"), sent)
        assertEquals("Friendscape Banker", first.name)
        assertEquals("Friendscape Banker", Npc(type, CoordGrid(1716, 6133)).name)
        GlobalNpcNames.replace(emptyMap())
        val restarted = GlobalNpcNameEditor(npcs, NpcNameStore(file))
        restarted.load()
        assertEquals("Friendscape Banker", second.name)
        restarted.restore(9484)
        assertEquals(type.name, first.name)
        assertEquals(emptyMap<Int, String>(), NpcNameStore(file).load())
        assertEquals(type.name, sent.last())
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() { ServerCacheManager.init(241).close() }
    }
}
