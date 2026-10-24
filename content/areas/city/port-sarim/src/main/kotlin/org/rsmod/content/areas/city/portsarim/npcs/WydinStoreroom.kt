package org.rsmod.content.areas.city.portsarim.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.wydinEmployed by boolVarBit("varbit.pirates_treasure_wydin_job")
private var Player.rumShipped by boolVarBit("varbit.pirates_treasure_rum_shipped")

private fun Player.wearingApron(): Boolean = worn.count(WHITE_APRON) > 0

internal suspend fun Dialogue.askWydinForJob() {
    chatPlayer(quiz, "Can I get a job here?")
    chatNpc(
        neutral,
        "Well, you're keen, I'll give you that. Okay, I'll give you a go. Have you got your " +
            "own white apron?",
    )
    if (!player.wearingApron() && player.inv.count(WHITE_APRON) == 0) {
        chatPlayer(sad, "No, I haven't.")
        chatNpc(
            neutral,
            "Well, you can't work here unless you have a white apron. Health and safety " +
                "regulations, you understand.",
        )
        chatPlayer(quiz, "Where can I get one of those?")
        chatNpc(
            neutral,
            "Well, I get all of mine over at the clothing shop in Varrock. They sell them cheap " +
                "there.",
        )
        chatNpc(
            neutral,
            "Oh, and I'm sure that I've seen a spare one over in Gerrant's fish store " +
                "somewhere. It's the little place just north of here.",
        )
        return
    }
    chatPlayer(happy, "Yes, I have one right here.")
    player.wydinEmployed = true
    chatNpc(
        happy,
        "Wow - you are well prepared! You're hired. Go through to the back and tidy up for me, " +
            "please.",
    )
    if (!player.wearingApron()) {
        chatNpc(neutral, "You need to put your white apron on first though.")
    }
}

internal suspend fun Dialogue.wydinEmployee(openShop: () -> Unit) {
    chatNpc(quiz, "Is it nice and tidy round the back now?")
    val topic =
        choice4(
            "Yes, can I work out front now?",
            1,
            "Yes, are you going to pay me yet?",
            2,
            "No, it's a complete mess.",
            3,
            "Can I buy something please?",
            4,
        )
    when (topic) {
        1 -> {
            chatPlayer(quiz, "Yes, can I work out front now?")
            chatNpc(neutral, "No, I'm the one who works here.")
        }
        2 -> {
            chatPlayer(quiz, "Yes, are you going to pay me yet?")
            chatNpc(shifty, "Umm... No, not yet.")
        }
        3 -> {
            chatPlayer(sad, "No, it's a complete mess.")
            chatNpc(neutral, "Ah well, it'll give you something to do, won't it.")
        }
        else -> {
            chatPlayer(quiz, "Can I buy something please?")
            chatNpc(happy, "Yes, of course.")
            openShop()
        }
    }
}

class WydinStoreroom
@Inject
constructor(private val locRepo: LocRepository, private val npcRepo: NpcRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.wydindoor") { openBackDoor(it.loc) }
        onOpLoc1("loc.grocerycrate") { searchCrate() }
    }

    private suspend fun ProtectedAccess.openBackDoor(door: BoundLocInfo) {
        arriveDelay()
        val insideStoreroom = coords.x < door.coords.x
        if (!insideStoreroom && !mayEnter(door)) {
            return
        }
        val dest = if (insideStoreroom) door.coords else door.coords.translateX(-1)
        val openCoords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        locRepo.del(door, DOOR_OPEN_TICKS)
        locRepo.add(
            openCoords,
            "loc.wydindooropen",
            DOOR_OPEN_TICKS,
            door.turnAngle(rotations = 1),
            door.shape,
        )
        soundSynth("synth.door_open")
        playerMove(dest)
    }

    private suspend fun ProtectedAccess.mayEnter(door: BoundLocInfo): Boolean {
        if (player.wydinEmployed && player.wearingApron()) {
            return true
        }
        val wydin = findWydin(door)
        val employed = player.wydinEmployed
        val conversation: suspend Dialogue.() -> Unit = {
            if (employed) {
                wydinSays("Can you put your white apron on before going in there, please?")
            } else {
                wydinSays(
                    "Hey, you can't go in there. Only employees of the grocery store can go in."
                )
                if (choice2("Well, can I get a job here?", true, "Sorry, I didn't realise.", false)) {
                    askWydinForJob()
                } else {
                    chatPlayer(neutral, "Sorry, I didn't realise.")
                }
            }
        }
        if (wydin != null) {
            startDialogue(wydin, conversation = conversation)
        } else {
            startDialogue(conversation)
        }
        return false
    }

    private suspend fun Dialogue.wydinSays(text: String) {
        if (npc != null) {
            chatNpc(neutral, text)
        } else {
            chatNpcSpecific("Wydin", WYDIN, neutral, text)
        }
    }

    private fun findWydin(door: BoundLocInfo): Npc? =
        npcRepo.findAll(ZoneKey.from(door.coords), zoneRadius = 1).firstOrNull { it.isType(WYDIN) }

    private suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()
        if (player.rumShipped) {
            if (invAdd(inv, "obj.karamja_rum").failure) {
                mes("There are a lot of bananas in the crate.")
                mes("You'll need some free space to take your rum.")
                return
            }
            player.rumShipped = false
            anim("seq.human_pickuptable")
            mes("There are a lot of bananas in the crate.")
            mes("You find your bottle of rum in amongst the bananas.")
            return
        }
        mes("There are a lot of bananas in the crate.")
        startDialogue {
            if (!choice2("Yes.", true, "No.", false, title = "Do you want to take a banana?")) {
                return@startDialogue
            }
            if (access.invAdd(access.inv, "obj.banana").success) {
                access.anim("seq.human_pickuptable")
                access.mes("You take a banana.")
            }
        }
    }

    private companion object {
        const val WYDIN = "npc.wydin"
        const val DOOR_OPEN_TICKS = 3
    }
}

private const val WHITE_APRON = "obj.white_apron"
