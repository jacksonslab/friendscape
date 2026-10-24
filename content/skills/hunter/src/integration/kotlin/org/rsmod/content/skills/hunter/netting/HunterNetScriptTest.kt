package org.rsmod.content.skills.hunter.netting

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.righthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.HunterTestDeps
import org.rsmod.content.skills.hunter.TRAP_TILE
import org.rsmod.content.skills.hunter.expectMessage
import org.rsmod.content.skills.hunter.npcType
import org.rsmod.content.skills.hunter.obj
import org.rsmod.content.skills.hunter.preparePlayer
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj

@Execution(ExecutionMode.SAME_THREAD)
class HunterNetScriptTest {
    @Test
    fun GameTestState.`caught butterfly goes into exactly one jar`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterNetScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 15)
            player.righthand = InvObj(NET)
            player.inv[0] = InvObj(JAR)
            player.inv[1] = InvObj(JAR)
            val butterfly = spawnButterfly()
            val xpBefore = player.statMap.getFineXP("stat.hunter")

            random.next = 0
            player.opNpc1(butterfly)
            expectMessage("You manage to catch the butterfly.")

            assertEquals(1, player.inv.count(obj(JAR)))
            assertEquals(1, player.inv.count(obj(RUBY)))
            assertEquals(240, player.statMap.getFineXP("stat.hunter") - xpBefore)
            assertFalse(butterfly.isVisible)
        }

    @Test
    fun GameTestState.`missed butterfly keeps the jar empty`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterNetScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 15)
            player.righthand = InvObj(NET)
            player.inv[0] = InvObj(JAR)
            val butterfly = spawnButterfly()

            random.next = 100
            player.opNpc1(butterfly)
            expectMessage("You fail to catch the butterfly.")

            assertEquals(1, player.inv.count(obj(JAR)))
            assertEquals(0, player.inv.count(obj(RUBY)))
            assertTrue(butterfly.isVisible)
        }

    @Test
    fun GameTestState.`butterfly without a jar is released for xp only`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterNetScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 15)
            player.righthand = InvObj(NET)
            val butterfly = spawnButterfly()

            random.next = 0
            player.opNpc1(butterfly)
            expectMessage("You manage to catch the butterfly, but have no jar to keep it in.")

            assertEquals(0, player.inv.count(obj(RUBY)))
        }

    @Test
    fun GameTestState.`netting below the level does nothing`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterNetScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 14)
            player.righthand = InvObj(NET)
            player.inv[0] = InvObj(JAR)
            val butterfly = spawnButterfly()

            player.opNpc1(butterfly)
            expectMessage("You need a Hunter level of 15 to catch this.")

            assertEquals(1, player.inv.count(obj(JAR)))
            assertTrue(butterfly.isVisible)
        }

    private fun GameTestScope.spawnButterfly(): Npc =
        spawnNpc(TRAP_TILE.translateX(1), npcType("npc.butterfly_ruby")) { mode = null }

    private companion object {
        const val NET = "obj.hunting_butterfly_net"
        const val JAR = "obj.butterfly_jar"
        const val RUBY = "obj.butterfly_jar_ruby"
    }
}
