package org.rsmod.content.quest.area.icemountain

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestArena
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.questItemExchange
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

private var Player.marleyProgress by intVarBit("varbit.bim_marley")
private var Player.checkalProgress by intVarBit("varbit.bim_checkal")
private var Player.burntofProgress by intVarBit("varbit.bim_burntof")
private var Player.flexUnlocked by intVarBit("varbit.emote_flex")
private var Player.rewardClaimed by intVarBit("varbit.bim_claimed_reward")
private var Player.pillars by intVarBit("varbit.bim_quest_pillars")

class BelowIceMountainQuest @Inject constructor(
    private val arenas: QuestArena,
    private val players: PlayerList,
    private val locs: LocRepository,
) : QuestScript("quest_belowicemountain", "varp.bim_main",
    rewards { item("obj.coins", 2000); extra("Access to the Ruins of Camdozaal"); extra("Flex emote") },
    ItemRewardDisplay("obj.bim_steak_sandwich", zoom = 400), questVarbit = "varbit.bim") {

    override fun ScriptContext.init() {
        for (willow in listOf("npc.bim_willow", "npc.bim_willow_outside", "npc.bim_willow_outside_entrace", "npc.bim_willow_inside"))
            onOpNpc1(willow) { startDialogue(it.npc) { willow() } }
        for (burntof in listOf("npc.bim_burntof", "npc.bim_burntof_pub", "npc.bim_burntof_temple_outside"))
            onOpNpc1(burntof) { startDialogue(it.npc) { burntof() } }
        onOpNpc1("npc.fai_varrock_bluemoon_chef") { startDialogue(it.npc) { sandwichRecipe() } }
        onOpHeldU("obj.knife", "obj.bread") { makeSandwich() }
        onOpHeldU("obj.knife", "obj.cooked_meat") { makeSandwich() }
        onOpLoc1("loc.bim_entrance") {
            if (quest.isQuestCompleted(player)) telejump(CAMDOZAAL)
            else startDialogue { willow() }
        }
        onOpLoc1("loc.bim_exit") {
            if (arenas.boss(player) != null) arenas.leave(player) else telejump(ENTRANCE)
        }
        onOpLoc1("loc.bim_willows_bag") { finishQuest() }
        onOpLoc1("loc.bim_boss_rock") { minePillar(it.loc.coords) }
        val guardian = checkNotNull(ServerCacheManager.getNpc(GUARDIAN.asRSCM()))
        onNpcQueue(guardian, "queue.death") {
            val owner = arenas.owner(npc)?.resolve(players)
            if (owner != null) owner.strongQueue(FINISH_QUEUE, 1)
        }
        onPlayerQueue(FINISH_QUEUE) {
            if (quest.getQuestStage(player) == 60) {
                quest.setQuestStage(this, 100)
                arenas.defeated(player)
            }
            finishQuest()
        }
    }

    override fun subTitle() = "talking to <col=800000>Willow<col=000080> south of <col=800000>Ice Mountain<col=000080>."
    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        if (quest.getQuestStage(access.player) >= 30) {
            line("Willow's crew are grave robbers! I must defeat the <red>Ancient Guardian</red> by fighting it or mining all four structural pillars with a pickaxe and level 10 Mining.")
        } else {
            line("Willow needs her old crew to open the ruins on the west side of Ice Mountain.")
            if (access.player.marleyProgress < RECRUITED) line("<red>Marley</red> in Edgeville wants a <red>steak sandwich</red>. The Blue Moon Inn cook knows the recipe.")
            if (access.player.checkalProgress < RECRUITED) line("<red>Checkal</red> in Barbarian Village wants me to train with <red>Atlas</red> and demonstrate the Flex emote.")
            if (access.player.burntofProgress < RECRUITED) line("<red>Burntof</red> in Falador's Rising Sun Inn wants a beer and a game of rock, paper, scissors.")
            if (crewReady(access.player)) line("Everyone has agreed. I should meet <red>Willow</red> at the ruins entrance west of Ice Mountain.")
        }
    }
    override fun completedLog(player: ProtectedAccess) = completionJournal(player) {
        line("I defeated the Ancient Guardian and discovered the Ruins of Camdozaal. Willow abandoned a bag containing 2,000 coins.")
    }

    private suspend fun Dialogue.willow() {
        if (quest.isQuestCompleted(player)) { chatNpc(neutral, "The ruins are yours to explore."); return }
        if (quest.getQuestStage(player) == 100) { access.finishQuest(); return }
        if (quest.isQuestNotStarted(player)) {
            chatNpc(neutral, "I've discovered ancient ruins, but I need my old excavation crew. Will you find Marley, Checkal and Burntof?")
            if (player.vars["varp.qp"] < 16) { chatNpc(neutral, "Come back when you have at least 16 quest points."); return }
            if (startQuestPrompt(quest)) quest.setQuestStage(access, 1)
            return
        }
        if (!crewReady(player)) {
            chatNpc(neutral, "Find Marley in Edgeville, Checkal in Barbarian Village, and Burntof in Falador's Rising Sun Inn. I'll meet you at the entrance west of Ice Mountain.")
            return
        }
        if (player.coords.x > 3010) {
            chatNpc(neutral, "Meet us at the ruins entrance on the west side of Ice Mountain.")
            return
        }
        chatNpc(happy, "Everyone's here! Let's open these ruins.")
        mesbox("Burntof blasts the rocks, Checkal shifts the debris, and Marley disarms the traps. Inside, Willow reveals that her crew are grave robbers.")
        chatNpc(angry, "You know too much. We can't have you telling the authorities!")
        mesbox("Burntof trips an ancient mechanism. A guardian wakes, and the crew flee, leaving you behind!")
        player.pillars = 0
        if (arenas.enter(access, GUARDIAN, GUARDIAN_SPAWN, ARENA_ENTRY, ENTRANCE)) {
            quest.setQuestStage(access, 60)
            for (pillar in PILLARS) locs.add(arenas.normal(player, pillar), "loc.bim_boss_rock", 6000, LocAngle.North, LocShape.CentrepieceStraight)
        }
    }

    suspend fun Dialogue.marleyQuest(): Boolean {
        if (!quest.isQuestInProgress(player) || player.marleyProgress >= RECRUITED) return false
        if (player.marleyProgress == 0) player.marleyProgress = 5
        chatNpc(neutral, "I'll help Willow if you bring me a steak sandwich. The cook in Varrock's Blue Moon Inn can tell you how to make one.")
        if (!access.questItemExchange(listOf(SANDWICH to 1), emptyList())) return true
        player.marleyProgress = RECRUITED
        recruited(access)
        chatNpc(happy, "Just what I wanted. I'll see you at the ruins.")
        return true
    }

    suspend fun Dialogue.checkalQuest(): Boolean {
        if (!quest.isQuestInProgress(player) || player.checkalProgress >= RECRUITED) return false
        if (player.checkalProgress == 0) player.checkalProgress = 5
        if (player.flexUnlocked == 0) {
            chatNpc(neutral, "Prove your strength. Train with Atlas in the Long Hall, then come back and show me what you learned.")
            return true
        }
        chatNpc(neutral, "Show me that flex Atlas taught you!")
        if (choice2("Demonstrate the Flex emote.", true, "Later.", false)) {
            access.anim("seq.emote_flex")
            access.delay(3)
            player.checkalProgress = RECRUITED
            recruited(access)
            chatNpc(happy, "Impressive! I'll help Willow at the ruins.")
        }
        return true
    }

    suspend fun Dialogue.atlasQuest(): Boolean {
        if (!quest.isQuestInProgress(player) || player.checkalProgress != 5) return false
        chatNpc(neutral, "Checkal sent you? Let's put you through your paces.")
        if (!choice2("I'm ready to train.", true, "Not yet.", false)) return true
        repeat(3) { access.anim("seq.bim_pushups"); access.delay(3) }
        player.flexUnlocked = 1
        player.checkalProgress = 15
        chatNpc(happy, "Now go and show Checkal your new Flex emote.")
        return true
    }

    private suspend fun Dialogue.sandwichRecipe() {
        if (player.marleyProgress == 0) { chatNpc(neutral, "I'm busy cooking for the inn."); return }
        chatNpc(neutral, "Marley's barred, but you can make his sandwich. Use a knife on bread with cooked meat in your pack. That's all there is to it.")
        if (player.marleyProgress < RECRUITED) player.marleyProgress = 10
    }

    private fun ProtectedAccess.makeSandwich() {
        if (player.marleyProgress < 10) { mes("You haven't learned the steak sandwich recipe yet."); return }
        if (inv.count("obj.knife") == 0) return
        if (sandwich(this)) mes("You slice the bread and fill it with cooked meat.")
        else mes("You need bread and cooked meat to make a steak sandwich.")
    }

    private suspend fun Dialogue.burntof() {
        if (!quest.isQuestInProgress(player) || player.burntofProgress >= RECRUITED) {
            chatNpc(neutral, "Nothing beats a good drink."); return
        }
        if (player.burntofProgress == 0) {
            chatNpc(neutral, "Buy me a drink first, then we'll talk about Willow.")
            val beer = BEERS.firstOrNull { player.inv.count(it) > 0 } ?: return
            if (!access.questItemExchange(listOf(beer to 1), listOf("obj.beer_glass" to 1))) return
            player.burntofProgress = 1
        }
        chatNpc(neutral, "Beat me at rock, paper, scissors, best of three, and I'll help.")
        repeat(2) {
            choice3("Rock", 1, "Paper", 2, "Scissors", 3)
            chatNpc(drunk, "You win! I can't seem to get my hands to do the right shape.")
        }
        player.burntofProgress = RECRUITED
        recruited(access)
        chatNpc(happy, "I'll meet Willow at the entrance.")
    }

    private fun recruited(access: ProtectedAccess) {
        if (quest.getQuestStage(access.player) < 15) quest.setQuestStage(access, 15)
    }

    private suspend fun ProtectedAccess.minePillar(pillar: CoordGrid) {
        val boss = arenas.boss(player) ?: return
        if (quest.getQuestStage(player) != 60 || player.miningLvl < 10) {
            mes("You need level 10 Mining to weaken the structural pillars."); return
        }
        val hasPickaxe = (inv.filterNotNull { true } + worn.filterNotNull { true }).any {
            val type = ServerCacheManager.getItem(it.id)
            type != null && type.isContentType("content.mining_pickaxe") &&
                player.miningLvl >= type.param(params.levelrequire)
        }
        if (!hasPickaxe) { mes("You need a pickaxe."); return }
        val centre = arenas.normal(player, GUARDIAN_SPAWN)
        val bit = 1 shl ((if (pillar.x > centre.x) 1 else 0) + (if (pillar.z > centre.z) 2 else 0))
        if (player.pillars and bit != 0) { mes("This pillar has already collapsed."); return }
        anim("seq.human_mining_bronze_pickaxe")
        delay(3)
        player.pillars = player.pillars or bit
        locs.add(pillar, "loc.bim_boss_rock_mined", 6000, LocAngle.North, LocShape.CentrepieceStraight)
        statAdvance("stat.mining", 10.0)
        mes("The pillar crumbles, showering the guardian with rubble.")
        boss.hitpoints = (boss.hitpoints - 10).coerceAtLeast(0)
        if (boss.hitpoints == 0 && boss.isSlotAssigned) {
            quest.setQuestStage(this, 100)
            arenas.defeated(player)
            finishQuest()
        }
    }

    private fun ProtectedAccess.finishQuest() {
        if (quest.getQuestStage(player) != 100) return
        if (quest.completeQuest(this)) {
            player.rewardClaimed = 1
            player.flexUnlocked = 1
            arenas.leave(player)
            telejump(CAMDOZAAL)
            mes("An Imcando dwarf named Ramarno welcomes you to the Ruins of Camdozaal.")
        } else mes("Make room for the 2,000 coins and search Willow's bag again.")
    }

    companion object {
        const val SANDWICH = "obj.bim_steak_sandwich"
        private const val RECRUITED = 40
        private const val GUARDIAN = "npc.bim_golem_boss"
        private const val FINISH_QUEUE = "queue.bim_quest_finish"
        private val BEERS = listOf("obj.beer", "obj.asgarnian_ale", "obj.dwarven_stout", "obj.wizards_mind_bomb")
        private val ENTRANCE = CoordGrid(2998, 3492, 0)
        private val CAMDOZAAL = CoordGrid(2952, 5782, 0)
        private val ARENA_ENTRY = CoordGrid(2952, 5767, 0)
        private val PILLARS = listOf(CoordGrid(2947, 5771, 0), CoordGrid(2947, 5778, 0), CoordGrid(2958, 5771, 0), CoordGrid(2958, 5778, 0))
        private val GUARDIAN_SPAWN = CoordGrid(2952, 5774, 0)
        private fun crewReady(player: Player) = player.marleyProgress >= RECRUITED && player.checkalProgress >= RECRUITED && player.burntofProgress >= RECRUITED
        fun sandwich(access: ProtectedAccess) = access.questItemExchange(
            listOf("obj.bread" to 1, "obj.cooked_meat" to 1), listOf(SANDWICH to 1))
    }
}
