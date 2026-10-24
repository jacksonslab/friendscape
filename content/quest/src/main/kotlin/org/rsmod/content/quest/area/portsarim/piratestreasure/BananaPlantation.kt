package org.rsmod.content.quest.area.portsarim.piratestreasure

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.crateBananas by intVarBit("varbit.pirates_treasure_crate_bananas")
internal var Player.crateRum by boolVarBit("varbit.pirates_treasure_crate_rum")
internal var Player.rumShipped by boolVarBit("varbit.pirates_treasure_rum_shipped")
internal var Player.luthasJob by boolVarBit("varbit.pirates_treasure_luthas_job")

class BananaPlantation @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(LUTHAS) { startDialogue(it.npc) { luthas() } }
        onOpNpc1(ZEMBO) { startDialogue(it.npc) { zembo() } }
        onOpNpc3(ZEMBO) { openSpiritsShop() }
        onOpLoc1(CRATE) { searchCrate() }
        onOpLoc2(CRATE) { fillCrate() }
        onOpLocU(CRATE, BANANA) { packBanana() }
        onOpLocU(CRATE, PiratesTreasureQuest.RUM) { stashRum() }
    }

    private suspend fun Dialogue.luthas() {
        if (player.crateBananas >= CRATE_CAPACITY) {
            crateFilled()
            return
        }
        if (player.luthasJob) {
            chatNpc(quiz, "Have you completed your task yet?")
            when (
                menu(
                    "What did I have to do again?" to LuthasTopic.Task,
                    "No, the crate isn't full yet." to LuthasTopic.NotFull,
                    "So where are these bananas going to be delivered to?" to LuthasTopic.Delivery,
                    "That customs officer is annoying isn't she?" to LuthasTopic.Customs,
                )
            ) {
                LuthasTopic.Task -> {
                    chatPlayer(quiz, "What did I have to do again?")
                    chatNpc(
                        neutral,
                        "There's a crate ready to be loaded onto the ship. If you could fill it " +
                            "up with bananas, I'll pay you $CRATE_PAYMENT gold.",
                    )
                }
                LuthasTopic.NotFull -> {
                    chatPlayer(neutral, "No, the crate isn't full yet...")
                    chatNpc(neutral, "Well come back when it is.")
                }
                LuthasTopic.Delivery -> whereAreBananasGoing()
                else -> annoyingCustomsOfficer()
            }
            return
        }
        chatNpc(happy, "Hello I'm Luthas, I run the banana plantation here.")
        val employment =
            menu(
                "Could you offer me employment on your plantation?" to true,
                "That customs officer is annoying isn't she?" to false,
            )
        if (!employment) {
            annoyingCustomsOfficer()
            return
        }
        chatPlayer(quiz, "Could you offer me employment on your plantation?")
        chatNpc(
            happy,
            "Yes, I can sort something out. There's a crate ready to be loaded onto the ship.",
        )
        chatNpc(
            neutral,
            "You wouldn't believe the demand for bananas from Wydin's shop over in Port Sarim. " +
                "I think this is the third crate I've shipped him this month..",
        )
        player.luthasJob = true
        chatNpc(happy, "If you could fill it up with bananas, I'll pay you $CRATE_PAYMENT gold.")
    }

    private suspend fun Dialogue.crateFilled() {
        chatPlayer(happy, "I've filled a crate with bananas.")
        chatNpc(happy, "Well done, here's your payment.")
        if (access.invAdd(access.inv, COINS, CRATE_PAYMENT).failure) {
            chatNpc(neutral, "You'll need to make some room in your pack for the coins first.")
            return
        }
        access.mes("Luthas hands you $CRATE_PAYMENT coins.")
        shipCrate(player)
        when (
            menu(
                "Will you pay me for another crate full?" to LuthasTopic.Again,
                "Thank you, I'll be on my way" to LuthasTopic.Leave,
                "So where are these bananas going to be delivered to?" to LuthasTopic.Delivery,
                "That customs officer is annoying isn't she?" to LuthasTopic.Customs,
            )
        ) {
            LuthasTopic.Again -> {
                chatPlayer(quiz, "Will you pay me for another crate full?")
                chatNpc(happy, "Yes certainly.")
                player.luthasJob = true
                chatNpc(
                    neutral,
                    "If you go outside you should see the old crate has been loaded on to the " +
                        "ship, and there is another empty crate in its place.",
                )
            }
            LuthasTopic.Leave -> chatPlayer(happy, "Thank you, I'll be on my way.")
            LuthasTopic.Delivery -> whereAreBananasGoing()
            else -> annoyingCustomsOfficer()
        }
    }

    private suspend fun Dialogue.whereAreBananasGoing() {
        chatPlayer(quiz, "So where are these bananas going to be delivered to?")
        chatNpc(neutral, "I sell them to Wydin who runs a grocery store in Port Sarim.")
    }

    private suspend fun Dialogue.annoyingCustomsOfficer() {
        chatPlayer(neutral, "That customs officer is annoying isn't she?")
        chatNpc(neutral, "Well I know her pretty well. She doesn't cause me any trouble any more.")
        chatNpc(
            neutral,
            "She doesn't even search my export crates any more. She knows they only contain " +
                "bananas.",
        )
        chatPlayer(quiz, "Really? How interesting. Whereabouts do you send those to?")
        chatNpc(
            neutral,
            "There is a little shop over in Port Sarim that buys them up by the crate. I " +
                "believe it is run by a man called Wydin.",
        )
    }

    private suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()
        val bananas = player.crateBananas
        when {
            bananas == 0 && player.crateRum ->
                mes(
                    "There is some rum in here, although with no bananas to cover it. It is a " +
                        "little obvious."
                )
            bananas == 0 -> mes("The crate is empty.")
            bananas >= CRATE_CAPACITY -> mes("The crate is full of bananas.")
            else -> mes("The crate is partially full of bananas.")
        }
        if (bananas > 0 && player.crateRum) {
            mes("There is also some rum stashed in here too.")
        }
    }

    private suspend fun ProtectedAccess.fillCrate() {
        arriveDelay()
        if (player.crateBananas >= CRATE_CAPACITY) {
            mes("The crate is already full.")
            return
        }
        val packed = minOf(inv.count(BANANA), CRATE_CAPACITY - player.crateBananas)
        if (packed == 0) {
            mes("You don't have any bananas to pack.")
            return
        }
        if (invDel(inv, BANANA, packed).failure) {
            return
        }
        anim(PACK_ANIM)
        player.crateBananas += packed
        mes("You fill the crate with bananas.")
        if (player.crateBananas >= CRATE_CAPACITY) {
            mes("The crate is now full.")
        }
    }

    private suspend fun ProtectedAccess.packBanana() {
        arriveDelay()
        if (player.crateBananas >= CRATE_CAPACITY) {
            mes("The crate is already full.")
            return
        }
        if (invDel(inv, BANANA).failure) {
            return
        }
        anim(PACK_ANIM)
        player.crateBananas++
        mes("You pack a banana into the crate.")
    }

    private suspend fun ProtectedAccess.stashRum() {
        arriveDelay()
        if (player.crateRum) {
            mes("There's already some rum in here...")
            return
        }
        if (invDel(inv, PiratesTreasureQuest.RUM).failure) {
            return
        }
        anim(PACK_ANIM)
        player.crateRum = true
        mes("You stash the rum in the crate.")
    }

    private suspend fun Dialogue.zembo() {
        chatNpc(
            happy,
            "Hey, are you wanting to try some of my fine wines and spirits? All brewed locally " +
                "on Karamja island.",
        )
        if (menu("Yes please." to true, "No, thank you." to false)) {
            chatPlayer(happy, "Yes please.")
            access.openSpiritsShop()
        } else {
            chatPlayer(neutral, "No, thank you.")
        }
    }

    private fun ProtectedAccess.openSpiritsShop() =
        shops.open(
            player = player,
            title = "Karamja Wines, Spirits, and Beers",
            shopInv = SPIRITS_SHOP,
            buyPercentage = 70.0,
            sellPercentage = 100.0,
            changePercentage = 2.0,
        )

    private enum class LuthasTopic {
        Task,
        NotFull,
        Delivery,
        Customs,
        Again,
        Leave,
    }

    companion object {
        const val CRATE_CAPACITY = 10
        const val CRATE_PAYMENT = 30

        private const val LUTHAS = "npc.luthas"
        private const val ZEMBO = "npc.zembo"
        private const val CRATE = "loc.bananacrate"
        private const val BANANA = "obj.banana"
        private const val COINS = "obj.coins"
        private const val SPIRITS_SHOP = "inv.boozeshop"
        private const val PACK_ANIM = "seq.human_pickuptable"

        /** Loads the full crate onto the ship: any rum hidden in it goes along to Wydin's. */
        fun shipCrate(player: Player) {
            if (player.crateRum) {
                player.rumShipped = true
            }
            player.crateRum = false
            player.crateBananas = 0
            player.luthasJob = false
        }
    }
}
