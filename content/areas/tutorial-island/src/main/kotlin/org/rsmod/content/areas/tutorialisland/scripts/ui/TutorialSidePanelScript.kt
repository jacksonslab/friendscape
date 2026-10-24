package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.rsmod.api.player.events.SideTabClicked
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.progression.TutorialFocusGuidance
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Advances tutorial steps when side-panel stones / equipment stats are opened.
 *
 * Uses stone overlay buttons instead of [onIfOpen] â€” many tab interfaces already have exclusive
 * IfOpen handlers elsewhere in the codebase.
 */
class TutorialSidePanelScript
@Inject
constructor(
    private val progression: TutorialProgression,
    private val focus: TutorialFocusGuidance,
) : PluginScript() {
    override fun ScriptContext.startup() {
        bindStones(
            "component.toplevel:stone11",
            "component.toplevel_pre_eoc:stone11",
        ) { onSettingsOpened(it) }

        // Combat / Stats / Quest / Inventory / Worn / Prayer / Magic / Account stones.
        bindStones(
            "component.toplevel_osrs_stretch:stone0",
            tutorial_components.FIXED_STONE0,
            tutorial_components.PRE_EOC_STONE0,
        ) {
            onCombatOpened(it)
        }
        onEvent<SideTabClicked> {
            when (tab) {
                1 -> onStatsOpened(player)
                2 -> onQuestListOpened(player)
                5 -> onPrayerOpened(player)
                6 -> onMagicOpened(player)
                11 -> onSettingsOpened(player)
            }
        }
        onIfOverlayButton(tutorial_components.FIXED_STONE1) { onStatsOpened(player) }
        onIfOverlayButton(tutorial_components.PRE_EOC_STONE1) { onStatsOpened(player) }
        bindStones(
            tutorial_components.FIXED_STONE2,
            tutorial_components.PRE_EOC_STONE2,
        ) {
            onQuestListOpened(it)
        }
        bindStones(
            "component.toplevel_osrs_stretch:stone3",
            tutorial_components.FIXED_STONE3,
            tutorial_components.PRE_EOC_STONE3,
        ) {
            onInventoryOpened(it)
        }
        bindStones(
            "component.toplevel_osrs_stretch:stone4",
            tutorial_components.FIXED_STONE4,
            tutorial_components.PRE_EOC_STONE4,
        ) {
            onWornOpened(it)
        }
        bindStones(
            tutorial_components.FIXED_STONE5,
            tutorial_components.PRE_EOC_STONE5,
        ) {
            onPrayerOpened(it)
        }
        bindStones(
            tutorial_components.FIXED_STONE6,
            tutorial_components.PRE_EOC_STONE6,
        ) {
            onMagicOpened(it)
        }
        bindStones(
            "component.toplevel_osrs_stretch:stone8",
            tutorial_components.FIXED_STONE8,
            tutorial_components.PRE_EOC_STONE8,
        ) {
            onAccountOpened(it)
        }
    }

    private fun ScriptContext.bindStones(
        vararg stones: String,
        action: (Player) -> Unit,
    ) {
        for (stone in stones) onIfOverlayButton(stone) { action(player) }
    }

    private fun onSettingsOpened(player: Player) {
        if (!progression.isActive(player)) return
        // The step only advances once the Gielinor Guide is spoken to again, so re-point at him
        // rather than re-applying the same step (which would just repeat "click the spanner").
        if (progression.current(player) == TutorialStep.GielinorSettings) {
            focus.pointBackToBasicsTutor(player)
        }
    }

    private fun onInventoryOpened(player: Player) {
        if (!progression.isActive(player)) return
        if (progression.current(player) == TutorialStep.SurvivalInv) {
            progression.setStep(player, TutorialStep.SurvivalFish)
        }
    }

    private fun onStatsOpened(player: Player) {
        if (!progression.isActive(player)) return
        // After fishing flashes skills (SurvivalSkills); opening the tab unlocks tools talk.
        if (progression.current(player) == TutorialStep.SurvivalSkills) {
            progression.setStep(player, TutorialStep.SurvivalTools)
        }
    }

    private fun onQuestListOpened(player: Player) {
        if (!progression.isActive(player)) return
        if (progression.current(player) == TutorialStep.QuestList) {
            focus.pointBackToQuestTutor(player)
        }
    }

    private fun onWornOpened(player: Player) {
        if (!progression.isActive(player)) return
        when (progression.current(player)) {
            TutorialStep.CombatWorn -> progression.setStep(player, TutorialStep.CombatStats)
            // Equipment-stats button is owned by the equipment plugin (exclusive IfOverlay);
            // opening worn again or talking to Vannaka advances dagger equip.
            TutorialStep.CombatStats -> progression.setStep(player, TutorialStep.CombatDagger)
            else -> Unit
        }
    }

    private fun onCombatOpened(player: Player) {
        if (!progression.isActive(player)) return
        if (progression.current(player) == TutorialStep.CombatStyles) {
            progression.setStep(player, TutorialStep.CombatMelee)
        }
    }

    private fun onAccountOpened(player: Player) {
        if (!progression.isActive(player)) return
        if (progression.current(player) == TutorialStep.AccountMgmt) {
            progression.setStep(player, TutorialStep.AccountMgmt)
        }
    }

    private fun onPrayerOpened(player: Player) {
        if (!progression.isActive(player)) return
        if (progression.current(player) == TutorialStep.PrayerTab) {
            progression.setStep(player, TutorialStep.PrayerTab)
        }
    }

    private fun onMagicOpened(player: Player) {
        if (!progression.isActive(player)) return
        if (progression.current(player) == TutorialStep.MagicTab) {
            progression.setStep(player, TutorialStep.MagicTab)
        }
    }
}
