package org.rsmod.content.quest.area.portsarim.piratestreasure

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class PiratesTreasureQuest
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
) :
    QuestScript(
        QUEST_KEY,
        "varp.hunt",
        rewards { item(CASKET, label = "One-Eyed Hector's Treasure Chest") },
        ItemRewardDisplay(CASKET, zoom = 300),
        questVarbit = "varbit.pirates_treasure_progress",
    ),
    SpadeDigHook {

    private val gardeners = hashMapOf<PlayerUid, Npc>()

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Pirate's Treasure end state is ${quest.maxSteps} in the cache dbrow, " +
                "but digging up the treasure writes $STAGE_COMPLETE."
        }

        onOpNpc1(FRANK) { startDialogue(it.npc) { redbeardFrank() } }
        onOpNpcU(FRANK) {
            when {
                it.objType.isType(RUM) -> startDialogue(it.npc) { rumOnFrank() }
                it.objType.isType(CASKET) -> startDialogue(it.npc) { casketOnFrank() }
                else -> mes("Nothing interesting happens.")
            }
        }
        onOpLoc1(CHEST) { mes("The chest is locked.") }
        onOpLocU(CHEST, KEY) { unlockChest(it.loc) }
        onOpHeld1(MESSAGE) { readMessage() }
        onOpHeld1(CASKET) { openCasket() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Redbeard Frank<col=000080> in <col=800000>Port Sarim<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val stage = stage(access.player)
            if (stage == STAGE_STARTED) {
                line(
                    "I have spoken to <red>Redbeard Frank</red>. He has agreed to tell me the " +
                        "location of some <red>treasure</red> for some <red>Karamjan rum</red>."
                )
                line(
                    "The <red>customs officers</red> on <red>Karamja</red> won't let me take rum " +
                        "off the island, so I will need to find a way to smuggle it past them."
                )
                return@questJournal
            }
            strike(
                "I have spoken to Redbeard Frank. He has agreed to tell me the location of some " +
                    "treasure for some Karamjan rum."
            )
            strike("I have smuggled some rum off Karamja, and given it to Frank.")
            if (stage == STAGE_KEY_GIVEN) {
                line(
                    "He has given me a <red>key</red> to <red>One-Eyed Hector's</red> chest. It " +
                        "is upstairs in the <red>Blue Moon Inn</red> in <red>Varrock</red>."
                )
                return@questJournal
            }
            strike("I have opened One-Eyed Hector's chest and found a message inside.")
            line(
                "The message says the treasure is buried in the <red>park</red> in " +
                    "<red>Falador</red>, where <red>Saradomin</red> points to the X."
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I smuggled some Karamjan rum off Karamja for Redbeard Frank, and he gave me the " +
                    "key to One-Eyed Hector's chest in the Blue Moon Inn."
            )
            line(
                "The chest held a message that led me to Falador Park, where I dug up Hector's " +
                    "treasure."
            )
        }

    private fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun Dialogue.lad(): String = if (isLad()) "lad" else "lass"

    private fun Dialogue.isLad(): Boolean = player.appearance.bodyType == Constants.bodytype_a

    private suspend fun Dialogue.redbeardFrank() {
        chatNpc(laugh, "Arr, Matey!")
        if (stage(player) == STAGE_STARTED) {
            askForRum()
            return
        }
        while (true) {
            val options = buildList {
                if (stage(player) == 0) add("I'm in search of treasure." to FrankTopic.Treasure)
                if (stage(player) == STAGE_KEY_GIVEN && !access.playerContainsObj(KEY)) {
                    add("I seem to have lost my chest key..." to FrankTopic.LostKey)
                }
                add("Arr!" to FrankTopic.Arr)
                add("Do you have anything for trade?" to FrankTopic.Trade)
            }
            when (menu(options)) {
                FrankTopic.Treasure -> return searchOfTreasure()
                FrankTopic.LostKey -> return replaceKey()
                FrankTopic.Arr -> {
                    chatPlayer(laugh, "Arr!")
                    chatNpc(laugh, "Arr!")
                }
                FrankTopic.Trade -> {
                    chatPlayer(quiz, "Do you have anything for trade?")
                    chatNpc(
                        neutral,
                        "Nothin' at the moment, but then again the Customs Agents are on the " +
                            "warpath right now.",
                    )
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.searchOfTreasure() {
        chatPlayer(shifty, "I'm in search of treasure.")
        chatNpc(
            shifty,
            "Arr, treasure you be after eh? Well I might be able to tell you where to find " +
                "some... For a price...",
        )
        chatPlayer(quiz, "What sort of price?")
        chatNpc(
            neutral,
            "Well for example if you can get me a bottle of rum... Not just any rum mind...",
        )
        chatNpc(
            happy,
            "I'd like some rum made on Karamja Island. There's no rum like Karamja Rum!",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "Not right now.")
            chatNpc(
                neutral,
                "Fair enough. I'll still be here and thirsty whenever you feel like helpin' out.",
            )
            return
        }
        quest.setQuestStage(access, STAGE_STARTED)
        chatPlayer(neutral, "Ok, I will bring you some Karamja Rum.")
        chatNpc(happy, "Yer a saint, although it'll take a miracle to get it off Karamja.")
        chatPlayer(quiz, "What do you mean?")
        customsWarning()
        chatPlayer(neutral, "Well I'll give it a shot.")
        chatNpc(happy, "Arr, that's the spirit!")
    }

    private suspend fun Dialogue.customsWarning() {
        chatNpc(
            neutral,
            "The Customs office has been clampin' down on the export of spirits. You seem like " +
                "a resourceful young ${lad()}, I'm sure ye'll be able to find a way to slip the " +
                "stuff past them.",
        )
    }

    private suspend fun Dialogue.askForRum() {
        chatNpc(quiz, "Have ye brought some rum for yer ol' mate Frank?")
        if (player.inv.count(RUM) == 0) {
            chatPlayer(neutral, "No, not yet.")
            chatNpc(neutral, "Not surprising, tis no easy task to get it off Karamja.")
            chatPlayer(quiz, "What do you mean?")
            customsWarning()
            chatPlayer(neutral, "Well I'll give it another shot.")
            return
        }
        chatPlayer(happy, "Yes, I've got some.")
        tradeRumForKey()
    }

    private suspend fun Dialogue.rumOnFrank() {
        when {
            stage(player) == STAGE_STARTED -> {
                chatPlayer(happy, "Frank, I have some Karamja Rum.")
                tradeRumForKey()
            }
            stage(player) > STAGE_STARTED ->
                chatNpc(
                    happy,
                    "Thanks fer the thought, but I've still got the bottle ye brought me.",
                )
            else -> access.mes("Nothing interesting happens.")
        }
    }

    private suspend fun Dialogue.casketOnFrank() {
        if (!quest.isQuestCompleted(player)) {
            access.mes("Nothing interesting happens.")
            return
        }
        chatPlayer(happy, "I have the treasure, would you like a share?")
        chatNpc(neutral, "No ${lad()}, you got it fair and square.")
        chatNpc(happy, "You enjoy it. It's what Hector would have wanted.")
    }

    private suspend fun Dialogue.tradeRumForKey() {
        chatNpc(
            shifty,
            "Now a deal's a deal, I'll tell ye about the treasure. I used to serve under a " +
                "pirate captain called One-Eyed Hector.",
        )
        chatNpc(
            shifty,
            "Hector were very successful and became very rich. But about a year ago we were " +
                "boarded by the Customs and Excise Agents.",
        )
        chatNpc(
            sad,
            "Hector were killed along with many of the crew, I were one of the few to escape " +
                "and I escaped with this.",
        )
        if (!swapRumForKey()) {
            return
        }
        quest.setQuestStage(access, STAGE_KEY_GIVEN)
        objbox(KEY, "Frank happily takes the rum... and hands you a key.")
        chatNpc(
            shifty,
            "This be Hector's key. I believe it opens his chest in his old room in the Blue " +
                "Moon Inn in Varrock.",
        )
        chatNpc(neutral, "With any luck his treasure will be in there.")
        val why =
            menu("Ok thanks, I'll go and get it." to false, "So why didn't you ever get it?" to true)
        if (why) {
            chatPlayer(quiz, "So why didn't you ever get it?")
            chatNpc(
                sad,
                "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken trouble maker.",
            )
        } else {
            chatPlayer(neutral, "Ok thanks, I'll go and get it.")
        }
    }

    private fun Dialogue.swapRumForKey(): Boolean = swapRumForKey(access)

    private suspend fun Dialogue.replaceKey() {
        chatPlayer(sad, "I seem to have lost my chest key...")
        chatNpc(laugh, "Arr, silly you. Fortunately I took the precaution to have another one made.")
        if (access.invAdd(access.inv, KEY).failure) {
            chatNpc(neutral, "Make some room in yer pack and I'll hand it over.")
            return
        }
        objbox(KEY, "Frank hands you a chest key.")
    }

    private suspend fun ProtectedAccess.unlockChest(chest: BoundLocInfo) {
        arriveDelay()
        soundSynth("synth.unlock")
        mes("You unlock the chest.")
        if (stage(player) < STAGE_KEY_GIVEN || stage(player) >= STAGE_COMPLETE) {
            mes("The chest is empty.")
            return
        }
        if (playerContainsObj(MESSAGE)) {
            mes("The chest is empty.")
            return
        }
        locRepo.change(chest, OPEN_CHEST, CHEST_OPEN_TICKS)
        mes("All that's in the chest is a message...")
        if (invAdd(inv, MESSAGE).failure) {
            mes("You don't have enough room to take the message.")
            return
        }
        mes("You take the message from the chest.")
    }

    private fun ProtectedAccess.readMessage() {
        if (stage(player) == STAGE_KEY_GIVEN) {
            quest.setQuestStage(this, STAGE_MESSAGE_READ)
        }
        ifOpenMainModal(MESSAGE_SCROLL)
        for (line in 1..MESSAGE_LINES) {
            ifSetText("$MESSAGE_LINE${line}_hw", MESSAGE_TEXT.getOrElse(line) { "" })
        }
    }

    private suspend fun ProtectedAccess.openCasket() {
        if (!openCasket(this)) {
            mes("You don't have enough inventory space to open the casket.")
            return
        }
        mes("You open the casket, and find One-Eyed Hector's treasure.")
    }

    override fun claims(player: Player): Boolean =
        player.coords == TREASURE_SPOT && stage(player) == STAGE_MESSAGE_READ

    override suspend fun ProtectedAccess.dig() {
        val gardener = gardeners[player.uid]
        if (gardener == null) {
            spawnGardener()
            return
        }
        if (gardener.isSlotAssigned && gardener.hitpoints > 0) {
            mes("You can't dig with the gardener attacking you!")
            return
        }
        if (!quest.completeQuest(this)) {
            mes("You need some free inventory space to take the treasure.")
            return
        }
        gardeners.remove(player.uid)
        startDialogue {
            objbox(CASKET, "You dig a hole in the ground and find a little chest of treasure.")
        }
    }

    private fun ProtectedAccess.spawnGardener() {
        val gardener = Npc(GARDENER, GARDENER_SPAWN)
        npcRepo.add(gardener, GARDENER_LIFESPAN)
        gardener.say("First moles, now this! Take this, vandal!")
        gardener.opPlayer2(player, aiPlayerInteractions)
        gardeners[player.uid] = gardener
    }

    private enum class FrankTopic {
        Treasure,
        LostKey,
        Arr,
        Trade,
    }

    companion object {
        const val QUEST_KEY = "quest_piratestreasure"

        const val STAGE_STARTED = 1
        const val STAGE_KEY_GIVEN = 2
        const val STAGE_MESSAGE_READ = 3
        const val STAGE_COMPLETE = 4

        const val RUM = "obj.karamja_rum"
        const val KEY = "obj.chest_key"
        const val MESSAGE = "obj.piratemessage"
        const val CASKET = "obj.pirate_casket"
        const val CASKET_COINS = 450

        private const val FRANK = "npc.redbeard_frank"
        private const val GARDENER = "npc.pirate_irate_gardener"
        private const val CHEST = "loc.piratechest"
        private const val OPEN_CHEST = "loc.piratechestopen"
        private const val MESSAGE_SCROLL = "interface.messagescroll_handwriting"
        private const val MESSAGE_LINE = "component.messagescroll_handwriting:messagescroll"

        private const val CHEST_OPEN_TICKS = 4
        private const val GARDENER_LIFESPAN = 300
        private const val MESSAGE_LINES = 11

        private val MESSAGE_TEXT =
            mapOf(
                5 to "Visit the city of the White Knights. In the park,",
                6 to "Saradomin points to the X which marks the spot.",
            )

        private val TREASURE_SPOT = CoordGrid(2999, 3383, 0)
        private val GARDENER_SPAWN = CoordGrid(2997, 3382, 0)

        /** Takes one Karamjan rum and gives the chest key, or changes nothing. */
        fun swapRumForKey(access: ProtectedAccess): Boolean =
            access.player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                delete {
                    from = inventory
                    obj = RUM.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = KEY.asRSCM()
                    strictCount = 1
                }
            }.success

        /** Swaps the casket for Hector's treasure in one transaction, or changes nothing. */
        fun openCasket(access: ProtectedAccess): Boolean =
            access.player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                delete {
                    from = inventory
                    obj = CASKET.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = "obj.coins".asRSCM()
                    strictCount = CASKET_COINS
                }
                insert {
                    into = inventory
                    obj = "obj.gold_ring".asRSCM()
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = "obj.emerald".asRSCM()
                    strictCount = 1
                }
            }.success
    }
}
