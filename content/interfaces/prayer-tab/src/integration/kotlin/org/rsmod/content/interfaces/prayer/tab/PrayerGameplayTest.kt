package org.rsmod.content.interfaces.prayer.tab

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.prayer.tab.scripts.PrayerDrainScript
import org.rsmod.content.interfaces.prayer.tab.scripts.PrayerTabScript
import org.rsmod.content.interfaces.prayer.tab.scripts.QuickPrayerScript

class PrayerTestDeps @Inject constructor(val repo: PrayerRepository)

private class PrayerTestModule : AbstractModule() {
    override fun configure() {
        bind(PrayerRepository::class.java).toProvider { PrayerRepository().apply { load() } }.`in`(Scopes.SINGLETON)
    }
}

@Execution(ExecutionMode.SAME_THREAD)
class PrayerGameplayTest {
    @Test
    fun GameTestState.`switching defence prayers replaces the old boost but preserves compatible attack prayers`() =
        runInjectedGameTest(PrayerTestDeps::class, PrayerTestModule(), PrayerTabScript::class, PrayerDrainScript::class) { deps ->
            prepare()
            toggle(deps.repo, "Thick Skin")
            toggle(deps.repo, "Clarity of Thought")
            toggle(deps.repo, "Rock Skin")
            assertEquals(0, player.vars[prayer(deps.repo, "Thick Skin").enabled])
            assertEquals(1, player.vars[prayer(deps.repo, "Rock Skin").enabled])
            assertEquals(1, player.vars[prayer(deps.repo, "Clarity of Thought").enabled])
        }

    @Test
    fun GameTestState.`only one protection prayer can remain active`() =
        runInjectedGameTest(PrayerTestDeps::class, PrayerTestModule(), PrayerTabScript::class, PrayerDrainScript::class) { deps ->
            prepare()
            toggle(deps.repo, "Protect from Magic")
            toggle(deps.repo, "Protect from Melee")
            val melee = prayer(deps.repo, "Protect from Melee")
            assertEquals(0, player.vars[prayer(deps.repo, "Protect from Magic").enabled])
            assertEquals(1, player.vars[melee.enabled])
            assertEquals(melee.overhead, player.overheadIcon)
        }

    @Test
    fun GameTestState.`a prayer requiring a higher level cannot be enabled`() =
        runInjectedGameTest(PrayerTestDeps::class, PrayerTestModule(), PrayerTabScript::class) { deps ->
            prepare()
            player.setBaseLevel("stat.prayer", 1)
            player.setCurrentLevel("stat.prayer", 1)
            toggle(deps.repo, "Protect from Melee")
            assertEquals(0, player.vars[prayer(deps.repo, "Protect from Melee").enabled])
        }

    @Test
    fun GameTestState.`quick prayers recheck locked scroll prayers instead of trusting saved selections`() =
        runInjectedGameTest(PrayerTestDeps::class, PrayerTestModule(), QuickPrayerScript::class, PrayerDrainScript::class) { deps ->
            prepare()
            val locked = prayer(deps.repo, "Rigour")
            assertFalse(locked.hasAllRequirements(player))
            VarPlayerIntMapSetter.set(player, "varbit.quickprayer_selected", 1 shl locked.id)
            quickOrb()
            assertEquals(0, player.vars[locked.enabled])
            assertEquals(0, player.vars["varbit.quickprayer_active"])
        }

    @Test
    fun GameTestState.`quick prayers normalize conflicting saved selections and toggle off cleanly`() =
        runInjectedGameTest(PrayerTestDeps::class, PrayerTestModule(), QuickPrayerScript::class, PrayerDrainScript::class) { deps ->
            prepare()
            val thick = prayer(deps.repo, "Thick Skin")
            val rock = prayer(deps.repo, "Rock Skin")
            VarPlayerIntMapSetter.set(player, "varbit.quickprayer_selected", (1 shl thick.id) or (1 shl rock.id))
            quickOrb()
            assertTrue((player.vars[thick.enabled] + player.vars[rock.enabled]) == 1)
            assertEquals(1, player.vars["varbit.quickprayer_active"])
            quickOrb()
            assertEquals(0, player.vars["varbit.prayer_allactive"])
            assertEquals(0, player.vars["varbit.quickprayer_active"])
        }

    @Test
    fun GameTestState.`the last prayer point disables active prayers in the same tick`() =
        runInjectedGameTest(PrayerTestDeps::class, PrayerTestModule(), PrayerTabScript::class, PrayerDrainScript::class) { deps ->
            prepare()
            player.setCurrentLevel("stat.prayer", 1)
            toggle(deps.repo, "Thick Skin")
            advanceUntil(predicate = { player.prayerLvl == 0 }, timeoutTicks = 100)
            assertEquals(0, player.vars["varbit.prayer_allactive"])
            assertEquals(0, player.vars["varbit.quickprayer_active"])
        }

    private fun GameTestScope.prepare() {
        player.setBaseLevel("stat.prayer", 99)
        player.setCurrentLevel("stat.prayer", 99)
        player.setBaseLevel("stat.defence", 99)
        player.withProtectedAccess {
            ifOpenOverlay("interface.prayerbook", "component.toplevel_osrs_stretch:side5")
            ifOpenOverlay("interface.orbs", "component.toplevel_osrs_stretch:orbs")
        }
        advance()
    }

    private fun prayer(repo: PrayerRepository, name: String): Prayer = repo.prayerList.first { it.name.equals(name, ignoreCase = true) }

    private fun GameTestScope.toggle(repo: PrayerRepository, name: String) {
        val target = prayer(repo, name)
        player.ifButton(repo.prayerComponents.entries.first { it.value.id == target.id }.key)
        advance(ticks = 2)
    }

    private fun GameTestScope.quickOrb() {
        player.ifButton(ServerCacheManager.fromComponent(RSCM.getRSCM("component.orbs:prayerbutton")), op = IfButtonOp.Op1)
        advance(ticks = 2)
    }
}
