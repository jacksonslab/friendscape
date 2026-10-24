package org.rsmod.content.areas.misc.blackknights

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The guards on the Black Knights' Fortress, and the large doors they watch. */
class FortressGuard @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (guard in GUARDS) {
            onOpNpc1(guard) { startDialogue(it.npc) { turnedAway() } }
        }
        for (door in LARGE_DOORS) {
            onOpLoc1(door) { largeDoor(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.largeDoor(door: BoundLocInfo) {
        if (door.coords.x != FORTRESS_DOOR_X || door.coords.z !in FORTRESS_DOOR_Z) {
            mes("You can't open this door.")
            return
        }
        arriveDelay()
        val inside = coords.x > door.coords.x
        if (!inside && !wearingUniform(player)) {
            startDialogue { turnedAway() }
            return
        }
        val dest = if (inside) door.coords else door.coords.translateX(1)
        locRepo.del(door, DOOR_OPEN_TICKS)
        soundSynth("synth.bigdoor_open")
        playerMove(dest)
    }

    private fun wearingUniform(player: Player): Boolean =
        player.worn.count(UNIFORM_BODY) > 0 && player.worn.count(UNIFORM_HELM) > 0

    private suspend fun Dialogue.turnedAway() {
        guardSays(angry, "Get lost. This is private property.")
        chatPlayer(angry, "Yes, but I work here!")
        guardSays(
            angry,
            "I might be new here but I can tell that's not true! You're not even wearing proper " +
                "uniform!",
        )
        if (choice2("Oh pleeeaaase let me in!", true, "So what is this uniform?", false)) {
            chatPlayer(sad, "Oh pleeeaaase let me in!")
            guardSays(angry, "Go away. You're getting annoying.")
            return
        }
        chatPlayer(quiz, "So what is this uniform?")
        guardSays(
            neutral,
            "Well you can see me wearing it. It's an iron chainbody and a medium bronze helm.",
        )
        chatPlayer(neutral, "Hmmm... I wonder if I can make that or get some in the local towns....")
        guardSays(quiz, "What was that you muttered?")
        chatPlayer(happy, "Oh, nothing important!")
    }

    private suspend fun Dialogue.guardSays(mesanim: MesAnimType, text: String) {
        if (npc != null) {
            chatNpc(mesanim, text)
        } else {
            chatNpcSpecific("Fortress Guard", GUARDS.first(), mesanim, text)
        }
    }

    private companion object {
        private const val UNIFORM_BODY = "obj.iron_chainbody"
        private const val UNIFORM_HELM = "obj.bronze_med_helm"
        private const val FORTRESS_DOOR_X = 3007
        private const val DOOR_OPEN_TICKS = 2

        private val FORTRESS_DOOR_Z = 3515..3516
        private val GUARDS = listOf("npc.fortressguard_01", "npc.fortressguard_02")
        private val LARGE_DOORS =
            listOf("loc.inaclefayeunopenabledoorl", "loc.inaclefayeunopenabledoorr")
    }
}
