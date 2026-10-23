package org.rsmod.content.skills.hunter.traps

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.BOX_TRAP
import org.rsmod.content.skills.hunter.HunterTestDeps
import org.rsmod.content.skills.hunter.SNARE
import org.rsmod.content.skills.hunter.TRAP_TILE
import org.rsmod.content.skills.hunter.bound
import org.rsmod.content.skills.hunter.expectMessage
import org.rsmod.content.skills.hunter.groundCount
import org.rsmod.content.skills.hunter.layFromSlot
import org.rsmod.content.skills.hunter.npcType
import org.rsmod.content.skills.hunter.obj
import org.rsmod.content.skills.hunter.opHeld1
import org.rsmod.content.skills.hunter.preparePlayer
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj

@Execution(ExecutionMode.SAME_THREAD)
class HunterTrapScriptTest {
    @Test
    fun GameTestState.`laying a snare takes exactly one from the stack`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE, 3)

            val trap = layFromSlot(deps, slot = 0)

            assertEquals(2, player.inv.count(obj(SNARE)))
            assertEquals(TrapState.Laid, trap.state)
            assertTrue(locExists(trap.coords, trap.row.laid))
            assertNotEquals(TRAP_TILE, player.coords)
        }

    @Test
    fun GameTestState.`dismantle returns the trap and frees the tile`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            assertEquals(0, player.inv.count(obj(SNARE)))

            player.opLoc1(trap.bound())
            advance(ticks = 3)

            assertEquals(1, player.inv.count(obj(SNARE)))
            assertNull(deps.traps[TRAP_TILE])
            assertFalse(locExists(TRAP_TILE, trap.row.laid))
        }

    @Test
    fun GameTestState.`dismantle with a full inventory keeps the trap laid`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            player.fillInv()

            player.opLoc1(trap.bound())
            expectMessage("You don't have enough inventory space to do that.")
            advance(ticks = 2)
            assertEquals(0, player.inv.count(obj(SNARE)))
            assertTrue(deps.traps.isActive(trap))
            assertTrue(locExists(TRAP_TILE, trap.row.laid))
        }

    @Test
    fun GameTestState.`lay is refused past the trap limit without taking the item`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE, 3)
            val limit = HunterTrapScript.trapLimit(1)

            repeat(limit) {
                allocZoneCollision(player.coords)
                layFromSlot(deps, slot = 0)
            }
            val before = player.inv.count(obj(SNARE))
            opHeld1(deps, slot = 0)
            expectMessage("You don't have a high enough Hunter level to set up more than $limit traps.")
            advance(ticks = 5)
            assertEquals(before, player.inv.count(obj(SNARE)))
            assertEquals(limit, deps.traps.count(player.uid))
        }

    @Test
    fun GameTestState.`box trap needs level 27 and keeps the item when refused`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 26)
            player.inv[0] = InvObj(BOX_TRAP)

            opHeld1(deps, slot = 0)
            expectMessage("You need a Hunter level of 27 to set up this trap.")
            advance(ticks = 5)
            assertEquals(1, player.inv.count(obj(BOX_TRAP)))
            assertNull(deps.traps[TRAP_TILE])
        }

    @Test
    fun GameTestState.`caught bird is checked for loot, xp and the snare back`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            val bird = spawnPrey("npc.hunting_bird_jungle", trap)

            catchPrey(trap)
            assertFalse(bird.isVisible)
            assertEquals(TrapState.Full, trap.state)

            val xpBefore = player.statMap.getFineXP("stat.hunter")
            player.opLoc1(trap.bound())
            expectMessage("You've caught a crimson swift.")
            assertEquals(1, player.inv.count(obj(SNARE)))
            assertEquals(1, player.inv.count(obj("obj.bones")))
            assertEquals(1, player.inv.count(obj("obj.spit_raw_bird_meat")))
            assertTrue(player.inv.count(obj("obj.hunting_jungle_feather")) in 5..10)
            assertEquals(340, player.statMap.getFineXP("stat.hunter") - xpBefore)
            assertNull(deps.traps[TRAP_TILE])
        }

    @Test
    fun GameTestState.`checking with a full inventory gives nothing and keeps the catch`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            spawnPrey("npc.hunting_bird_jungle", trap)
            catchPrey(trap)
            player.fillInv()
            val xpBefore = player.statMap.getFineXP("stat.hunter")

            player.opLoc1(trap.bound())
            expectMessage("You don't have enough inventory space to do that.")
            advance(ticks = 2)
            assertEquals(xpBefore, player.statMap.getFineXP("stat.hunter"))
            assertEquals(0, player.inv.count(obj(SNARE)))
            assertEquals(0, player.inv.count(obj("obj.bones")))
            assertTrue(deps.traps.isActive(trap))
            assertEquals(TrapState.Full, trap.state)
        }

    @Test
    fun GameTestState.`same-tick check clicks only pay out once`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            spawnPrey("npc.hunting_bird_jungle", trap)
            catchPrey(trap)
            val bound = trap.bound()

            player.opLoc1(bound)
            player.opLoc1(bound)
            player.opLoc2(bound)
            advance(ticks = 1)
            player.opLoc1(bound)
            player.opLoc2(bound)
            advance(ticks = 3)

            assertEquals(1, player.inv.count(obj(SNARE)) + deps.traps.count(player.uid))
            assertEquals(1, player.inv.count(obj("obj.bones")))
            assertEquals(1, player.inv.count(obj("obj.spit_raw_bird_meat")))
        }

    @Test
    fun GameTestState.`another player cannot dismantle or check your trap`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            val thief = registerPlayer(TRAP_TILE.translateX(1))
            thief.clearInv()

            thief.opLoc1(trap.bound())
            expectMessage("This isn't your trap.", target = thief)
            assertEquals(0, thief.inv.count(obj(SNARE)))
            assertTrue(deps.traps.isActive(trap))
        }

    @Test
    fun GameTestState.`failed snare is reset in place without moving items`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            val bird = spawnPrey("npc.hunting_bird_jungle", trap)

            random.next = 100
            advanceUntil({ trap.state == TrapState.Failed }, timeoutTicks = 12)
            assertTrue(bird.isVisible)

            player.opLoc2(trap.bound())
            advance(ticks = 3)

            assertEquals(TrapState.Laid, trap.state)
            assertTrue(locExists(TRAP_TILE, trap.row.laid))
            assertEquals(0, player.inv.count(obj(SNARE)))
        }

    @Test
    fun GameTestState.`unchecked catch collapses into the snare on the floor`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            spawnPrey("npc.hunting_bird_jungle", trap)
            catchPrey(trap)

            advance(ticks = HunterTrapScript.COLLAPSE_TICKS - 2)
            expectMessage("The bird snare that you laid has fallen over.")
            assertNull(deps.traps[TRAP_TILE])
            assertFalse(locExists(TRAP_TILE, checkNotNull(trap.creature).full))
            assertEquals(1, deps.groundCount(TRAP_TILE, SNARE))
            assertEquals(0, player.inv.count(obj("obj.bones")))
        }

    @Test
    fun GameTestState.`walking away collapses the trap`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            val far = TRAP_TILE.translateX(HunterTrapScript.COLLAPSE_DISTANCE + 8)

            allocZoneCollision(far)
            player.teleport(far)
            advance(ticks = 2)

            assertNull(deps.traps[TRAP_TILE])
            assertFalse(locExists(TRAP_TILE, trap.row.laid))
            assertEquals(1, deps.groundCount(TRAP_TILE, SNARE))
        }

    @Test
    fun GameTestState.`logging out collapses every trap`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            layFromSlot(deps, slot = 0)

            eventBus.publish(SessionStateEvent.Logout(player))

            assertEquals(0, deps.traps.count(player.uid))
            assertEquals(1, deps.groundCount(TRAP_TILE, SNARE))
        }

    @Test
    fun GameTestState.`laying from a ground stack takes exactly one`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 27)
            val stack = Obj.fromServer(mapClock, TRAP_TILE, BOX_TRAP, 3)
            deps.objRepo.add(stack, Int.MAX_VALUE)

            deps.objInteractions.interact(player, stack, InteractionOp.Op4)
            advance(ticks = 6)

            assertTrue(deps.traps[TRAP_TILE] != null)
            assertEquals(2, deps.groundCount(TRAP_TILE, BOX_TRAP))
            assertEquals(0, player.inv.count(obj(BOX_TRAP)))
        }

    @Test
    fun GameTestState.`box trap catches a ferret`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 27)
            player.inv[0] = InvObj(BOX_TRAP)
            val trap = layFromSlot(deps, slot = 0)
            spawnPrey("npc.hunting_ferret", trap)

            catchPrey(trap)
            player.opLoc1(trap.bound())
            advance(ticks = 3)

            assertEquals(1, player.inv.count(obj("obj.hunting_ferret")))
            assertEquals(1, player.inv.count(obj(BOX_TRAP)))
        }

    @Test
    fun GameTestState.`creatures above the hunter level always escape`() =
        runInjectedGameTest(HunterTestDeps::class, null, HunterTrapScript::class) { deps ->
            preparePlayer(deps, hunterLevel = 1)
            player.inv[0] = InvObj(SNARE)
            val trap = layFromSlot(deps, slot = 0)
            val bird = spawnPrey("npc.multicoloured_bird", trap)

            advanceUntil({ trap.state != TrapState.Laid }, timeoutTicks = 6)

            assertTrue(trap.state == TrapState.Failing || trap.state == TrapState.Failed)
            assertTrue(bird.isVisible)
        }

    private fun GameTestScope.spawnPrey(npc: String, trap: Trap): Npc =
        spawnNpc(trap.coords.translateZ(1), npcType(npc)) { mode = null }

    private fun GameTestScope.catchPrey(trap: Trap) {
        random.next = 0
        advanceUntil({ trap.state == TrapState.Full }, timeoutTicks = 12)
    }
}
