package org.rsmod.content.areas.tutorialisland.progression

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varps
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

@Singleton
class TutorialProgression @Inject constructor(private val focus: TutorialFocusGuidance) {
    private var Player.step by enumVarp<TutorialStep>(tutorial_varps.TUTORIAL_2)
    private var Player.completed by boolVarp(tutorial_varps.TUT2_COMPLETED)
    private var Player.enrolled by boolVarp("varp.friendscape_tutorial_started")
    private var Player.newAccount by boolVarBit("varbit.new_player_account")

    fun prepareLogin(player: Player) {
        if (player.newAccount && !player.completed && !player.enrolled) {
            player.enrolled = true
            player.coords = org.rsmod.map.CoordGrid(0, 26, 95, 22, 35)
        }
    }

    fun isActive(player: Player): Boolean = player.enrolled && !player.completed

    fun current(player: Player): TutorialStep = player.step

    fun setStep(player: Player, step: TutorialStep, hintNpc: Npc? = null) {
        if (!isActive(player)) return
        player.step = step
        if (step == TutorialStep.Completed) {
            player.completed = true
            focus.end(player)
            return
        }
        focus.apply(player, step, hintNpc)
    }

    fun advance(player: Player, to: TutorialStep, hintNpc: Npc? = null) {
        if (!isActive(player)) return
        if (player.step.varValue >= to.varValue) {
            // Allow re-applying focus on reconnect / same-step refresh.
            focus.apply(player, player.step, hintNpc)
            return
        }
        setStep(player, to, hintNpc)
    }

    fun requireAtLeast(player: Player, min: TutorialStep): Boolean =
        isActive(player) && player.step.varValue >= min.varValue

    fun deny(player: Player, message: String) {
        player.mes(message)
    }

    fun resume(player: Player) {
        if (!isActive(player)) return
        if (catchUpSmithing(player)) return
        focus.apply(player, player.step)
    }

    /**
     * The bar and dagger steps can't rely on a stat event: `onAdvanceStat` only fires on a level-up,
     * and neither a bronze bar nor a bronze dagger gives enough xp for one. Anything that reads the
     * step during the mining section calls this first, so holding the item is what counts.
     */
    fun catchUpSmithing(player: Player): Boolean {
        if (!isActive(player)) return false
        val next =
            when {
                player.step == TutorialStep.MineSmelt &&
                    player.inv.count(tutorial_objs.BRONZE_BAR) > 0 -> TutorialStep.MineHammer
                player.step == TutorialStep.MineDagger &&
                    player.inv.count(tutorial_objs.BRONZE_DAGGER) > 0 -> TutorialStep.MineGate
                else -> return false
            }
        setStep(player, next)
        return true
    }

    fun completeQuest(player: Player) {
        // Quest complete happens at Wind Strike; leave kit applied on teleport.
        if (player.step.varValue < TutorialStep.LeaveTalk.varValue) {
            setStep(player, TutorialStep.LeaveTalk)
        }
    }

    fun finishAndLeave(player: Player) {
        setStep(player, TutorialStep.Completed)
    }
}
