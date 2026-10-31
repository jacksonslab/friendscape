package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.sailing.FriendscapeSailingObservationsRow
import org.rsmod.api.table.sailing.SailingChartingGenericRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.InvObj

class SpyglassTestDeps @Inject constructor(val held: HeldInteractions, val xpMods: XpModifiers)

@Execution(ExecutionMode.SAME_THREAD)
class SpyglassInteractionTest {
    @Test
    fun GameTestState.`a spyglass discovery awards XP once across repeated interactions`() =
        runInjectedGameTest(SpyglassTestDeps::class, null, SpyglassScript::class) { deps ->
            prepareObservation()
            val before = player.statMap.getXP(STAT_SAILING)
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
            advance(ticks = 5)
            assertEquals(1, player.vars["varbit.sailing_charting_spyglass_the_pandemonium_complete"])
            val rewarded = player.statMap.getXP(STAT_SAILING)
            assertEquals((50.0 * deps.xpMods.get(player, STAT_SAILING)).toInt(), rewarded - before)
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
            advance(ticks = 5)
            assertEquals(rewarded, player.statMap.getXP(STAT_SAILING))
            assertEquals(1, player.inv.count("obj.sailing_charting_spyglass"))
        }

    @Test
    fun GameTestState.`a charting view refuses a player below its Sailing requirement`() =
        runInjectedGameTest(SpyglassTestDeps::class, null, SpyglassScript::class) { deps ->
            prepareObservation()
            val observation = FriendscapeSailingObservationsRow.all().first { it.level > 1 }
            allocZoneCollision(observation.coords)
            player.teleport(observation.coords)
            player.setBaseLevel(STAT_SAILING, 1)
            player.setCurrentLevel(STAT_SAILING, 1)
            val before = player.statMap.getXP(STAT_SAILING)
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
            advance(ticks = 5)
            val word = player.vars["varp.sailing_charting_completion_${observation.uniqueId / 32}"]
            assertEquals(0, word and (1 shl (observation.uniqueId % 32)))
            assertEquals(before, player.statMap.getXP(STAT_SAILING))
        }

    @Test
    fun GameTestState.`moving away while observing prevents completion and XP`() =
        runInjectedGameTest(SpyglassTestDeps::class, null, SpyglassScript::class) { deps ->
            val observation = prepareObservation()
            val before = player.statMap.getXP(STAT_SAILING)
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
            advance()
            val away = observation.coords.translate(10, 10)
            allocZoneCollision(away)
            player.teleport(away)
            advance(ticks = 5)
            assertEquals(0, player.vars["varbit.sailing_charting_spyglass_the_pandemonium_complete"])
            assertEquals(before, player.statMap.getXP(STAT_SAILING))
        }

    @Test
    fun GameTestState.`losing the spyglass during observation prevents completion`() =
        runInjectedGameTest(SpyglassTestDeps::class, null, SpyglassScript::class) { deps ->
            val observation = prepareObservation()
            val before = player.statMap.getXP(STAT_SAILING)
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
            advance()
            player.clearInv()
            advance(ticks = 5)
            assertEquals(0, player.vars["varbit.sailing_charting_spyglass_the_pandemonium_complete"])
            assertEquals(before, player.statMap.getXP(STAT_SAILING))
        }

    @Test
    fun GameTestState.`migrating old landmark progress neither grants XP nor overwrites spyglass flags`() =
        runInjectedGameTest(SpyglassTestDeps::class, null, SpyglassScript::class) { deps ->
            prepareObservation()
            val before = player.statMap.getXP(STAT_SAILING)
            VarPlayerIntMapSetter.set(player, "varp.sailing_charting_completion_0", 1 shl 14)
            for (word in 0 until 12) {
                VarPlayerIntMapSetter.set(player, "varp.friendscape_sailing_charted_$word", -1)
            }
            player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op2) }
            advance(ticks = 2)
            assertEquals(1, player.vars["varbit.sailing_charting_spyglass_the_pandemonium_complete"])
            assertTrue(player.vars["varp.sailing_charting_completion_0"] and (1 shl 4) != 0)
            val khazard = SailingChartingGenericRow.getRow("dbrow.sailing_charting_generic_rowboat_port_khazard").sailingChartingCore
            val word = player.vars["varp.sailing_charting_completion_${khazard.uniqueId / 32}"]
            assertTrue(word and (1 shl (khazard.uniqueId % 32)) != 0)
            assertEquals(before, player.statMap.getXP(STAT_SAILING))
        }

    private fun GameTestScope.prepareObservation(): FriendscapeSailingObservationsRow {
        val observation = FriendscapeSailingObservationsRow.all().first { it.uniqueId == 14 }
        allocZoneCollision(observation.coords)
        player.teleport(observation.coords)
        player.clearInv()
        player.inv[0] = InvObj("obj.sailing_charting_spyglass")
        player.setBaseLevel(STAT_SAILING, 20)
        player.setCurrentLevel(STAT_SAILING, 20)
        return observation
    }
}
