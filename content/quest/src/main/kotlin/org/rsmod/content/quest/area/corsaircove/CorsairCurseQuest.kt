package org.rsmod.content.quest.area.corsaircove

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestArena
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.questItemExchange
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

private var Player.colinStory by intVarBit("varbit.corscurs_cabinboy")
private var Player.gnocciStory by intVarBit("varbit.corscurs_cook")
private var Player.arsenStory by intVarBit("varbit.corscurs_thief")
private var Player.ithoiExposed by intVarBit("varbit.corscurs_navigator")

class CorsairCurseQuest @Inject constructor(
    private val arenas: QuestArena,
    private val players: PlayerList,
    private val accessLauncher: ProtectedAccessLauncher,
    private val bossDeps: BossDeps,
) : QuestScript("quest_corsaircurse", "varp.corscurs",
    rewards { extra("Access to the Corsair Cove bank") },
    ItemRewardDisplay(RELIC, zoom = 400), questVarbit = "varbit.corscurs_progress"), SpadeDigHook {

    override fun ScriptContext.init() {
        BossCombat.register(this, ITHOI_COMBAT, bossDeps)
        for (captain in listOf("npc.corsair_captain_crossroads", "npc.corsair_captain_1op", "npc.corsair_captain_2ops", "npc.corsair_captain_throne", "npc.corsair_ferry_covedocks", "npc.corsair_ferry_rimmington")) {
            onOpNpc1(captain) { startDialogue(it.npc) { captain() } }
            onOpNpc3(captain) { sail() }
        }
        for (navigator in listOf("npc.corscurs_navigator", "npc.corscurs_navigator_sick"))
            onOpNpc1(navigator) { startDialogue(it.npc) { ithoi() } }
        for (colin in listOf("npc.corscurs_cabinboy", "npc.corscurs_cabinboy_sick", "npc.corscurs_cabinboy_2ops"))
            onOpNpc1(colin) { startDialogue(it.npc) { colin() } }
        onOpNpc3("npc.corscurs_cabinboy_2ops") { sail() }
        for (cook in listOf("npc.corscurs_cook", "npc.corscurs_cook_sick", "npc.corscurs_cook_postquest"))
            onOpNpc1(cook) { startDialogue(it.npc) { gnocci() } }
        for (thief in listOf("npc.corscurs_thief", "npc.corscurs_thief_sick", "npc.corscurs_thief_postquest"))
            onOpNpc1(thief) { startDialogue(it.npc) { arsen() } }
        onOpNpc1("npc.ogress_tess") { startDialogue(it.npc) { tess() } }
        onOpLoc1("loc.corscurs_telescope") {
            if (quest.getQuestStage(player) >= 20 && player.colinStory == 1) {
                startDialogue { mesbox("Through the telescope you see a wet ogre. Colin's 'mermaid' was no mermaid at all.") }
                player.colinStory = 2
            } else mes("You look out over the sea.")
        }
        for (wood in listOf("loc.corscurs_driftwood_dry", "loc.corscurs_driftwood_multi"))
            onOpLocU(wood, "obj.tinderbox") { exposeIthoi() }
        onPlayerCoordsChanged {
            if (quest.getQuestStage(player) == 50 && player.coords.level == 1 &&
                player.coords.x in 2525..2535 && player.coords.z in 2833..2843
            ) accessLauncher.launch(player) { beginFight() }
        }
        val ithoi = checkNotNull(ServerCacheManager.getNpc(ITHOI.asRSCM()))
        onNpcQueue(ithoi, "queue.death") {
            arenas.owner(npc)?.resolve(players)?.strongQueue(DEFEATED_QUEUE, 1)
        }
        onPlayerQueue(DEFEATED_QUEUE) {
            if (quest.getQuestStage(player) == 50) {
                quest.setQuestStage(this, 55)
                arenas.leave(player)
                mes("Ithoi has been defeated. You should report back to Captain Tock.")
            }
        }
        onOpNpc1("npc.corscurs_banker_1op") {
            startDialogue(it.npc) { chatNpc(neutral, "Captain Tock hasn't authorised you to use the bank yet.") }
        }
        onOpNpc1("npc.corscurs_banker_allops") { tryOpenBank() }
        onOpNpc3("npc.corscurs_banker_allops") { tryOpenBank() }
    }

    override fun subTitle() = "talking to <col=800000>Captain Tock<col=000080> at the crossroads north of <col=800000>Port Sarim<col=000080>."
    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        val p = access.player
        when (quest.getQuestStage(p)) {
            in 1..14 -> line("Captain Tock's crew are sick. I should meet him at his ship on the <red>western Rimmington dock</red> and sail to Corsair Cove.")
            in 15..19 -> line("I should talk to <red>Ithoi</red> in his hut south-west of the cove about the curse.")
            in 20..24 -> {
                if (p.arsenStory < 3) line("<red>Arsen</red> thinks a stolen ogre relic caused the curse. Captain Tock has it; <red>Chief Tess</red> in the dungeon may know more.")
                if (p.colinStory < 3) line("<red>Colin</red> thinks he offended a mermaid. I should look through <red>Ithoi's telescope</red>, then return to Colin.")
                if (p.gnocciStory < 3) line("<red>Gnocci</red> thinks a demonic doll caused the curse. I should dig by the lump of sand west of Ithoi's hut and report what I find.")
                if (theoriesCleared(p)) line("I've disproved all three theories. I should speak to <red>Captain Tock</red>.")
            }
            25 -> line("I should ask <red>Gnocci</red> who cooked dinner on the night the crew became sick.")
            30 -> line("I should ask <red>Arsen</red> what he knows about Ithoi.")
            35 -> line("<red>Ithoi</red> cooked the meal and was about to lose his job. I should confront him.")
            40 -> line("Ithoi admitted poisoning the crew. I can expose his fake illness by using a <red>tinderbox</red> on the <red>driftwood</red> under his hut.")
            45 -> line("I should tell <red>Captain Tock</red> that Ithoi poisoned his crew and faked the curse.")
            50 -> line("Captain Tock wants me to defeat <red>Ithoi</red>. I should enter his hut prepared for a fight.")
            else -> line("Ithoi is defeated. I should report back to <red>Captain Tock</red>.")
        }
    }
    override fun completedLog(player: ProtectedAccess) = completionJournal(player) {
        line("I exposed Ithoi's poisoning plot and defeated him. Captain Tock has granted me access to Yusuf's bank in Corsair Cove.")
    }

    private suspend fun Dialogue.captain() {
        when {
            quest.isQuestNotStarted(player) -> {
                chatNpc(worried, "Me crew are sick, and the navigator says we're cursed. Will ye help us?")
                if (!startQuestPrompt(quest)) return
                quest.setQuestStage(access, 10)
                chatNpc(neutral, "Meet me at the western dock in Rimmington. We'll sail to Corsair Cove.")
            }
            quest.isQuestCompleted(player) -> {
                chatNpc(happy, "The crew are recovering. You're welcome aboard.")
                if (choice2("Travel between Rimmington and Corsair Cove.", true, "Stay here.", false)) access.sail()
            }
            quest.getQuestStage(player) < 15 -> {
                chatNpc(neutral, "Ready to sail to Corsair Cove?")
                if (player.coords.x < 3000 && choice2("Let's sail.", true, "Not yet.", false)) access.sail()
            }
            quest.getQuestStage(player) == 55 -> {
                chatNpc(happy, "Ye stopped the traitor! I'll tell Yusuf ye can use the bank. Thank ye for helping me crew.")
                quest.completeQuest(access)
            }
            quest.getQuestStage(player) == 45 -> {
                chatPlayer(neutral, "Ithoi poisoned the crew. He's pretending to be ill, but I saw him run outside to put out a fire.")
                chatNpc(angry, "He betrayed us! Deal with him before he can hurt anyone else.")
                quest.setQuestStage(access, 50)
            }
            quest.getQuestStage(player) == 20 && theoriesCleared(player) -> {
                chatNpc(neutral, "Ithoi and I were away at a parlay. The crew got sick after dinner, and Ithoi fell ill later.")
                quest.setQuestStage(access, 25)
            }
            player.arsenStory == 1 && !access.playerContainsObj(RELIC) -> {
                chatNpc(neutral, "Arsen's relic? Take it and see what the ogres make of it.")
                if (access.invAdd(access.inv, RELIC).failure) chatNpc(neutral, "Make some room first.")
            }
            else -> {
                chatNpc(neutral, "Talk to me crew and find out what happened. Ithoi, Gnocci, Colin and Arsen all have their own ideas.")
                if (choice2("Travel to Rimmington.", true, "Keep investigating.", false)) access.sail()
            }
        }
    }

    private fun ProtectedAccess.sail() {
        if (quest.isQuestNotStarted(player)) return
        if (player.coords.z > 3100) {
            if (quest.getQuestStage(player) < 15) quest.setQuestStage(this, 15)
            telejump(COVE_DOCK)
        } else telejump(RIMMINGTON_DOCK)
    }

    private suspend fun Dialogue.ithoi() {
        when (quest.getQuestStage(player)) {
            15 -> {
                chatNpc(worried, "It must be a curse. Ask the others what they did to bring it upon us.")
                quest.setQuestStage(access, 20)
            }
            35 -> {
                chatPlayer(neutral, "You cooked the meal, and the captain was going to dismiss you. You poisoned the crew!")
                chatNpc(angry, "Without a curse to cure, they don't need a navigator. You can't prove I'm not sick too!")
                quest.setQuestStage(access, 40)
            }
            40 -> chatNpc(angry, "I'm too ill to get up. You can't prove otherwise.")
            50 -> access.beginFight()
            else -> chatNpc(neutral, "These are dangerous waters. You should beware of curses.")
        }
    }

    private suspend fun Dialogue.arsen() {
        if (quest.getQuestStage(player) == 30) {
            chatNpc(neutral, "My brother Francois heard the captain was going to dismiss Ithoi. We don't need a navigator now we've settled here.")
            quest.setQuestStage(access, 35)
        } else if (quest.getQuestStage(player) >= 20 && player.arsenStory == 0) {
            chatNpc(worried, "I grabbed a relic from the ogres' cave. Their shamans must have cursed us. Ask the captain for it and return it to Chief Tess.")
            player.arsenStory = 1
        } else if (player.arsenStory == 2) {
            chatPlayer(neutral, "It was just Tess's tooth-pick. The ogres haven't cursed you.")
            chatNpc(happy, "Then this wasn't my fault! Thank you.")
            player.arsenStory = 3
        } else chatNpc(worried, "I still feel terrible.")
    }

    private suspend fun Dialogue.tess() {
        if (player.arsenStory != 1) { chatNpc(neutral, "What skinny human want?"); return }
        if (!returnRelic(access)) { chatNpc(neutral, "Show Tess what human stole."); return }
        chatNpc(neutral, "That just Tess's old tooth-pick! Ogres not curse skinny humans.")
        player.arsenStory = 2
    }

    private suspend fun Dialogue.colin() {
        if (quest.isQuestCompleted(player)) {
            chatNpc(happy, "The captain said you're welcome aboard. Ready to travel?")
            if (choice2("Travel.", true, "Not now.", false)) access.sail()
            return
        }
        if (quest.getQuestStage(player) >= 20 && player.colinStory == 0) {
            chatNpc(worried, "I saw a mermaid through Ithoi's telescope and called to her. Maybe she cursed us for being rude!")
            player.colinStory = 1
        } else if (player.colinStory == 2) {
            chatPlayer(neutral, "I looked through the telescope. You saw an ogre, not a mermaid.")
            chatNpc(happy, "Then I didn't offend a mermaid! What a relief.")
            player.colinStory = 3
        } else chatNpc(worried, "I hope you can help us.")
    }

    private suspend fun Dialogue.gnocci() {
        if (quest.getQuestStage(player) == 25) {
            chatNpc(neutral, "I was too upset to cook that night. Ithoi prepared dinner before he left with the captain.")
            quest.setQuestStage(access, 30)
        } else if (quest.getQuestStage(player) >= 20 && player.gnocciStory == 0) {
            chatNpc(worried, "I found a doll while fishing. It moved by itself! I buried it by the tree, west of Ithoi's hut. Its demon must have cursed us.")
            player.gnocciStory = 1
        } else if (player.gnocciStory == 2) {
            chatPlayer(neutral, "I dug up the doll. It's a clockwork toy, with no demon inside it.")
            chatNpc(happy, "So the curse isn't my fault!")
            player.gnocciStory = 3
        } else chatNpc(worried, "I don't feel well enough to cook.")
    }

    override fun claims(player: Player) = quest.isQuestInProgress(player) && player.gnocciStory == 1 &&
        player.coords.level == 0 && player.coords.chebyshevDistance(DOLL_SPOT) <= 2

    override suspend fun ProtectedAccess.dig() {
        startDialogue { mesbox("You uncover a clockwork doll. Its movements come from a mechanism, not a demon. You return it to the sand.") }
        player.gnocciStory = 2
    }

    private suspend fun ProtectedAccess.exposeIthoi() {
        if (quest.getQuestStage(player) != 40 || inv.count("obj.tinderbox") == 0) return
        startDialogue {
            mesbox("You light the driftwood. Ithoi leaps out of bed and rushes outside to douse the fire with magic. He's clearly well enough to move!")
        }
        player.ithoiExposed = 1
        quest.setQuestStage(this, 45)
    }

    private fun ProtectedAccess.beginFight() {
        if (arenas.enter(this, ITHOI, CoordGrid(2529, 2839, 1), CoordGrid(2531, 2837, 1), CoordGrid(2533, 2836, 0)))
            mes("Ithoi attacks!")
    }

    companion object {
        const val RELIC = "obj.corscurs_relic"
        private const val ITHOI = "npc.corscurs_navigator_combat"
        private const val DEFEATED_QUEUE = "queue.corsair_quest_defeated"
        private val COVE_DOCK = CoordGrid(2574, 2835, 1)
        private val RIMMINGTON_DOCK = CoordGrid(2909, 3226, 0)
        private val DOLL_SPOT = CoordGrid(2504, 2839, 0)
        private val ITHOI_COMBAT = boss(ITHOI) {
            stats(attackRate = 5)
            fun strike(element: String, maximum: Int) = ability("${element}_strike") {
                anim("seq.human_caststrike")
                projectile(
                    spotanim = "spotanim.${element}strike_travel",
                    launch = "spotanim.${element}strike_casting",
                    impact = "spotanim.${element}strike_impact",
                    hit = Effect.Hit(damage = Accuracy(Roll(0..maximum)), type = Magic),
                )
            }
            val wind = strike("wind", 2)
            val water = strike("water", 4)
            val earth = strike("earth", 6)
            val fire = strike("fire", 8)
            phase("combat") {
                weightedSelectorRandom {
                    +random(wind, weight = 1)
                    +random(water, weight = 1)
                    +random(earth, weight = 1)
                    +random(fire, weight = 1)
                }
            }
        }
        private fun theoriesCleared(player: Player) = player.colinStory == 3 && player.gnocciStory == 3 && player.arsenStory == 3
        fun returnRelic(access: ProtectedAccess) = access.questItemExchange(listOf(RELIC to 1), emptyList())
    }
}
