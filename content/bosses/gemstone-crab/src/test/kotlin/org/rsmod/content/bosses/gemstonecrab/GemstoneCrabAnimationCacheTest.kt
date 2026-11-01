package org.rsmod.content.bosses.gemstonecrab

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcAvatar

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GemstoneCrabAnimationCacheTest {
    @Test
    fun `swamp boss can repeatedly punch and kick using default animation priority`() {
        val type = requireNotNull(ServerCacheManager.getNpc(65518))
        val npc = Npc(type, NpcAvatar(type.size))
        repeat(12) {
            for ((name, id) in listOf("seq.devito_boss_attack" to 63072, "seq.devito_boss_kick" to 63076)) {
                npc.resetAnim()
                npc.anim(name)
                assertEquals(id, npc.pendingSequence.id)
            }
        }
    }

    @Test
    fun `every exported sequence has server timing metadata`() {
        val durations = listOf(100, 50, 67, 120, 120, 100, 70)
        for ((offset, duration) in durations.withIndex()) {
            val seq = ServerCacheManager.getAnim(63070 + offset)
            assertNotNull(seq, "Missing server animation ${63070 + offset}")
            assertEquals(duration, seq!!.totalDelay)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            GameValProvider.load()
            ServerCacheManager.init(241).close()
        }
    }
}
