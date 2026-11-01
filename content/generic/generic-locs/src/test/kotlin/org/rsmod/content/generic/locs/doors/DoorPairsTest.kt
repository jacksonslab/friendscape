package org.rsmod.content.generic.locs.doors

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResourceLock("ServerCacheManager")
class DoorPairsTest {
    private lateinit var pairs: DoorPairs

    @BeforeAll
    fun cache() {
        ServerCacheManager.init(241).close()
        pairs = DoorPairs(ServerCacheManager.getObjects().values)
    }

    @Test
    fun `varrock poor door opens into its open variant and back`() {
        val closed = "loc.fai_varrock_poor_door".asRSCM(RSCMType.LOC)
        val open = "loc.fai_varrock_poor_door_open".asRSCM(RSCMType.LOC)
        assertEquals(open, pairs.nextStage[closed])
        assertEquals(closed, pairs.nextStage[open])
    }

    @Test
    fun `every link joins a closed door to an opened one with the same name`() {
        for ((from, to) in pairs.nextStage) {
            val a = ServerCacheManager.getObject(from)!!
            val b = ServerCacheManager.getObject(to)!!
            assertTrue(a.name.equals(b.name, ignoreCase = true), "${a.internalName} -> ${b.internalName}")
            assertTrue((from in pairs.closed) != (to in pairs.closed), "${a.internalName} -> ${b.internalName}")
        }
    }

    @Test
    fun `name variants map closed and open symbols onto each other`() {
        assertTrue("poordooropen" in DoorPairs.nameVariants("poordoor"))
        assertTrue("poordoor" in DoorPairs.nameVariants("poordooropen"))
        assertTrue("gate_open" in DoorPairs.nameVariants("gate_closed"))
        assertTrue("x_door_open_l" in DoorPairs.nameVariants("x_door_l"))
    }
}
