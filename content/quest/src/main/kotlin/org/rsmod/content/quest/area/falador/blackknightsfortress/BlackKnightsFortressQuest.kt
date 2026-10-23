package org.rsmod.content.quest.area.falador.blackknightsfortress

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.hasQuestPointsFor
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.requiredQuestPoints
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

private var Player.cauldronSabotaged by intVarBit("varbit.spy_cauldron_multi")

class BlackKnightsFortressQuest @Inject constructor(private val locRepo: LocRepository) :
    QuestScript(
        "quest_blackknightsfortress",
        "varp.spy",
        rewards { item("obj.coins", COIN_REWARD, label = "2,500 Coins") },
        ItemRewardDisplay(CABBAGE, zoom = 250),
        questVarbit = "varbit.black_knights_fortress_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Black Knights' Fortress end state is ${quest.maxSteps} in the cache dbrow, " +
                "but Sir Amik's reward writes $STAGE_COMPLETE."
        }

        onPlayerLogin { syncCauldron(player) }
        onOpNpc1("npc.sir_amik_varze") { startDialogue(it.npc) { sirAmik() } }
        onOpLoc1("loc.bkfortressdoor1") { walkThrough(it.loc) }
        onOpLoc1("loc.bkfortressdoor2") { meetingRoomDoor(it.loc) }
        onOpLoc1("loc.bkfortressdoor3") { walkThrough(it.loc) }
        onOpLoc1("loc.bksecretdoor") { pushWall(it.loc) }
        onOpLoc1("loc.witchgrill") { listenAtGrill() }
        onOpLocU("loc.blackknighthole", CABBAGE) { dropCabbage() }
        onOpLocU("loc.blackknighthole", DRAYNOR_CABBAGE) {
            mes("This is the wrong sort of cabbage!")
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Sir Amik Varze<col=000080> on the top floor of " +
            "<col=800000>Falador Castle<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val stage = stage(access.player)
            strike("Sir Amik Varze has asked me to spy on the Black Knights.")
            if (stage == STAGE_STARTED) {
                line(
                    "I need to get inside the <red>Black Knights' Fortress</red>, north of " +
                        "<red>Falador</red>, and find out about their <red>secret weapon</red>."
                )
                line(
                    "The guards will only let me in if I'm wearing their uniform: an " +
                        "<red>iron chainbody</red> and a <red>bronze med helm</red>."
                )
                return@questJournal
            }
            strike("I overheard the Black Knights' plans to brew an invincibility potion.")
            if (stage == STAGE_OVERHEARD) {
                line(
                    "The potion needs a special cabbage. If I drop an ordinary " +
                        "<red>cabbage</red> into their cauldron, it should ruin the potion."
                )
                return@questJournal
            }
            strike("I ruined the potion with an ordinary cabbage.")
            line("I should return to <red>Sir Amik Varze</red> in <red>Falador Castle</red>.")
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I infiltrated the Black Knights' Fortress, overheard their plan to brew an " +
                    "invincibility potion and ruined it with an ordinary cabbage."
            )
            line("Sir Amik Varze rewarded me for my spying.")
        }

    private fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun syncCauldron(player: Player) {
        val sabotaged = if (stage(player) >= STAGE_SABOTAGED) 1 else 0
        if (player.cauldronSabotaged != sabotaged) {
            player.cauldronSabotaged = sabotaged
        }
    }

    private suspend fun Dialogue.sirAmik() {
        when (stage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED,
            STAGE_OVERHEARD -> missionUnderway()
            STAGE_SABOTAGED -> reward()
            else -> {
                chatNpc(
                    happy,
                    "Hello again, friend. The Black Knights are still reeling, thanks to you.",
                )
                chatPlayer(happy, "Glad I could help.")
            }
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(neutral, "Greetings friend, I am Sir Amik Varze.")
        if (!menu("I seek a quest!" to true, "I don't, I'm just looking around." to false)) {
            chatPlayer(neutral, "I don't, I'm just looking around.")
            chatNpc(neutral, "Well, feel free to look around, but please don't touch anything.")
            return
        }
        chatPlayer(happy, "I seek a quest!")
        if (!player.hasQuestPointsFor(quest)) {
            chatNpc(
                neutral,
                "I'm sorry, but this mission needs someone with more experience. Come back " +
                    "when you have earned ${quest.requiredQuestPoints} quest points.",
            )
            return
        }
        chatNpc(
            neutral,
            "Well, I need some spy work doing but it's quite dangerous. It will involve going " +
                "into the Black Knights' fortress.",
        )
        val brave =
            menu(
                "I laugh in the face of danger!" to true,
                "I go and cower in a corner at the first sign of danger!" to false,
            )
        if (!brave) {
            chatPlayer(worried, "I go and cower in a corner at the first sign of danger!")
            chatNpc(neutral, "Err.... Well. I... Spy work is not for the faint-hearted. Never mind.")
            return
        }
        chatPlayer(happy, "I laugh in the face of danger!")
        chatNpc(
            neutral,
            "Well that's good. Don't get too overconfident though. You've come along at just " +
                "the right time actually. All of my knights are already known to the Black " +
                "Knights.",
        )
        chatNpc(neutral, "Subtlety isn't exactly our strong point.")
        chatPlayer(quiz, "So what needs doing?")
        chatNpc(
            neutral,
            "Well, the Black Knights have started making strange threats to us; demanding " +
                "large amounts of money and land, and threatening to invade Falador if we don't " +
                "pay.",
        )
        chatNpc(
            neutral,
            "Now normally this wouldn't be a problem, but they claim to have a powerful new " +
                "secret weapon. What I want you to do is get inside their fortress, find out " +
                "what their secret weapon is, and then sabotage it.",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "I'll think about it.")
            chatNpc(neutral, "Don't take too long. Falador's safety may depend on it.")
            return
        }
        quest.setQuestStage(access, STAGE_STARTED)
        chatPlayer(happy, "Ok, I'll give it a try.")
        chatNpc(
            neutral,
            "Their fortress is to the north of here, just west of Ice Mountain. I believe " +
                "their guards wear an iron chainbody and a bronze med helm; if you dress the " +
                "same, you may be able to slip past them.",
        )
        chatNpc(happy, "Good luck, and be careful.")
    }

    private suspend fun Dialogue.missionUnderway() {
        chatNpc(quiz, "How's the mission going?")
        if (stage(player) == STAGE_STARTED) {
            chatPlayer(neutral, "I haven't managed to find out what the secret weapon is yet.")
            chatNpc(
                neutral,
                "Remember, the guards wear an iron chainbody and a bronze med helm. Dress the " +
                    "same and you might get in.",
            )
            return
        }
        chatPlayer(
            neutral,
            "I have found out that the Black Knights are brewing an invincibility potion.",
        )
        chatNpc(shocked, "That's terrible! You must stop them!")
        chatPlayer(neutral, "I think I know how. It needs a special cabbage...")
        chatNpc(neutral, "Then see what you can do. Hurry!")
    }

    private suspend fun Dialogue.reward() {
        chatPlayer(happy, "I have ruined the Black Knights' invincibility potion.")
        chatPlayer(
            happy,
            "That should put a stop to your problem and an end to their little schemes.",
        )
        chatNpc(
            happy,
            "Yes we have just received a message from the Black Knights saying they withdraw " +
                "their demands, which would seem to confirm your story.",
        )
        chatPlayer(quiz, "You said you were going to pay me.")
        if (!quest.completeQuest(access)) {
            chatNpc(neutral, "Make some room in your pack and I'll hand over your payment.")
            return
        }
        chatNpc(happy, "Yes, that's right. Here you go. Well done, and thank you!")
    }

    private suspend fun ProtectedAccess.meetingRoomDoor(door: BoundLocInfo) {
        arriveDelay()
        if (coords.x >= door.coords.x) {
            walkThrough(door)
            return
        }
        var enter = false
        startDialogue {
            chatNpcSpecific(
                "Fortress Guard",
                GUARD,
                neutral,
                "I wouldn't go in there if I woz you. Those Black Knights are in an important " +
                    "meeting. They said they'd kill anyone who went through that door.",
            )
            enter = menu(STAY_OUT to false, GO_IN to true)
            if (enter) chatPlayer(angry, GO_IN) else chatPlayer(neutral, STAY_OUT)
        }
        if (enter) {
            walkThrough(door)
        }
    }

    private suspend fun ProtectedAccess.walkThrough(door: BoundLocInfo) {
        arriveDelay()
        val dest = door.otherSide(coords)
        locRepo.del(door, DOOR_OPEN_TICKS)
        soundSynth("synth.door_open")
        playerMove(dest)
    }

    private suspend fun ProtectedAccess.pushWall(wall: BoundLocInfo) {
        arriveDelay()
        mes("You push against the wall and find a secret passage.")
        val dest = wall.otherSide(coords)
        locRepo.del(wall, DOOR_OPEN_TICKS)
        playerMove(dest)
    }

    private suspend fun ProtectedAccess.listenAtGrill() {
        arriveDelay()
        if (stage(player) != STAGE_STARTED) {
            mes("You can't hear much of interest through the grill.")
            return
        }
        startDialogue {
            captain("So... how's the secret weapon coming along?")
            witch(
                "The invincibility potion is almost ready... It's taken me FIVE YEARS, but it's " +
                    "almost ready."
            )
            witch(
                "Greldo, the goblin here, is just going to fetch the last ingredient for me. " +
                    "It's a specially grown cabbage, from my cabbage garden at Draynor Manor."
            )
            witch("Greldo! Go and fetch a cabbage from Draynor Manor!")
            chatNpcSpecific("Greldo", GRELDO, neutral, "Yeth Mithreth.")
            captain("Well, hurry up! The sooner we can attack Falador the better.")
        }
        quest.setQuestStage(this, STAGE_OVERHEARD)
    }

    private suspend fun Dialogue.captain(text: String) =
        chatNpcSpecific("Black Knight Captain", CAPTAIN, neutral, text)

    private suspend fun Dialogue.witch(text: String) =
        chatNpcSpecific("Witch", WITCH, neutral, text)

    private suspend fun ProtectedAccess.dropCabbage() {
        arriveDelay()
        if (stage(player) != STAGE_OVERHEARD) {
            mes("You don't see any reason to drop a cabbage down there.")
            return
        }
        if (invDel(inv, CABBAGE).failure) {
            return
        }
        mes("You drop a cabbage down the hole.")
        mes("The cabbage lands in the cauldron below.")
        quest.setQuestStage(this, STAGE_SABOTAGED)
        syncCauldron(player)
        startDialogue {
            witch("Where has Greldo got to with that cabbage?")
            witch("Hang on... that's not one of MY cabbages! The potion is ruined!")
            captain("WHAT?! Five years of work... wasted! Find whoever did this!")
        }
    }

    /** The tile across [this] wall from [from], using the edge its angle places it on. */
    private fun BoundLocInfo.otherSide(from: CoordGrid): CoordGrid {
        val (near, far) =
            when (angle) {
                LocAngle.West -> coords.translateX(-1) to coords
                LocAngle.North -> coords to coords.translateZ(1)
                LocAngle.East -> coords to coords.translateX(1)
                LocAngle.South -> coords.translateZ(-1) to coords
            }
        return if (from == far) near else far
    }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_OVERHEARD = 2
        const val STAGE_SABOTAGED = 3
        const val STAGE_COMPLETE = 4

        const val CABBAGE = "obj.cabbage"
        const val COIN_REWARD = 2500

        private const val DRAYNOR_CABBAGE = "obj.magic_cabbage"
        private const val GUARD = "npc.fortressguard"
        private const val CAPTAIN = "npc.grillknight"
        private const val WITCH = "npc.fortwitch"
        private const val GRELDO = "npc.greldo"
        private const val DOOR_OPEN_TICKS = 2
        private const val STAY_OUT = "Okay, I won't."
        private const val GO_IN = "I don't care. I'm going in anyway."
    }
}
