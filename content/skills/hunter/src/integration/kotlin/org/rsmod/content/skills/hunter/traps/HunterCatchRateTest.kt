package org.rsmod.content.skills.hunter.traps

import org.junit.jupiter.api.Test
import org.rsmod.api.table.hunter.HunterTrapCreatureRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.utils.skills.SkillingSuccessRate

class HunterCatchRateTest {
    @Test
    fun GameTestState.`catch rates match the wiki's Mod Ash figures`() =
        runGameTest {
            val rows = HunterTrapCreatureRow.all().associateBy { it.npc.internalName }
            fun rate(npc: String, level: Int): Double {
                val row = rows.getValue(npc)
                return SkillingSuccessRate.successRate(row.low, row.high, level, 99)
            }
            assertEquals(101 / 256.0, rate("npc.hunting_bird_jungle", 1))
            assertEquals(146 / 256.0, rate("npc.hunting_chinchompa", 53))
            assertEquals(117 / 256.0, rate("npc.hunting_chinchompa_big", 63))
            assertEquals(229 / 256.0, rate("npc.hunting_chinchompa_black", 99))
        }

    @Test
    fun GameTestState.`trap limit rises at 40, 60 and 80`() =
        runGameTest {
            assertEquals(2, HunterTrapScript.trapLimit(1))
            assertEquals(2, HunterTrapScript.trapLimit(39))
            assertEquals(3, HunterTrapScript.trapLimit(40))
            assertEquals(4, HunterTrapScript.trapLimit(60))
            assertEquals(5, HunterTrapScript.trapLimit(80))
        }
}
