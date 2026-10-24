package org.rsmod.content.areas.tutorialisland.scripts.skills

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpcU
import org.rsmod.api.script.onOpNpcU
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_seqs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.areas.tutorialisland.progression.tutorialExchange
import org.rsmod.content.skills.cooking.DoughMakingRequest
import org.rsmod.content.skills.smithing.smelting.SmeltingScript
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Island-scoped skill interactions for Learning the Ropes.
 *
 * Level-3 caps / HP rules live in
 * [org.rsmod.content.areas.tutorialisland.scripts.rules.TutorialIslandRulesScript].
 */
class TutorialSkillsScript
@Inject
constructor(
    private val progression: TutorialProgression,
    private val locRepo: LocRepository,
    private val smithing: SmeltingScript,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.FISHING_SPOT) { fish() }
        onApNpc1(tutorial_npcs.FISHING_SPOT) {
            if (isWithinApRange(it.npc, 1)) fish()
        }
        onOpNpcU(tutorial_npcs.FISHING_SPOT) { fishWith(it.objType) }
        val spotType = ServerCacheManager.getNpc(tutorial_npcs.FISHING_SPOT.asRSCM())!!
        onApNpcU(spotType) { if (isWithinApRange(it.npc, 1)) fishWith(it.objType) }
        onOpLoc1(tutorial_locs.TREE) { chop() }
        onOpLoc1(tutorial_locs.TREE2) { chop() }
        onOpLoc1(tutorial_locs.OAK) { oakDeny() }
        onOpHeldU(tutorial_objs.TINDERBOX, tutorial_objs.TUT2_LOGS) { lightFire() }
        onOpLocU(tutorial_locs.FIRE, tutorial_objs.TUT2_RAW_SHRIMP) { cookShrimp() }
        onEvent<DoughMakingRequest> {
            if (water == tutorial_objs.BUCKET_WATER && progression.isActive(access.player)) {
                handled = true
                access.mixDough()
            }
        }
        onOpLoc1(tutorial_locs.RANGE) { cookBread() }
        onOpLocU(tutorial_locs.RANGE, tutorial_objs.BREAD_DOUGH) { cookBread() }
        onOpLoc1(tutorial_locs.TIN_ROCK) { mineTin() }
        onOpLoc1(tutorial_locs.COPPER_ROCK) { mineCopper() }
        onOpLoc1(tutorial_locs.FURNACE) { smelt() }
        onOpLocU(tutorial_locs.FURNACE, tutorial_objs.TIN_ORE) { smelt() }
        onOpLocU(tutorial_locs.FURNACE, tutorial_objs.COPPER_ORE) { smelt() }
        // No anvil binding: the island's anvil is the shared `anvil` loc, and a type-level handler
        // wins over a content-level one everywhere - so binding it here would put the tutorial's
        // one-recipe script on every anvil in the game. The smithing module's content handler
        // serves it instead; `advanceOnSmithing` below is what keeps progression moving.
    }

    private fun ProtectedAccess.fishWith(obj: ItemServerType) {
        if (obj.id != tutorial_objs.NET.asRSCM()) {
            mes("Nothing interesting happens.")
            return
        }
        fish()
    }

    private fun ProtectedAccess.fish() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalInv)) {
            progression.deny(
                player,
                "You cannot fish here yet. You must progress further in the tutorial.",
            )
            return
        }
        if (!inv.contains(tutorial_objs.NET)) {
            mes("You need a small fishing net to catch shrimp.")
            return
        }
        if (!tutorialExchange(emptyList(), listOf(tutorial_objs.TUT2_RAW_SHRIMP to 1))) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }
        anim(tutorial_seqs.SMALL_NET)
        statAdvance("stat.fishing", FISHING_XP)
        mes("You manage to catch some shrimp.")
        // Catch advances to SurvivalSkills (flash skills tab). Opening skills â†’ SurvivalTools,
        // then talking to Brynna gives the axe and tinderbox.
        if (progression.current(player).varValue <= TutorialStep.SurvivalFish.varValue) {
            progression.setStep(player, TutorialStep.SurvivalSkills)
        }
    }

    private fun ProtectedAccess.chop() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalWc)) {
            progression.deny(
                player,
                "You cannot cut down this tree yet. You must progress further in the tutorial.",
            )
            return
        }
        if (progression.current(player).varValue > TutorialStep.SurvivalFm.varValue) {
            mes("Perhaps you've done enough woodcutting now.")
            return
        }
        if (inv.freeSpace() <= 0) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }
        anim("seq.human_woodcutting_bronze_axe")
        invAdd(inv, tutorial_objs.TUT2_LOGS, 1)
        statAdvance("stat.woodcutting", WOODCUTTING_XP)
        mes("You manage to cut some logs.")
        progression.advance(player, TutorialStep.SurvivalFm)
    }

    private fun ProtectedAccess.oakDeny() {
        mes(
            "You won't be able to chop oak trees until you have a Woodcutting level of 15. You'll advance to higher Woodcutting levels by chopping down normal trees."
        )
    }

    private suspend fun ProtectedAccess.lightFire() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalFm)) {
            return
        }
        if (invTotal(inv, tutorial_objs.TUT2_LOGS) <= 0) {
            return
        }
        val fireCoords = coords
        if (!tutorialExchange(listOf(tutorial_objs.TUT2_LOGS to 1), emptyList())) return
        // The light-fire seq has to finish before the player is asked to move: walking in the same
        // tick replaces the animation and nothing is drawn at all.
        anim(tutorial_seqs.LIGHT_FIRE)
        delay(LIGHT_FIRE_DELAY)
        statAdvance("stat.firemaking", FIREMAKING_XP)
        // Live steps the player off the log tile as the fire appears. `walk` routes around blocked
        // tiles, so the player simply stays put if west is unavailable.
        walk(fireCoords.translateX(-1))
        locRepo.add(
            fireCoords,
            tutorial_locs.FIRE,
            FIRE_DURATION,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        mes("The fire catches and the logs begin to burn.")
        progression.advance(player, TutorialStep.SurvivalCook)
    }

    private fun ProtectedAccess.cookShrimp() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalCook)) {
            return
        }
        if (invTotal(inv, tutorial_objs.TUT2_RAW_SHRIMP) > 0) {
            anim(tutorial_seqs.COOK_FIRE)
            if (!tutorialExchange(listOf(tutorial_objs.TUT2_RAW_SHRIMP to 1), listOf(tutorial_objs.SHRIMP to 1))) return
            statAdvance("stat.cooking", COOK_SHRIMP_XP)
            mes("You manage to cook some shrimp.")
            progression.advance(player, TutorialStep.SurvivalGate)
        }
    }

    private fun ProtectedAccess.mixDough() {
        if (!progression.requireAtLeast(player, TutorialStep.ChefDough)) {
            return
        }
        if (
            invTotal(inv, tutorial_objs.POT_FLOUR) <= 0 ||
                invTotal(inv, tutorial_objs.BUCKET_WATER) <= 0
        ) {
            return
        }
        if (!tutorialExchange(listOf(tutorial_objs.POT_FLOUR to 1, tutorial_objs.BUCKET_WATER to 1),
            listOf(tutorial_objs.POT_EMPTY to 1, tutorial_objs.BUCKET_EMPTY to 1, tutorial_objs.BREAD_DOUGH to 1))) {
            mes("You need one more free inventory space to make dough.")
            return
        }
        mes("You make some dough.")
        progression.advance(player, TutorialStep.ChefBread)
    }

    private fun ProtectedAccess.cookBread() {
        if (!progression.requireAtLeast(player, TutorialStep.ChefBread)) {
            if (progression.current(player).varValue < TutorialStep.ChefDough.varValue) {
                mes(
                    "You haven't got anything suitable for cooking! The master chef can help you out with that."
                )
            }
            return
        }
        if (invTotal(inv, tutorial_objs.BREAD_DOUGH) <= 0) {
            mes(
                "You haven't got anything suitable for cooking! The master chef can help you out with that."
            )
            return
        }
        anim(tutorial_seqs.COOK_RANGE)
        if (!tutorialExchange(listOf(tutorial_objs.BREAD_DOUGH to 1), listOf(tutorial_objs.BREAD to 1))) return
        statAdvance("stat.cooking", COOK_BREAD_XP)
        mes("You manage to bake some bread.")
        progression.advance(player, TutorialStep.ChefExit)
    }

    private fun ProtectedAccess.mineTin() {
        mineOre(tutorial_objs.TIN_ORE, "tin")
    }

    private fun ProtectedAccess.mineCopper() {
        mineOre(tutorial_objs.COPPER_ORE, "copper")
    }

    private fun ProtectedAccess.mineOre(ore: String, name: String) {
        if (!progression.requireAtLeast(player, TutorialStep.MineOres)) {
            progression.deny(
                player,
                "You are not ready to mine yet. Please follow the tutorial and you'll be mining in no time.",
            )
            return
        }
        if (invTotal(inv, tutorial_objs.BRONZE_PICKAXE) <= 0 &&
            invTotal(worn, tutorial_objs.BRONZE_PICKAXE) <= 0
        ) {
            mes("You need a pickaxe to mine this rock.")
            return
        }
        if (inv.freeSpace() <= 0) {
            mes(
                "You don't have enough room in your inventory to hold the ore. You'll need to drop something. To do so, right-click on an item you don't want and select drop. Once you've done that, try mining the rock again."
            )
            return
        }
        anim(tutorial_seqs.MINE_BRONZE_PICKAXE)
        invAdd(inv, ore, 1)
        statAdvance("stat.mining", MINING_XP)
        mes("You manage to mine some $name.")
        val hasTin = invTotal(inv, tutorial_objs.TIN_ORE) > 0
        val hasCopper = invTotal(inv, tutorial_objs.COPPER_ORE) > 0
        if (hasTin && hasCopper) {
            progression.advance(player, TutorialStep.MineSmelt)
        } else {
            progression.resume(player)
        }
    }

    /**
     * The island's furnace is `tut2_furnace`, a tutorial-only loc, so gating it here is safe and
     * only the smelting itself is delegated. The real dialog offers every bar, but the player is
     * carrying one tin and one copper, so bronze is the only row that can produce anything.
     */
    private suspend fun ProtectedAccess.smelt() {
        if (!progression.requireAtLeast(player, TutorialStep.MineSmelt)) {
            progression.deny(
                player,
                "This is a furnace for smelting metal. You'll learn how to use it soon.",
            )
            return
        }
        smithing.openTutorialSmelt(this)
    }

    private companion object {
        /** Two minutes, long enough to cook the tutorial shrimp without stranding the fire. */
        private const val FIRE_DURATION = 200

        /** Long enough for `human_createfire` to play before the player steps away. */
        private const val LIGHT_FIRE_DELAY = 4

        /* Live xp rewards for the tutorial's one-off skilling actions. */
        private const val FISHING_XP = 10.0
        private const val WOODCUTTING_XP = 25.0
        private const val FIREMAKING_XP = 40.0
        private const val COOK_SHRIMP_XP = 30.0
        private const val COOK_BREAD_XP = 40.0
        private const val MINING_XP = 17.5
    }
}
