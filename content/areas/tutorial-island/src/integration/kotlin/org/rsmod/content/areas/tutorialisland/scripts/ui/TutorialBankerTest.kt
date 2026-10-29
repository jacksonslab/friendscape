package org.rsmod.content.areas.tutorialisland.scripts.ui

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import java.nio.ByteBuffer
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.areas.tutorialisland.scripts.locs.TutorialBankScript
import org.rsmod.content.areas.tutorialisland.scripts.npcs.AccountTutorScript
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class TutorialBankerTest {
    @Test
    fun GameTestState.`the live map bankers open banking and the Account Guide is a separate spawn`() =
        runInjectedGameTest(BankerDependencies::class, null, TutorialBankScript::class, AccountTutorScript::class) { deps ->
            val cache = Cache.load(Path.of(".data", "cache", "SERVER"))
            val bytes = try { checkNotNull(cache.data(5, (26 shl 8) or 95, 5)) } finally { cache.close() }
            val buffer = ByteBuffer.wrap(bytes)
            val spawns = List(buffer.short.toInt() and 0xffff) { buffer.int }
            val bankerSpawn = spawns.first {
                ServerCacheManager.getNpc(it and 0xffff)?.name == "Banker"
            }
            val guideSpawn = spawns.first {
                ServerCacheManager.getNpc(it and 0xffff)?.name == "Account Guide"
            }
            assertTrue((bankerSpawn and 0xffff) != (guideSpawn and 0xffff))
            val tile = CoordGrid(26 * 64 + ((bankerSpawn ushr 22) and 63),
                95 * 64 + ((bankerSpawn ushr 16) and 63), bankerSpawn ushr 28)
            allocZoneCollision(tile)
            player.teleport(tile.translateZ(-2))
            player.bank.fillNulls()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.BankOpen)
            val banker = spawnNpc(tile, checkNotNull(ServerCacheManager.getNpc(bankerSpawn and 0xffff)))
            player.opNpc1(banker)
            advance(ticks = 5)
            assertTrue(player.ui.containsModal("interface.bankmain"))
            assertEquals(TutorialStep.PollView, deps.progression.current(player))
        }

    @Test
    fun GameTestState.`talking to the banker across the counter opens the bank and advances the lesson`() =
        runInjectedGameTest(BankerDependencies::class, null, TutorialBankScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile.translateZ(-2))
            player.bank.fillNulls()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.BankOpen)
            placeMapLoc(tile.translateZ(-1), tutorial_locs.BANKBOOTH)
            val banker = spawnNpc(tile, checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.BANKER.asRSCM())))

            player.opNpc1(banker)
            advance(ticks = 5)

            assertTrue(player.ui.containsModal("interface.bankmain"))
            assertEquals(TutorialStep.PollView, deps.progression.current(player))
            assertEquals(25, player.bank.count("obj.coins"))
            assertEquals(tile.translateZ(-2), player.coords)
        }

    @Test
    fun GameTestState.`repeated banker clicks cannot recreate withdrawn tutorial coins`() =
        runInjectedGameTest(BankerDependencies::class, null, TutorialBankScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile.translateZ(-1))
            player.clearInv()
            player.bank.fillNulls()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.BankOpen)
            val banker = spawnNpc(tile, checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.BANKER.asRSCM())))
            player.opNpc1(banker)
            advance(ticks = 3)
            assertEquals(25, player.bank.count("obj.coins"))

            player.bank.fillNulls()
            player.inv[0] = InvObj("obj.coins", 25)
            player.ifClose(deps.events)
            player.opNpc1(banker)
            player.opNpc1(banker)
            advance(ticks = 4)

            assertTrue(player.ui.containsModal("interface.bankmain"))
            assertEquals(0, player.bank.count("obj.coins"))
            assertEquals(25, player.inv.count("obj.coins"))
            assertEquals(TutorialStep.PollView, deps.progression.current(player))
        }

    @Test
    fun GameTestState.`banker cannot skip unfinished tutorial lessons`() =
        runInjectedGameTest(BankerDependencies::class, null, TutorialBankScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile.translateZ(-1))
            player.bank.fillNulls()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.PrayerTab)
            val banker = spawnNpc(tile, checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.BANKER.asRSCM())))
            player.opNpc1(banker)
            advance(ticks = 4)
            assertFalse(player.ui.containsModal("interface.bankmain"))
            assertTrue(player.bank.isEmpty())
            assertEquals(TutorialStep.PrayerTab, deps.progression.current(player))
        }

    @Test
    fun GameTestState.`full bank does not lose existing items when tutorial cash cannot fit`() =
        runInjectedGameTest(BankerDependencies::class, null, TutorialBankScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile.translateZ(-1))
            for (slot in player.bank.indices) player.bank[slot] = InvObj("obj.bronze_dagger")
            val before = player.bank.objs.toList()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.BankOpen)
            val banker = spawnNpc(tile, checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.BANKER.asRSCM())))
            player.opNpc1(banker)
            advance(ticks = 4)
            assertEquals(before, player.bank.objs.toList())
            assertEquals(0, player.bank.count("obj.coins"))
        }

    @Test
    fun GameTestState.`an existing maximum coin stack is preserved`() =
        runInjectedGameTest(BankerDependencies::class, null, TutorialBankScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile.translateZ(-1))
            player.bank.fillNulls()
            player.bank[0] = InvObj("obj.coins", Int.MAX_VALUE)
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.BankOpen)
            val banker = spawnNpc(tile, checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.BANKER.asRSCM())))
            player.opNpc1(banker)
            advance(ticks = 4)
            assertEquals(Int.MAX_VALUE, player.bank.count("obj.coins"))
        }

    @Test
    fun GameTestState.`ultimate ironmen can finish the lesson without opening or crediting a bank`() =
        runInjectedGameTest(BankerDependencies::class, null, TutorialBankScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile.translateZ(-1))
            player.bank.fillNulls()
            player.setGamemode(PlayerGamemode.ULTIMATE_IRONMAN)
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.BankOpen)
            val banker = spawnNpc(tile, checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.BANKER.asRSCM())))
            player.opNpc1(banker)
            advance(ticks = 4)
            assertFalse(player.ui.containsModal("interface.bankmain"))
            assertTrue(player.bank.isEmpty())
            assertEquals(TutorialStep.PollView, deps.progression.current(player))
        }
}

private class BankerDependencies @Inject constructor(val progression: TutorialProgression, val events: EventBus)

private val Player.bank get() = invMap.getOrPut("inv.bank")
