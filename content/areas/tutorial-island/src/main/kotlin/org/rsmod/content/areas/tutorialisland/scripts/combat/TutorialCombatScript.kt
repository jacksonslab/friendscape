package org.rsmod.content.areas.tutorialisland.scripts.combat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onNpcHit
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.progression.LearningTheRopesJournal
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TutorialCombatScript
@Inject
constructor(private val progression: TutorialProgression, private val players: PlayerList) : PluginScript() {
    override fun ScriptContext.startup() {
        onNpcHit(checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.GIANT_RAT.asRSCM()))) { ratHit() }
        onNpcHit(checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.CHICKEN.asRSCM()))) { chickenHit() }
    }

    private fun NpcHitEvents.Impact.ratHit() {
        if (npc.hitpoints != 0 || hit.damage <= 0 || !hit.isFromPlayer) return
        val player = hit.resolvePlayerSource(players) ?: return
        if (!progression.isActive(player)) return
        val next = ratKillStep(progression.current(player), hit.type) ?: return
        player.mes("You have defeated the giant rat!")
        progression.advance(player, next)
    }

    private fun NpcHitEvents.Impact.chickenHit() {
        if (npc.hitpoints != 0 || hit.damage <= 0 || !hit.isFromPlayer || hit.type != HitType.Magic) return
        if (hit.secondaryType()?.id != "obj.01_wind_strike".asRSCM()) return
        val player = hit.resolvePlayerSource(players) ?: return
        if (!progression.isActive(player)) return
        val step = progression.current(player)
        if (step.varValue !in TutorialStep.MagicRunes.varValue until TutorialStep.LeaveTalk.varValue) return
        player.mes("The chicken is defeated by your Wind Strike!")
        LearningTheRopesJournal.notifyComplete(player)
        progression.completeQuest(player)
    }
}

internal fun ratKillStep(step: TutorialStep, hitType: HitType): TutorialStep? = when {
    step == TutorialStep.CombatMelee && hitType == HitType.Melee -> TutorialStep.CombatRange
    step == TutorialStep.CombatRange && hitType == HitType.Ranged -> TutorialStep.CombatLadder
    else -> null
}
