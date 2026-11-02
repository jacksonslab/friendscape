package org.rsmod.content.areas.city.varrock.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The traders at the Varrock Square stalls: Benny's newsstand, Eliza and Nevet. */
class VarrockSquareStalls : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(BENNY) { startDialogue(it.npc) { benny() } }
        onOpHeld1(NEWSPAPER) { ifOpenMainModal(NEWSPAPER_INTERFACE) }

        onOpNpc1(ELIZA) { startDialogue(it.npc) { eliza() } }
        onOpNpc3(ELIZA) {
            if (vars[ELIZA_MET] == 0) return@onOpNpc3
            startDialogue(it.npc) { speedrunShop() }
        }
        onOpNpc4(ELIZA) {
            if (vars[ELIZA_MET] == 0) return@onOpNpc4
            startDialogue(it.npc) { claimTrophies() }
        }

        onOpNpc1(NEVET) { startDialogue(it.npc) { nevet() } }
        onOpNpc3(NEVET) {
            if (vars[NEVET_MET] == 0) return@onOpNpc3
            startDialogue(it.npc) { eventsShop() }
        }
    }

    private suspend fun Dialogue.benny() {
        val question =
            choice4(
                "Can I have a newspaper, please?",
                "Can I have a newspaper, please?",
                "How much does a paper cost?",
                "How much does a paper cost?",
                "What is the Varrock Herald?",
                "What is the Varrock Herald?",
                "Anything interesting in there?",
                "Anything interesting in there?",
            )
        chatPlayer(quiz, question)
        when (question) {
            "What is the Varrock Herald?" ->
                chatNpc(
                    happy,
                    "The finest newspaper in Misthalin, edited, printed and published by " +
                        "myself, Benny Gutenberg.",
                )
            "Anything interesting in there?" ->
                chatNpc(laugh, "Every word of it! You'll have to buy a copy to find out.")
            else -> Unit
        }
        chatNpc(neutral, "A paper costs $NEWSPAPER_PRICE coins. Would you like one?")
        if (!choice2("Yes please.", true, "No thanks.", false)) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(neutral, "Suit yourself. Come back if you change your mind.")
            return
        }
        chatPlayer(happy, "Yes please.")
        if (access.invTotal(access.inv, COINS) < NEWSPAPER_PRICE) {
            chatPlayer(sad, "Oh, I don't seem to have enough money.")
            chatNpc(neutral, "Well, no cash, no paper.")
            return
        }
        if (!access.buyNewspaper()) {
            access.mes("You don't have enough inventory space.")
            return
        }
        objbox(NEWSPAPER, "You buy a newspaper for $NEWSPAPER_PRICE coins.")
        chatNpc(happy, "Enjoy your read!")
    }

    private suspend fun Dialogue.eliza() {
        chatNpc(happy, "Hello there, adventurer! Fancy buying something?")
        while (true) {
            val option =
                choice4(
                    "Yes please.",
                    1,
                    "What are you selling?",
                    2,
                    "I'd like to claim some trophies.",
                    3,
                    "I'll pass.",
                    4,
                )
            when (option) {
                1 -> {
                    chatPlayer(happy, "Yes please.")
                    vars[ELIZA_MET] = 1
                    speedrunShop()
                    return
                }
                2 -> {
                    chatPlayer(quiz, "What are you selling?")
                    chatNpc(
                        happy,
                        "Rewards for adventurers who race through their quests. Outfits, a " +
                            "giant stopwatch, speedy teleport scrolls and the like.",
                    )
                    chatNpc(
                        neutral,
                        "Coins don't interest me, mind. I only deal in Speedrun Points.",
                    )
                    vars[ELIZA_MET] = 1
                    chatNpc(quiz, "So, want to buy something?")
                }
                3 -> {
                    chatPlayer(quiz, "I'd like to claim some trophies.")
                    vars[ELIZA_MET] = 1
                    claimTrophies()
                    return
                }
                else -> {
                    chatPlayer(neutral, "I'll pass.")
                    chatNpc(neutral, "Fair enough.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.speedrunShop() {
        mesbox("You don't have any Speedrun Points to spend.")
    }

    private suspend fun Dialogue.claimTrophies() {
        chatNpc(neutral, "You don't have any trophies to claim right now.")
    }

    private suspend fun Dialogue.nevet() {
        chatNpc(happy, "Hello! Looking to buy?")
        while (true) {
            val option =
                if (vars[NEVET_MET] == 0) {
                    choice2("What are you selling?", 2, "No thanks.", 3)
                } else {
                    choice3("Yes please.", 1, "What are you selling?", 2, "No thanks.", 3)
                }
            when (option) {
                1 -> {
                    chatPlayer(happy, "Yes please.")
                    eventsShop()
                    return
                }
                2 -> {
                    chatPlayer(quiz, "What are you selling?")
                    chatNpc(
                        happy,
                        "All sorts! Keepsakes from the community events, tabards, emblems, " +
                            "scrolls and more.",
                    )
                    chatNpc(neutral, "I've no use for coins, though. Only Event Points will do.")
                    vars[NEVET_MET] = 1
                    chatNpc(quiz, "So, looking to buy?")
                }
                else -> {
                    chatPlayer(neutral, "No thanks.")
                    chatNpc(neutral, "Fair enough.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.eventsShop() {
        mesbox("You don't have any Event Points to spend.")
    }

    private companion object {
        const val BENNY = "npc.qip_soa_newspaperseller"
        const val ELIZA = "npc.speedrunning_shop"
        const val NEVET = "npc.events_shop"
        const val ELIZA_MET = "varbit.speedrunning_shop_owner_met"
        const val NEVET_MET = "varbit.events_shop_owner_met"
        const val NEWSPAPER_INTERFACE = "interface.newspaper"
    }
}

private const val COINS = "obj.coins"
internal const val NEWSPAPER = "obj.qip_soa_newspaper1"
internal const val NEWSPAPER_PRICE = 50

internal fun ProtectedAccess.buyNewspaper(): Boolean =
    player
        .invTransaction(inv) {
            val target = select(inv)
            delete(target, COINS.asRSCM(RSCMType.OBJ), NEWSPAPER_PRICE)
            add(target, NEWSPAPER.asRSCM(RSCMType.OBJ), count = 1)
        }
        .success
