package org.rsmod.content.areas.tutorialisland.progression

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.configs.tutorial_interfaces
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varbits
import org.rsmod.content.areas.tutorialisland.progression.HintArrows
import org.rsmod.content.areas.tutorialisland.progression.TutorialClientScripts
import org.rsmod.content.areas.tutorialisland.progression.clearHintArrow
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Single hub for Tutorial Island attention cues. Every [TutorialProgression.setStep] routes here.
 *
 * Yellow hint arrows are step-driven: NPC talk steps resolve a nearby instance (or use [hintNpc]
 * when it matches), door/loc steps use tile arrows, and UI flash steps intentionally leave the
 * world arrow cleared so the flashing side icon is the only cue.
 *
 * Overlay chrome matches live OSRS: `tutorial_overlay` on `overlay_atmosphere`, guide text via CS2
 * `mesoverlay` (1974), and side panels closed while the tutorial is active.
 */
@Singleton
class TutorialFocusGuidance
@Inject
constructor(private val eventBus: EventBus, private val npcSearch: NpcSearch) {
    private var Player.flashingSide by intVarBit(tutorial_varbits.FLASHSIDE)

    fun apply(player: Player, step: TutorialStep, hintNpc: Npc? = null) {
        clear(player)
        openOverlay(player)
        applyClickBlocker(player, step)
        applySidePanels(player, step)
        setControlGuide(player, step)
        TutorialClientScripts.tutorialProgressbarSet(player, step.varValue, TutorialStep.MAX_PROGRESS)
        applyHint(player, step, hintNpc)
        applyUiFlash(player, step)
        applyOutlines(player, step)
        applyOreGuide(player, step)
        if (step == TutorialStep.CombatDagger && player.worn.count(tutorial_objs.BRONZE_DAGGER) > 0) {
            pointBackTo(
                player,
                tutorial_npcs.COMBAT_TUTOR,
                guideHtml(step, combatDaggerGuide(player)),
            )
        }
    }

    fun clear(player: Player) {
        player.clearHintArrow()
        TutorialClientScripts.entityHighlightClear(player)
        TutorialClientScripts.highlightingOff(player)
    }

    fun end(player: Player) {
        clear(player)
        // Every panel the tutorial kept locked has to come back, or the player leaves the island
        // with dead side tabs until their next login re-opens them.
        for (panel in SIDE_PANELS) {
            player.openPanel(panel)
        }
        TutorialClientScripts.tutorialEnd(player)
    }

    /**
     * Live first-login order after the overlay is up: drop the click blocker for the Newbie Melody
     * sting, then put it back before the character creator modal opens.
     */
    fun playCharacterCreateSting(player: Player) {
        player.ifSetHide(tutorial_components.TUTORIAL_NOCLICK, hide = true)
        player.midiSong(NEWBIE_MELODY)
        player.ifSetHide(tutorial_components.TUTORIAL_NOCLICK, hide = false)
    }

    /**
     * `tutorial_overlay:noclick` (614:1) is a parent-sized `noClickThrough` layer that swallows
     * every click on the game view. The cache ships it hidden; it is only meant to be shown while a
     * tutorial modal owns the screen, so it has to be hidden again for every ordinary step -
     * otherwise the world stays unclickable for the rest of the session.
     */
    private fun applyClickBlocker(player: Player, step: TutorialStep) {
        val modalStep = step == TutorialStep.CharCreate || step == TutorialStep.ExpSelect
        player.ifSetHide(tutorial_components.TUTORIAL_NOCLICK, hide = !modalStep)
    }

    /**
     * The Gielinor Guide has a second line of dialogue once the settings menu has been opened, but
     * the step value does not change, so nothing in [apply] would move the player's attention back
     * to him. Re-points the arrow and swaps the guide text to say so.
     */
    fun pointBackToBasicsTutor(player: Player) {
        pointBackTo(
            player,
            tutorial_npcs.BASICS_TUTOR,
            "<col=0000ff>Settings menu</col><br>Good. Now speak to the Gielinor Guide again to " +
                "continue.",
        )
    }

    fun pointBackToQuestTutor(player: Player) {
        player.flashingSide = FLASH_SIDE_NONE
        pointBackTo(
            player,
            tutorial_npcs.QUEST_TUTOR,
            "<col=0000ff>Your quest list</col><br>This is your quest list. Talk to the Quest " +
                "Guide again for an explanation.",
        )
    }

    fun pointBackToTabTutor(player: Player, step: TutorialStep) {
        val npcType = when (step) {
            TutorialStep.PrayerTab -> tutorial_npcs.PRAYER_TUTOR
            TutorialStep.MagicTab -> tutorial_npcs.MAGIC_TUTOR
            TutorialStep.AccountMgmt -> tutorial_npcs.BANK_TUTOR
            else -> return
        }
        val body = when (step) {
            TutorialStep.PrayerTab -> "Speak to Brother Brace again to learn how to use your prayers."
            TutorialStep.MagicTab -> "Speak to the Magic Instructor again to receive runes and learn your first spell."
            else -> "Speak to the Account Guide again to continue."
        }
        player.flashingSide = FLASH_SIDE_NONE
        player.ifSetText(tutorial_components.HINT_TEXT_2, body)
        pointBackTo(player, npcType, guideHtml(step, body))
    }

    private fun pointBackTo(player: Player, npcType: String, text: String) {
        val npc = npcSearch.find(player.coords, npcType, HINT_NPC_DISTANCE, HuntVis.Off)
        if (npc != null) {
            HintArrows.setNpc(player, npc)
        }
        TutorialClientScripts.mesOverlay(player, text)
    }

    fun setControlGuide(player: Player, step: TutorialStep) {
        val body = when (step) {
            TutorialStep.CombatDagger -> combatDaggerGuide(player)
            TutorialStep.CombatRange -> rangedGuide(player)
            else -> step.guideBody
        }
        player.ifSetText(tutorial_components.HINT_TEXT_1, step.guideTitle)
        player.ifSetText(tutorial_components.HINT_TEXT_2, body)
        player.ifSetText(tutorial_components.HINT_TEXT_3, "")
        player.ifSetText(tutorial_components.HINT_TEXT_4, "")
        player.ifOpenOverlay(tutorial_interfaces.MESOVERLAY, "component.chatbox:chatmodal", eventBus)
        TutorialClientScripts.mesOverlay(player, guideHtml(step, body))
    }

    private fun combatDaggerGuide(player: Player): String =
        if (player.worn.count(tutorial_objs.BRONZE_DAGGER) > 0) {
            "Your bronze dagger is equipped. Speak to the combat instructor again to get your next equipment."
        } else {
            TutorialStep.CombatDagger.guideBody
        }

    private fun rangedGuide(player: Player): String = when {
        player.worn.count(tutorial_objs.SHORTBOW) > 0 &&
            player.worn.count(tutorial_objs.BRONZE_ARROW) > 0 ->
            "You are ready for ranged combat. Attack a giant rat from outside the pen using your shortbow."
        player.worn.count(tutorial_objs.SHORTBOW) > 0 ->
            "Your shortbow is equipped. Equip the bronze arrows in your backpack, then attack a giant rat from outside the pen."
        player.inv.count(tutorial_objs.SHORTBOW) > 0 -> TutorialStep.CombatRange.guideBody
        else -> "Well done! Speak to the combat instructor again to learn about ranged combat and receive a shortbow and arrows."
    }

    private fun rangedTarget(player: Player): String =
        if (player.worn.count(tutorial_objs.SHORTBOW) > 0) tutorial_npcs.GIANT_RAT
        else tutorial_npcs.COMBAT_TUTOR

    /**
     * Live Past Experience modal (`tutorial_player_experience` / 929) on the main modal slot.
     *
     * The three choices are static components, not children of the content layer (929:4 decodes
     * with no children), so Op1 is enabled per button over the static `-1..-1` range. Clicks then
     * arrive as `comsub == -1` on each button.
     */
    fun openPastExperience(player: Player) {
        player.ifOpenMainModal(tutorial_interfaces.TUTORIAL_PLAYER_EXPERIENCE, eventBus)
        player.ifSetEvents(tutorial_components.EXPERIENCE_BUTTON_NEW, -1..-1, IfEvent.Op1)
        player.ifSetEvents(tutorial_components.EXPERIENCE_BUTTON_RETURNING, -1..-1, IfEvent.Op1)
        player.ifSetEvents(tutorial_components.EXPERIENCE_BUTTON_EXPERIENCED, -1..-1, IfEvent.Op1)
    }

    private fun openOverlay(player: Player) {
        // Live opens `tutorial_overlay` on toplevel:overlay_atmosphere (164:1 / stretch equiv).
        // `tutorial_progressbar_init` is not sent from here: 614:0 carries a baked onLoad hook
        // that runs it with the six component ids it needs as soon as the overlay opens.
        player.ifOpenOverlay(
            tutorial_interfaces.TUTORIAL_OVERLAY,
            "component.toplevel_osrs_stretch:overlay_atmosphere",
            eventBus,
        )
    }

    /**
     * Matches OSRS first-login: side panels start closed and unlock one at a time as the tutorial
     * reaches them (orbs / XP drops stay throughout).
     *
     * A side tab is a sub-interface the gameframe opens into `toplevel_target_sideN` once, at
     * login; clicking a stone is a client-side switch between whatever is already open. So closing
     * a panel here is what locks the tab - and the panel has to be re-opened for the tab to do
     * anything at all, which is why this cannot simply close everything on every step.
     */
    private fun applySidePanels(player: Player, step: TutorialStep) {
        for (panel in SIDE_PANELS) {
            if (step.varValue >= panel.unlock.varValue) {
                player.openPanel(panel)
            } else {
                player.closePanel(panel)
            }
        }
    }

    private fun Player.openPanel(panel: SidePanel) {
        if (!ui.containsOverlay(panel.interf)) {
            ifOpenOverlay(panel.interf, panel.target, eventBus)
        }
    }

    private fun Player.closePanel(panel: SidePanel) {
        if (ui.containsOverlay(panel.interf)) {
            ifCloseOverlay(panel.interf, eventBus)
        }
    }

    private fun applyHint(player: Player, step: TutorialStep, hintNpc: Npc?) {
        val tile = hintTile(step)
        if (tile != null) {
            HintArrows.setTile(player, tile)
            return
        }
        val type = if (step == TutorialStep.CombatRange) rangedTarget(player) else hintNpcType(step)
        if (type == null) return
        val npc =
            hintNpc?.takeIf { it.id == type.asRSCM() }
                ?: npcSearch.find(player.coords, type, HINT_NPC_DISTANCE, HuntVis.Off)
        if (npc != null) {
            HintArrows.setNpc(player, npc)
        }
    }

    private fun applyUiFlash(player: Player, step: TutorialStep) {
        player.flashingSide = FLASH_SIDE_NONE
        val side =
            when (step) {
                TutorialStep.GielinorSettings -> 11
                TutorialStep.SurvivalInv -> 3
                TutorialStep.SurvivalSkills -> 1
                TutorialStep.QuestList -> constants.toplevel_details
                TutorialStep.CombatWorn -> 4
                TutorialStep.CombatStyles -> 0
                TutorialStep.AccountMgmt -> 8
                TutorialStep.PrayerTab -> constants.toplevel_prayer
                TutorialStep.MagicTab -> 6
                TutorialStep.CombatStats -> {
                    TutorialClientScripts.equipmentIconFlash(player)
                    return
                }
                TutorialStep.MagicRunes,
                TutorialStep.MagicCast -> {
                    TutorialClientScripts.magicFlashSpell(player, tutorial_components.SPELL_WIND_STRIKE)
                    return
                }
                TutorialStep.HomeTele -> {
                    TutorialClientScripts.magicFlashSpell(player, tutorial_components.SPELL_HOME_TELEPORT)
                    return
                }
                else -> return
            }
        // `flashside` is 4 bits holding the side id plus one, leaving 0 for "nothing is flashing".
        // Confirmed in-game: the settings step flashes the spanner. `toplevel_flashicon` (913) is
        // a proc taking 3 ints whose meaning has not been recovered, so it is not called here.
        // The client suppresses the tutorial cue for the currently selected tab.
        if (step == TutorialStep.AccountMgmt || step == TutorialStep.PrayerTab || step == TutorialStep.MagicTab) {
            TutorialClientScripts.toplevelSidebuttonSwitch(player, 3)
        }
        player.flashingSide = side + 1
    }

    private fun applyOutlines(player: Player, step: TutorialStep) {
        TutorialClientScripts.highlightingOn(player)
        val npcType = if (step == TutorialStep.CombatRange) rangedTarget(player) else outlineNpcType(step)
        if (npcType != null) {
            TutorialClientScripts.highlightNpcTypeOn(player, npcType.asRSCM())
        }

        val locType = if (step == TutorialStep.MineOres) nextOreRock(player) else outlineLocType(step)
        if (locType != null) {
            TutorialClientScripts.highlightLocTypeOn(player, locType.asRSCM())
        }
    }

    /**
     * Both ores are one step, so without this the tin outline stays up after the tin is mined and
     * the player keeps mining tin, never told that the step is waiting on copper.
     */
    private fun nextOreRock(player: Player): String {
        val hasTin = player.inv.count(tutorial_objs.TIN_ORE) > 0
        val hasCopper = player.inv.count(tutorial_objs.COPPER_ORE) > 0
        return if (hasTin && !hasCopper) tutorial_locs.COPPER_ROCK else tutorial_locs.TIN_ROCK
    }

    private fun applyOreGuide(player: Player, step: TutorialStep) {
        if (step != TutorialStep.MineOres) return
        val hasTin = player.inv.count(tutorial_objs.TIN_ORE) > 0
        val hasCopper = player.inv.count(tutorial_objs.COPPER_ORE) > 0
        val text =
            when {
                hasTin && !hasCopper -> "Now mine one of the copper rocks to get some copper ore."
                hasCopper && !hasTin -> "Now mine one of the tin rocks to get some tin ore."
                else -> return
            }
        TutorialClientScripts.mesOverlay(player, "<col=0000ff>Mining</col><br>$text")
    }

    /**
     * Tiles are the decoded map positions of each loc, not estimates: every one of these was read
     * out of the rev-239 mapsquare loc lists for 26_95 and 26_195.
     *
     * The chef door is the one exception. It is a wall door on the west edge of 7,12, so an arrow
     * on the loc's own tile lands on the square the player already stands on rather than on the
     * doorway; it points at 6,12, the tile through the gap.
     */
    private fun hintTile(step: TutorialStep): CoordGrid? =
        when (step) {
            TutorialStep.GielinorDoor -> CoordGrid(0, 26, 95, 26, 35)
            TutorialStep.SurvivalGate -> CoordGrid(0, 26, 95, 17, 20)
            TutorialStep.ChefDoor -> CoordGrid(0, 26, 95, 6, 12)
            TutorialStep.ChefExit -> CoordGrid(0, 26, 95, 0, 18)
            TutorialStep.QuestDoor -> CoordGrid(0, 26, 95, 14, 54)
            TutorialStep.QuestLadder -> CoordGrid(0, 26, 95, 16, 47)
            TutorialStep.MineGate -> CoordGrid(0, 26, 195, 22, 31)
            TutorialStep.CombatLadder -> CoordGrid(0, 26, 195, 39, 54)
            TutorialStep.BankOpen -> CoordGrid(0, 26, 95, 48, 52)
            TutorialStep.PollView -> CoordGrid(0, 26, 95, 47, 49)
            TutorialStep.AccountExit -> CoordGrid(0, 26, 95, 58, 52)
            TutorialStep.PrayerExit -> CoordGrid(0, 26, 95, 57, 34)
            else -> null
        }

    private fun hintNpcType(step: TutorialStep): String? =
        when (step) {
            TutorialStep.GielinorTalk -> tutorial_npcs.BASICS_TUTOR
            TutorialStep.SurvivalTalk,
            TutorialStep.SurvivalTools -> tutorial_npcs.SURVIVAL_TUTOR
            TutorialStep.SurvivalFish -> tutorial_npcs.FISHING_SPOT
            TutorialStep.ChefTalk -> tutorial_npcs.NAV_TUTOR
            TutorialStep.QuestTalk -> tutorial_npcs.QUEST_TUTOR
            TutorialStep.MineTalk,
            TutorialStep.MineHammer -> tutorial_npcs.MINING_TUTOR
            TutorialStep.CombatTalk,
            TutorialStep.CombatGear,
            TutorialStep.CombatRange -> tutorial_npcs.COMBAT_TUTOR
            TutorialStep.CombatMelee -> tutorial_npcs.GIANT_RAT
            TutorialStep.AccountTalk,
            TutorialStep.AccountMgmt -> tutorial_npcs.BANK_TUTOR
            TutorialStep.PrayerTalk,
            TutorialStep.PrayerTab -> tutorial_npcs.PRAYER_TUTOR
            TutorialStep.Ironman -> tutorial_npcs.IRONMAN_TUTOR
            TutorialStep.MagicTalk,
            TutorialStep.MagicTab,
            TutorialStep.LeaveTalk -> tutorial_npcs.MAGIC_TUTOR
            TutorialStep.MagicRunes,
            TutorialStep.MagicCast -> tutorial_npcs.CHICKEN
            else -> null
        }

    private fun outlineNpcType(step: TutorialStep): String? =
        when (step) {
            TutorialStep.GielinorTalk -> tutorial_npcs.BASICS_TUTOR
            TutorialStep.SurvivalTalk,
            TutorialStep.SurvivalTools -> tutorial_npcs.SURVIVAL_TUTOR
            TutorialStep.SurvivalFish -> tutorial_npcs.FISHING_SPOT
            TutorialStep.ChefTalk -> tutorial_npcs.NAV_TUTOR
            TutorialStep.QuestTalk,
            TutorialStep.QuestList -> tutorial_npcs.QUEST_TUTOR
            TutorialStep.MineTalk,
            TutorialStep.MineHammer -> tutorial_npcs.MINING_TUTOR
            TutorialStep.CombatTalk,
            TutorialStep.CombatGear,
            TutorialStep.CombatRange -> tutorial_npcs.COMBAT_TUTOR
            TutorialStep.CombatMelee -> tutorial_npcs.GIANT_RAT
            TutorialStep.AccountTalk,
            TutorialStep.AccountMgmt -> tutorial_npcs.BANK_TUTOR
            TutorialStep.PrayerTalk,
            TutorialStep.PrayerTab -> tutorial_npcs.PRAYER_TUTOR
            TutorialStep.Ironman -> tutorial_npcs.IRONMAN_TUTOR
            TutorialStep.MagicTalk,
            TutorialStep.MagicTab,
            TutorialStep.LeaveTalk -> tutorial_npcs.MAGIC_TUTOR
            TutorialStep.MagicRunes,
            TutorialStep.MagicCast -> tutorial_npcs.CHICKEN
            else -> null
        }

    private fun outlineLocType(step: TutorialStep): String? =
        when (step) {
            TutorialStep.GielinorDoor -> tutorial_locs.SURVIVAL_ENTRY
            TutorialStep.SurvivalWc -> tutorial_locs.TREE
            TutorialStep.SurvivalGate -> tutorial_locs.SURVIVAL_EXIT_L
            TutorialStep.ChefDoor -> tutorial_locs.NAV_ENTRY
            TutorialStep.ChefBread -> tutorial_locs.RANGE
            TutorialStep.ChefExit -> tutorial_locs.NAV_EXIT
            TutorialStep.QuestDoor -> tutorial_locs.QUEST_ENTRY
            TutorialStep.QuestLadder -> tutorial_locs.QUEST_LADDER
            TutorialStep.MineOres -> tutorial_locs.TIN_ROCK
            TutorialStep.MineSmelt -> tutorial_locs.FURNACE
            TutorialStep.MineDagger -> tutorial_locs.ANVIL
            TutorialStep.MineGate -> tutorial_locs.MINING_EXIT_L
            TutorialStep.CombatLadder -> tutorial_locs.COMBAT_LADDER
            TutorialStep.BankOpen -> tutorial_locs.BANKBOOTH
            TutorialStep.PollView -> tutorial_locs.POLLBOOTH
            TutorialStep.AccountExit -> tutorial_locs.BANK_EXIT
            TutorialStep.PrayerExit -> tutorial_locs.PRAYER_EXIT_L
            else -> null
        }

    private data class SidePanel(
        val interf: String,
        val target: String,
        val unlock: TutorialStep,
    )

    private companion object {
        /** Wide enough to cover each tutorial area from its entrance teleports. */
        private const val HINT_NPC_DISTANCE = 64

        /** `flashside` value that leaves every side icon alone. */
        private const val FLASH_SIDE_NONE = 0

        /**
         * Targets mirror the gameframe's own `StandardOverlays` mapping. Each panel unlocks on the
         * step that first asks the player to use it; the social/emote/music tabs stay locked for
         * the whole tutorial and come back in [end].
         */
        private val SIDE_PANELS =
            listOf(
                SidePanel(
                    "interface.settings_side",
                    "component.toplevel_osrs_stretch:side11",
                    TutorialStep.GielinorSettings,
                ),
                SidePanel(
                    "interface.inventory",
                    "component.toplevel_osrs_stretch:side3",
                    TutorialStep.SurvivalInv,
                ),
                SidePanel(
                    "interface.stats",
                    "component.toplevel_osrs_stretch:side1",
                    TutorialStep.SurvivalSkills,
                ),
                SidePanel(
                    "interface.side_journal",
                    "component.toplevel_osrs_stretch:side2",
                    TutorialStep.QuestList,
                ),
                SidePanel(
                    "interface.wornitems",
                    "component.toplevel_osrs_stretch:side4",
                    TutorialStep.CombatWorn,
                ),
                SidePanel(
                    "interface.combat_interface",
                    "component.toplevel_osrs_stretch:side0",
                    TutorialStep.CombatStyles,
                ),
                SidePanel(
                    "interface.account",
                    "component.toplevel_osrs_stretch:side8",
                    TutorialStep.AccountMgmt,
                ),
                SidePanel(
                    "interface.prayerbook",
                    "component.toplevel_osrs_stretch:side5",
                    TutorialStep.PrayerTab,
                ),
                SidePanel(
                    "interface.magic_spellbook",
                    "component.toplevel_osrs_stretch:side6",
                    TutorialStep.MagicTab,
                ),
                SidePanel(
                    "interface.logout",
                    "component.toplevel_osrs_stretch:side10",
                    TutorialStep.Completed,
                ),
                SidePanel(
                    "interface.friends",
                    "component.toplevel_osrs_stretch:side9",
                    TutorialStep.Completed,
                ),
                SidePanel(
                    "interface.side_channels",
                    "component.toplevel_osrs_stretch:side7",
                    TutorialStep.Completed,
                ),
                SidePanel(
                    "interface.emote",
                    "component.toplevel_osrs_stretch:side12",
                    TutorialStep.Completed,
                ),
                SidePanel(
                    "interface.music",
                    "component.toplevel_osrs_stretch:side13",
                    TutorialStep.Completed,
                ),
            )

        /** Live `midi_song_v2 id=62` (Newbie Melody) on first character-create open. */
        private val NEWBIE_MELODY = "midi.newbie_melody"

        private fun guideHtml(step: TutorialStep, body: String = step.guideBody): String =
            "<col=0000ff>${step.guideTitle}</col><br>$body"
    }
}
