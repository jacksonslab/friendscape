package org.rsmod.content.areas.tutorialisland.scripts.ui

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.progression.TutorialFocusGuidance
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep

class TutorialCharacterScriptTest {
    @Test
    fun GameTestState.`login enrolls only fresh accounts and preserves returning positions`() =
        runInjectedGameTest(PastExperienceDependencies::class, null, TutorialCharacterScript::class) { deps ->
            val original = org.rsmod.map.CoordGrid(3222, 3218)
            player.coords = original
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varbit.new_player_account", 0)
            deps.progression.prepareLogin(player)
            assertEquals(false, deps.progression.isActive(player))
            assertEquals(original, player.coords)
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varbit.new_player_account", 1)
            deps.progression.prepareLogin(player)
            assertEquals(true, deps.progression.isActive(player))
            val resumed = player.coords.translateX(2)
            player.coords = resumed
            deps.progression.prepareLogin(player)
            assertEquals(resumed, player.coords)
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varp.tut2_completed", 1)
            deps.progression.prepareLogin(player)
            assertEquals(false, deps.progression.isActive(player))
            assertEquals(resumed, player.coords)
        }

    @Test
    fun GameTestState.`brand new button advances past the experience modal`() =
        runPastExperienceTest(tutorial_components.EXPERIENCE_BUTTON_NEW)

    @Test
    fun GameTestState.`returning button advances past the experience modal`() =
        runPastExperienceTest(tutorial_components.EXPERIENCE_BUTTON_RETURNING)

    @Test
    fun GameTestState.`experienced button advances past the experience modal`() =
        runPastExperienceTest(tutorial_components.EXPERIENCE_BUTTON_EXPERIENCED)

    /**
     * `tutorial_overlay:noclick` swallows every game-view click while it is shown, so leaving the
     * experience modal has to hide it again.
     */
    @Test
    fun GameTestState.`leaving the experience modal hides the click blocker`() =
        runInjectedGameTest(
            PastExperienceDependencies::class,
            null,
            TutorialCharacterScript::class,
        ) { deps ->
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.ExpSelect)
            deps.focus.openPastExperience(player)
            client.clearOutgoing()

            player.ifButton(dev.openrune.ServerCacheManager.fromComponent(tutorial_components.EXPERIENCE_BUTTON_NEW))
            advance()

            val blocker = tutorial_components.TUTORIAL_NOCLICK
            val hides =
                client.outgoingMessages.filterIsInstance<IfSetHide>().filter {
                    it.interfaceId == (blocker.asRSCM() ushr 16) && it.componentId == (blocker.asRSCM() and 65535)
                }
            assertEquals(true, hides.lastOrNull()?.hidden, "Click blocker was left visible.")
        }

    /**
     * The three choices are static components, so their clicks arrive with `comsub == -1`. They are
     * only reachable because `openPastExperience` enables Op1 over the `-1..-1` range; the buttons
     * have no cache-baked events of their own.
     */
    private fun GameTestState.runPastExperienceTest(button: String) =
        runInjectedGameTest(
            PastExperienceDependencies::class,
            null,
            TutorialCharacterScript::class,
        ) { deps ->
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.ExpSelect)
            deps.focus.openPastExperience(player)

            player.ifButton(dev.openrune.ServerCacheManager.fromComponent(button))
            advance()

            assertEquals(TutorialStep.GielinorTalk, deps.progression.current(player))
        }
}

private class PastExperienceDependencies
@Inject
constructor(val progression: TutorialProgression, val focus: TutorialFocusGuidance)
