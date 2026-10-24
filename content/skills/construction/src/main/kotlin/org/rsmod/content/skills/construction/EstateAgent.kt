package org.rsmod.content.skills.construction

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sells a first house and moves it between towns; the relocation fees are in [HouseLocation]. */
class EstateAgent @Inject constructor(private val manager: HouseManager) : PluginScript() {
    private var Player.houseLocation by intVarBit(VARBIT_HOUSE_LOCATION)

    override fun ScriptContext.startup() {
        for (npc in AGENTS) {
            onOpNpc1(npc) { startDialogue(it.npc) { talk() } }
            onOpNpc3(npc) { startDialogue(it.npc) { relocate() } }
        }
    }

    private suspend fun Dialogue.talk() {
        chatNpc(happy, "Hello. Welcome to the estate agent's office. How can I help you?")
        if (player.houseLocation == 0) {
            val buy =
                choice2("How can I get a house?", true, "Never mind.", false)
            if (buy) {
                offerHouse()
            }
            return
        }
        val move = choice2("Can you move my house please?", true, "Never mind.", false)
        if (move) {
            relocate()
        }
    }

    private suspend fun Dialogue.offerHouse() {
        chatPlayer(quiz, "How can I get a house?")
        chatNpc(
            happy,
            "I can sell you a starter house in Rimmington for ${HOUSE_PRICE.formatted()} coins. " +
                "As your Construction improves you can have it moved to other towns.",
        )
        val buy = choice2("Yes please, I'll buy one.", true, "No thanks.", false)
        if (!buy) {
            return
        }
        if (player.houseLocation != 0) {
            chatNpc(neutral, "You already own a house.")
            return
        }
        if (access.invDel(access.inv, OBJ_COINS, HOUSE_PRICE).failure) {
            chatNpc(neutral, "You'll need ${HOUSE_PRICE.formatted()} coins for that.")
            return
        }
        player.houseLocation = HouseLocation.RIMMINGTON
        chatNpc(
            happy,
            "Thank you. Go through the house portal in Rimmington and your new house will be " +
                "ready for you to start building in.",
        )
    }

    private suspend fun Dialogue.relocate() {
        val current = player.houseLocation
        if (current == 0) {
            chatNpc(neutral, "You don't have a house for me to move. Talk to me if you'd like one.")
            return
        }
        if (manager.ownedBy(player) != null) {
            chatNpc(neutral, "I can't move your house while people are in it.")
            return
        }
        val towns = HouseLocation.ALL.filter { it.id != current }
        val labels =
            towns.map { "${it.name} (level ${it.level}, ${it.cost.formatted()} coins)" } + "Cancel"
        val town = towns.getOrNull(access.menu("Where would you like your house?", false, labels))
        if (town == null) {
            return
        }
        if (player.baseConstructionLvl < town.level) {
            chatNpc(neutral, "You need a Construction level of ${town.level} to live in ${town.name}.")
            return
        }
        if (access.invDel(access.inv, OBJ_COINS, town.cost).failure) {
            chatNpc(neutral, "Moving your house to ${town.name} costs ${town.cost.formatted()} coins.")
            return
        }
        player.houseLocation = town.id
        chatNpc(happy, "All done. Your house now stands in ${town.name}; use the portal there to visit it.")
    }

    private fun Int.formatted(): String = "%,d".format(this)

    private companion object {
        const val HOUSE_PRICE = 1000

        val AGENTS: List<String> = listOf("npc.poh_estate_agent", "npc.prif_estate_agent")
    }
}
