package org.rsmod.content.quest.area.crandor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.dragon
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
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
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

private var Player.shipPurchased by intVarBit("varbit.dragon_slayer_ship_purchased")
private var Player.shipRepairs by intVarBit("varbit.dragon_slayer_ship_repairs")
private var Player.nedRecruited by intVarBit("varbit.dragonslayer_asked_ned")
private var Player.shortcut by intVarBit("varbit.dragonslayer_crandor_found_secret_door")

class DragonSlayerQuest @Inject constructor(
    private val arenas: QuestArena,
    private val players: PlayerList,
    private val locs: LocRepository,
    private val objs: ObjRepository,
    private val bossDeps: BossDeps,
) : QuestScript("quest_dragonslayer1", "varp.dragonquest",
    rewards { xp("stat.strength", 18650.0); xp("stat.defence", 18650.0); extra("The right to wear rune platebodies and green dragonhide bodies") },
    ItemRewardDisplay(HEAD, zoom = 400), questVarbit = "varbit.dragon_slayer_progress") {

    override fun ScriptContext.init() {
        onOpNpc1("npc.guildmaster") { startDialogue(it.npc) { guildmaster() } }
        onOpNpc1("npc.wormbrain") { startDialogue(it.npc) { wormbrain() } }
        onOpNpc1("npc.dragonslayer_ned_on_ship") { startDialogue(it.npc) { nedQuest() } }
        onOpNpc1("npc.ned_1op") { startDialogue(it.npc) { nedQuest() } }
        onOpNpc1("npc.dragonslayer_ned_on_crandor") { startDialogue(it.npc) { chatNpc(neutral, "We made it to Crandor. You'll find Elvarg beneath the volcano.") } }
        for (a in MAP_PARTS.indices) for (b in a + 1 until MAP_PARTS.size)
            onOpHeldU(MAP_PARTS[a], MAP_PARTS[b]) {
                if (assembleMap(this)) mes("You join all three pieces into the map of Crandor.")
                else mes("You need all three map pieces.")
            }
        for (chest in listOf("loc.funchestshut", "loc.funchestopen")) {
            onOpLoc1(chest) { mazeChest() }
            onOpLoc2(chest) { mazeChest() }
        }
        offering("obj.silk", "varbit.dragonslayer_used_silk")
        offering("obj.bowl_unfired", "varbit.dragonslayer_used_bowl_unfired")
        offering("obj.lobster_pot", "varbit.dragonslayer_used_lobster_pot")
        offering("obj.wizards_mind_bomb", "varbit.dragonslayer_used_wizards_mind_bomb")
        keyedDoor("loc.melzardoor", MAZE_KEY, consume = false)
        keyedDoor("loc.reddoor", "obj.redkey")
        keyedDoor("loc.orangedoor", "obj.orangekey")
        keyedDoor("loc.yellowdoor", "obj.yellowkey")
        keyedDoor("loc.bluedoor", "obj.bluekey")
        keyedDoor("loc.magentadoor", "obj.magentakey")
        keyedDoor("loc.greendoor", "obj.greenkey")
        onOpLoc1("loc.funladdertop") { climbTo(it.loc.coords.translateZ(6400)) }
        onOpLoc1("loc.dragon_slayer_ladder_from_cellar") { climbTo(it.loc.coords.translateZ(-6400)) }
        onOpLoc1("loc.dragonslayer_melzar_exit_laddertop") { climbTo(it.loc.coords.translateLevel(-1)) }
        onOpLoc1("loc.funexit") { crossDoor(it.loc) }
        onOpLoc1("loc.dragon_slayer_qip_ruin_entrance") { climbTo(CoordGrid(2833, 9656, 0)) }
        for (hole in listOf("loc.dragonslayer_shiphole", "loc.shiphole")) {
            onOpLoc1(hole) { repairShip() }
            onOpLocU(hole, "obj.woodplank") { repairShip() }
        }
        onOpLoc1("loc.dragonshipgangplank_on") {
            if (player.shipPurchased == 0) mes("This is Klarense's ship. You should speak to him first.")
            else telejump(CoordGrid(3048, 3206, 1))
        }
        onOpLoc1("loc.dragonshipgangplank_off") { telejump(CoordGrid(3047, 3204, 0)) }
        onOpLoc1("loc.dragonshipladdertop") {
            if (it.loc.coords.x == 3049 && it.loc.coords.z == 3208)
                climbTo(CoordGrid(3049, 9640, 1))
            else climbTo(it.loc.coords.translateLevel(-1))
        }
        onOpLoc1("loc.dragonshipladder") {
            if (it.loc.coords.x == 3049 && it.loc.coords.z == 9640)
                climbTo(CoordGrid(3049, 3208, 1))
            else climbTo(it.loc.coords.translateLevel(1))
        }
        onOpLoc1("loc.dragon_slayer_qip_stalagtite_jump") {
            if (arenas.boss(player) != null) arenas.leave(player)
            else if (quest.getQuestStage(player) in 3..8) {
                arenas.enter(this, ELVARG, ELVARG_SPAWN, ARENA_ENTRY, ARENA_EXIT)
            } else mes("You have no reason to enter Elvarg's lair.")
        }
        onOpLoc1("loc.dragonsecretdoor") {
            if (player.coords.z >= 9600 || player.shortcut != 0) {
                player.shortcut = 1
                crossDoor(it.loc)
            } else mes("The wall is solid. Perhaps it opens from the other side.")
        }
        BossCombat.register(this, dragon(ELVARG, meleeMax = 8, dragonfireMax = 69, ranged = true, attackRate = 4), bossDeps)
        val elvarg = checkNotNull(ServerCacheManager.getNpc(ELVARG.asRSCM()))
        onNpcQueue(elvarg, "queue.death") {
            arenas.owner(npc)?.resolve(players)?.strongQueue(ELVARG_QUEUE, 1)
        }
        onPlayerQueue(ELVARG_QUEUE) {
            if (quest.getQuestStage(player) !in 3..8) return@onPlayerQueue
            quest.setQuestStage(this, 8)
            if (!playerContainsObj(HEAD) && invAdd(inv, HEAD).failure)
                objs.add(HEAD, ARENA_EXIT, 6000, player, 1)
            arenas.leave(player)
            mes("You take Elvarg's head as proof of your victory. Return to Oziach in Edgeville.")
        }
    }

    override fun subTitle() = "talking to the <col=800000>Guildmaster<col=000080> in the <col=800000>Champions' Guild<col=000080>."
    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        val p = access.player
        when (quest.getQuestStage(p)) {
            1 -> line("The Guildmaster sent me to <red>Oziach</red>, north-west of Edgeville, to prove myself a champion.")
            2 -> line("Oziach wants me to slay <red>Elvarg</red>, the dragon of Crandor. I should ask the <red>Guildmaster</red> how to get there.")
            8 -> line("I defeated Elvarg! I should show <red>Oziach</red> her head.")
            else -> {
                line("I need a <red>map of Crandor</red>, a <red>ship</red>, and protection against <red>dragonfire</red>.")
                if (!access.playerContainsObj(MAP) && p.nedRecruited == 0) {
                    line("Melzar's piece is in <red>Melzar's Maze</red>, north-west of Rimmington. The Guildmaster has the key.")
                    line("<red>Wormbrain</red> in Port Sarim jail has a piece. He'll sell it for 10,000 coins, or I could fight him and use Telekinetic Grab on it.")
                    line("The <red>Oracle</red> on Ice Mountain knows about the last piece. The magic door in the Dwarven Mine needs silk, an unfired bowl, a lobster pot and a wizard's mind bomb.")
                }
                if (p.shipPurchased == 0) line("<red>Klarense</red> in Port Sarim will sell me a ship for <red>2,000 coins</red>.")
                if (p.shipRepairs < 3) line("I need to repair the ship's hole three times with a hammer, <red>3 planks</red> and <red>90 steel nails</red> in total. Repairs completed: ${p.shipRepairs}/3.")
                line("<red>Duke Horacio</red> can supply an anti-dragon shield. <red>Ned</red> in Draynor can captain the repaired ship if I bring the map.")
            }
        }
    }
    override fun completedLog(player: ProtectedAccess) = completionJournal(player) {
        line("I sailed to Crandor, defeated Elvarg and showed Oziach her head. I've proven myself a true champion.")
    }

    private suspend fun Dialogue.guildmaster() {
        when (quest.getQuestStage(player)) {
            0 -> {
                chatNpc(neutral, "To prove yourself a champion, speak to Oziach in Edgeville. He can set you a worthy task.")
                if (player.vars["varp.qp"] < 32) { chatNpc(neutral, "You need at least 32 quest points."); return }
                if (startQuestPrompt(quest)) quest.setQuestStage(access, 1)
            }
            1 -> chatNpc(neutral, "Oziach lives north-west of Edgeville. Ask him how to earn the right to wear rune platebody armour.")
            else -> {
                chatNpc(neutral, "Elvarg destroyed Crandor. Three surviving wizards split the only map: Melzar hid his piece in his maze; Thalzar's is hidden in the Dwarven Mine; Lozar's was stolen by Wormbrain.")
                chatNpc(neutral, "Ask the Oracle about Thalzar's piece. Klarense has a ship, Ned can sail it, and Duke Horacio has a shield against dragonfire.")
                if (quest.getQuestStage(player) == 2) quest.setQuestStage(access, 3)
                for (flag in INSTRUCTIONS) VarPlayerIntMapSetter.set(player, flag, 1)
                if (!access.playerContainsObj(MAZE_KEY) && access.invAdd(access.inv, MAZE_KEY).success)
                    objbox(MAZE_KEY, "The Guildmaster gives you a key to Melzar's Maze.")
            }
        }
    }

    suspend fun Dialogue.oziachQuest(): Boolean {
        when (quest.getQuestStage(player)) {
            0, 10 -> return false
            1 -> {
                chatNpc(neutral, "If ye want to wear a rune platebody, prove yerself. Slay Elvarg, the dragon of Crandor, and bring me her head.")
                quest.setQuestStage(access, 2)
                chatNpc(neutral, "The Guildmaster can tell ye how to get there.")
            }
            8 -> {
                if (!access.questItemExchange(listOf(HEAD to 1), emptyList())) {
                    chatNpc(neutral, "Bring me Elvarg's head as proof.")
                    return true
                }
                chatNpc(happy, "Ye've done it! Ye've earned the right to wear rune plate armour.")
                quest.completeQuest(access)
            }
            else -> chatNpc(neutral, "Bring me the head of Elvarg. The Guildmaster can help ye prepare.")
        }
        return true
    }

    suspend fun Dialogue.oracleQuest(): Boolean {
        if (quest.getQuestStage(player) !in 2..7) return false
        chatNpc(neutral, "Thalzar's map lies beyond a magic door in the mine below. Bring silk, an unfired bowl, a lobster pot and a wizard's mind bomb. Use each on the door.")
        VarPlayerIntMapSetter.set(player, "varbit.dragonslayer_instructions_oracle", 1)
        return true
    }

    private suspend fun Dialogue.wormbrain() {
        if (quest.getQuestStage(player) !in 3..7) { chatNpc(neutral, "What are you looking at?"); return }
        chatNpc(neutral, "A piece of a map, you say? I've got one. Yours for 10,000 coins.")
        if (access.playerContainsObj(MAP_PARTS[1]) || access.playerContainsObj(MAP)) return
        if (!choice2("Pay 10,000 coins.", true, "No deal.", false)) return
        if (buyMapPiece(access)) objbox(MAP_PARTS[1], "Wormbrain hands you his piece of the map.")
        else chatNpc(neutral, "You need the money and room for the map piece.")
    }

    suspend fun Dialogue.klarenseQuest(): Boolean {
        if (quest.getQuestStage(player) !in 3..7) return false
        if (player.shipPurchased != 0) { chatNpc(neutral, "The Lady Lumbridge is yours. You'll need to fix her hull before sailing."); return true }
        chatNpc(neutral, "The Lady Lumbridge is for sale. Two thousand coins, as she stands.")
        if (!choice2("Buy the ship for 2,000 coins.", true, "Not yet.", false)) return true
        if (!access.questItemExchange(listOf("obj.coins" to 2000), emptyList())) {
            chatNpc(neutral, "Come back when you have the money."); return true
        }
        player.shipPurchased = 1
        chatNpc(happy, "She's yours. You'll need three planks and ninety steel nails to repair the hull. Don't forget a hammer.")
        return true
    }

    suspend fun Dialogue.nedQuest(): Boolean {
        if (quest.getQuestStage(player) !in 3..8) return false
        if (player.shipPurchased == 0 || player.shipRepairs < 3) {
            chatNpc(neutral, "I can sail to Crandor, but first you'll need a seaworthy ship. Klarense has one for sale in Port Sarim.")
            return true
        }
        if (player.nedRecruited == 0) {
            if (!access.questItemExchange(listOf(MAP to 1), emptyList())) {
                chatNpc(neutral, "Bring me the complete map of Crandor and I'll navigate for you.")
                return true
            }
            player.nedRecruited = 1
            quest.setQuestStage(access, 7)
            chatNpc(happy, "This map will do nicely! I'll meet you aboard the Lady Lumbridge.")
            return true
        }
        if (player.coords.x < 3000 || player.coords.z > 3300) {
            chatNpc(neutral, "I'll meet you aboard the Lady Lumbridge in Port Sarim.")
            return true
        }
        chatNpc(neutral, "Ready to sail? Bring your anti-dragon shield and prepare for battle.")
        if (choice2("Sail to Crandor.", true, "Not yet.", false)) {
            mesbox("Ned steers through the reefs. As Crandor comes into view, Elvarg attacks the ship! You escape ashore, but the Lady Lumbridge is wrecked.")
            access.telejump(CoordGrid(2833, 3253, 0))
        }
        return true
    }

    private fun ScriptContext.offering(item: String, flag: String) {
        onOpLocU(MAGIC_DOOR, item) {
            if (quest.getQuestStage(player) !in 2..7 || player.vars[flag] != 0) return@onOpLocU
            if (!questItemExchange(listOf(item to 1), emptyList())) return@onOpLocU
            VarPlayerIntMapSetter.set(player, flag, 1)
            mes("The offering disappears into the magic door.")
        }
    }

    fun ProtectedAccess.oracleChest() {
        if (!quest.isQuestInProgress(player) || !offeringsComplete(player)) {
            mes("The chest is sealed by magic."); return
        }
        givePiece(MAP_PARTS[2])
    }

    fun canOpenMagicDoor(player: Player): Boolean = offeringsComplete(player) || quest.isQuestCompleted(player)

    private fun ProtectedAccess.mazeChest() {
        if (quest.getQuestStage(player) !in 3..7) return
        givePiece(MAP_PARTS[0])
    }

    private fun ProtectedAccess.givePiece(piece: String) {
        if (playerContainsObj(piece) || playerContainsObj(MAP) || player.nedRecruited != 0) {
            mes("You've already collected this map piece."); return
        }
        if (invAdd(inv, piece).failure) mes("Make some room to take the map piece.")
        else mes("You find a piece of the map to Crandor.")
    }

    private fun ScriptContext.keyedDoor(door: String, key: String, consume: Boolean = true) {
        onOpLoc1(door) {
            if (inv.count(key) == 0) { mes("The door is locked. You need the matching key."); return@onOpLoc1 }
            if (consume && !questItemExchange(listOf(key to 1), emptyList())) return@onOpLoc1
            crossDoor(it.loc)
        }
    }

    private suspend fun ProtectedAccess.crossDoor(door: BoundLocInfo) {
        arriveDelay()
        val eastWest = door.angleId == 0 || door.angleId == 2
        val landing = if (eastWest)
            CoordGrid(if (player.coords.x >= door.x) door.x - 1 else door.x, door.z, door.level)
        else CoordGrid(door.x, if (player.coords.z >= door.z) door.z - 1 else door.z, door.level)
        locs.del(door, 2)
        walk(landing)
        delay(2)
    }

    private suspend fun ProtectedAccess.climbTo(destination: CoordGrid) {
        arriveDelay()
        anim("seq.human_climbing")
        delay(2)
        telejump(destination)
    }

    private fun ProtectedAccess.repairShip() {
        if (player.shipPurchased == 0 || player.shipRepairs >= 3) return
        if (inv.count("obj.hammer") == 0) { mes("You need a hammer."); return }
        if (!repairHull(this)) { mes("You need a plank and 30 steel nails for each repair."); return }
        player.shipRepairs++
        VarPlayerIntMapSetter.set(player, "varbit.dragonslayer_ship_onethird_fixed", if (player.shipRepairs >= 1) 1 else 0)
        VarPlayerIntMapSetter.set(player, "varbit.dragonslayer_ship_twothird_fixed", if (player.shipRepairs >= 2) 1 else 0)
        VarPlayerIntMapSetter.set(player, "varbit.dragonslayer_ship_fullyfixed", if (player.shipRepairs >= 3) 1 else 0)
        mes(if (player.shipRepairs == 3) "The hull is seaworthy." else "You nail a plank over part of the hole.")
    }

    companion object {
        const val HEAD = "obj.dragon_slayer_qip_elvargs_head"
        const val MAP = "obj.dragonmap"
        val MAP_PARTS = listOf("obj.mappart1", "obj.mappart2", "obj.mappart3")
        private const val MAZE_KEY = "obj.melzarkey"
        private const val MAGIC_DOOR = "loc.dragon_slayer_qip_magic_door"
        private const val ELVARG = "npc.elvarg_alive"
        private const val ELVARG_QUEUE = "queue.dragon_slayer_elvarg_defeated"
        private val ELVARG_SPAWN = CoordGrid(2852, 9637, 0)
        private val ARENA_ENTRY = CoordGrid(2848, 9635, 0)
        private val ARENA_EXIT = CoordGrid(2845, 9635, 0)
        private val OFFERINGS = listOf("varbit.dragonslayer_used_silk", "varbit.dragonslayer_used_bowl_unfired", "varbit.dragonslayer_used_lobster_pot", "varbit.dragonslayer_used_wizards_mind_bomb")
        private val INSTRUCTIONS = listOf("varbit.dragonslayer_instructions_melzar", "varbit.dragonslayer_instructions_oracle", "varbit.dragonslayer_instructions_wormbrain", "varbit.dragonslayer_instructions_ship", "varbit.dragonslayer_instructions_shield")
        private fun offeringsComplete(player: Player) = OFFERINGS.all { player.vars[it] != 0 }
        fun assembleMap(access: ProtectedAccess) = access.questItemExchange(MAP_PARTS.map { it to 1 }, listOf(MAP to 1))
        fun buyMapPiece(access: ProtectedAccess) = access.questItemExchange(listOf("obj.coins" to 10000), listOf(MAP_PARTS[1] to 1))
        fun repairHull(access: ProtectedAccess) = access.questItemExchange(listOf("obj.woodplank" to 1, "obj.nails" to 30), emptyList())
    }
}
