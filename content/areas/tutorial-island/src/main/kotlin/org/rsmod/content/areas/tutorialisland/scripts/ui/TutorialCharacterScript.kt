package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varbits
import org.rsmod.content.areas.tutorialisland.progression.TutorialFocusGuidance
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Character creator confirm + Past Experience selection for Learning the Ropes.
 *
 * `player_design` is opened by [org.rsmod.content.areas.tutorialisland.TutorialIslandBootstrap] on
 * EngineLoginReady. Confirm closes the creator and opens live `tutorial_player_experience` (929)
 * instead of a chat choice, advancing CharCreate â†’ ExpSelect â†’ GielinorTalk.
 */
class TutorialCharacterScript
@Inject
constructor(
    private val progression: TutorialProgression,
    private val focus: TutorialFocusGuidance,
) : PluginScript() {
    private var Player.adventurePaths by
        boolVarBit(tutorial_varbits.ADVENTUREPATH_PLAYER_PARTICIPATING)

    override fun ScriptContext.startup() {
        onIfModalButton(tutorial_components.PLAYER_DESIGN_CONFIRM) { confirmAppearance() }
        onIfModalButton(tutorial_components.EXPERIENCE_BUTTON_NEW) {
            selectExperience(brandNew = true)
        }
        onIfModalButton(tutorial_components.EXPERIENCE_BUTTON_RETURNING) {
            selectExperience(brandNew = false)
        }
        onIfModalButton(tutorial_components.EXPERIENCE_BUTTON_EXPERIENCED) {
            selectExperience(brandNew = false)
        }
    }

    private suspend fun ProtectedAccess.confirmAppearance() {
        if (!progression.isActive(player)) return
        val step = progression.current(player)
        if (step != TutorialStep.CharCreate && step != TutorialStep.ExpSelect) return
        player.appearance.softHidden = false
        ifClose()
        // Live replays Newbie Melody when the creator closes before Past Experience.
        player.midiSong(NEWBIE_MELODY)
        progression.setStep(player, TutorialStep.ExpSelect)
        focus.openPastExperience(player)
    }

    private suspend fun ProtectedAccess.selectExperience(brandNew: Boolean) {
        if (!progression.isActive(player)) return
        if (progression.current(player) != TutorialStep.ExpSelect) return
        player.adventurePaths = brandNew
        ifClose()
        progression.setStep(player, TutorialStep.GielinorTalk)
    }

    private companion object {
        private val NEWBIE_MELODY = "midi.newbie_melody"
    }
}
