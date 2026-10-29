package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pandemonium, the Sailing introduction. Friendscape keeps Sailing open without it, so the quest
 * only tells the story and pays its rewards. The sail south is narrated: the cache's stationary
 * intro boat over the wreck stands in for Will and Anne's boat, and the cargo run is the cache's
 * own port task, handled by the normal courier ledgers and cargo holds.
 */
class Pandemonium
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val shipbuilding: Shipbuilding,
) :
    QuestScript(
        "quest_pandemonium",
        "varp.sailing_intro_primary",
        rewards {
            xp("stat.sailing", 300.0)
            item("obj.sawmill_coupon", 25, label = "25 Sawmill coupons (wood plank)")
            item("obj.boat_repair_kit", 2, label = "2 Repair kits")
            item("obj.sailing_charting_spyglass", label = "A spyglass")
        },
        ItemRewardDisplay("obj.sailing_charting_spyglass", zoom = 400),
        questVarbit = "varbit.sailing_intro",
    ) {
    private var deckAnne: Npc? = null

    override fun ScriptContext.init() {
        check(quest.maxSteps == COMPLETE) {
            "Pandemonium's end state is ${quest.maxSteps} in the cache, but the script completes at $COMPLETE."
        }
        onGameStartup { spawnNpcs() }

        onOpNpc1(WILL) { startDialogue(it.npc) { willOrAnne() } }
        onOpNpc1(ANNE) { startDialogue(it.npc) { willOrAnne() } }
        onOpLoc1(HOOK) { deployHook() }
        onOpLoc2(HOOK) { mes("This isn't your boat to modify.") }
        onOpLoc1(INTRO_HELM) { leaveIntroBoat() }

        for (steve in STEVE_VARIANTS) {
            onOpNpc1(steve) { startDialogue(it.npc) { steve() } }
        }
        onOpNpc3(STEVE_TRADER) { mes("Steve doesn't have anything to sell you yet.") }
        onOpNpc1(RIBS) { startDialogue(it.npc) { ribs() } }
        for (jim in JIM_VARIANTS) {
            onOpNpc1(jim) { startDialogue(it.npc) { juniorJim() } }
        }
        onOpNpc3(JIM_SHIPWRIGHT) { shipbuilding.commission(this) }
        onOpNpc4(JIM_SHIPWRIGHT) { mes("Boat customisation isn't available yet.") }
        onOpNpc5(JIM_SHIPWRIGHT) { mes("Your raft is always waiting at the nearest gangplank.") }
        onOpNpc1(GROG) { startDialogue(it.npc) { oldGrog() } }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Will</col> or <col=800000>Anne</col> on the docks of " +
            "<col=800000>Port Sarim</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)
            step(stage, ABOARD, "Will and Anne offered me a job salvaging a shipwreck south of Port Sarim.")
            if (stage == ABOARD) {
                line("I should lower the <red>salvaging hook</red> over the wreck.")
            }
            step(stage, OVERBOARD, "The hook brought up a medallion, and Anne pushed me overboard. I washed up on the <red>Pandemonium</red>.")
            if (stage == OVERBOARD) {
                line("I should talk to <red>'Squawking' Steve Beanie</red> in the pub.")
            }
            if (stage == MET_STEVE) {
                line("Steve wants me to ask <red>Ribs</red> about the map Will and Anne stole.")
            }
            if (stage == HEARD_RIBS) {
                line("Ribs told me about the medallion's curse. I should report back to <red>Steve</red>.")
            }
            step(stage, DELIVERING, "Steve sent me to <red>Junior Jim</red> to get a boat of my own.")
            if (stage == SENT_TO_JIM) {
                line("I should talk to <red>Junior Jim</red> on the Pandemonium docks.")
            }
            step(stage, DELIVERED, "Junior Jim traded me a raft for the old cup.")
            if (stage == DELIVERING) {
                line("I need to collect a <red>crate of ship parts</red> from the <red>Port Sarim</red> ledger table and deliver it to the <red>Pandemonium</red> ledger table.")
            }
            if (stage == DELIVERED) {
                line("I delivered Jim's crate. I should let <red>Steve</red> know.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("Will and Anne hired me to salvage a wreck, then stole its medallion and pushed me overboard.")
            line("Junior Jim traded me a raft for the old cup, and I ran my first delivery for him.")
            line("Old Grog has agreed to help track Will and Anne down.")
        }

    private fun QuestJournalBuilder.step(stage: Int, doneAt: Int, text: String) {
        if (stage >= doneAt) strike(text)
    }

    private fun spawnNpcs() {
        for ((type, coords) in STATIC_SPAWNS) {
            npcRepo.add(Npc(type, coords), Int.MAX_VALUE)
        }
        val anne = Npc(ANNE_BOAT, ANNE_DECK)
        npcRepo.add(anne, Int.MAX_VALUE)
        deckAnne = anne
    }

    private fun stage(access: ProtectedAccess): Int = quest.getQuestStage(access.player)

    private suspend fun Dialogue.willOrAnne() {
        when (stage(access)) {
            NOT_STARTED -> recruit()
            ABOARD ->
                if (onIntroBoat()) {
                    chatNpcSpecific(ANNE_NAME, ANNE, happy, "We're over the wreck! Go on, lower the salvaging hook and let's see what's down there.")
                } else {
                    chatNpcSpecific(WILL_NAME, WILL, happy, "There you are! Hop aboard, we've got a wreck to salvage.")
                    boardIntroBoat()
                }
            else -> chatNpcSpecific(WILL_NAME, WILL, neutral, "We're busy. Off you go.")
        }
    }

    private suspend fun Dialogue.recruit() {
        chatNpcSpecific(ANNE_NAME, ANNE, happy, "Ah, look what we have here, Will! This looks like someone who needs a good job!")
        chatPlayer(confused, "What?")
        chatNpcSpecific(WILL_NAME, WILL, happy, "Goodness, Anne, I think you're right! This one's clearly never worked an honest day in their life, and it's about time someone changed that!")
        chatPlayer(confused, "But I don't need a...")
        chatNpcSpecific(ANNE_NAME, ANNE, happy, "We run a salvage business. There's a wreck south of here, past the Karamja Shipyard, and we need another pair of hands.")
        if (!startQuestPrompt(quest)) {
            chatPlayer(confused, "Sorry, but I'm not looking for a job right now. I'm just going to go...")
            chatNpcSpecific(WILL_NAME, WILL, happy, "Oh, you'll be back, friend! You already know you can't turn down an opportunity like this!")
            return
        }
        chatPlayer(happy, "Alright, I'm in. What do I need to do?")
        chatNpcSpecific(WILL_NAME, WILL, happy, "Just hop aboard. We'll sail south to the wreck, and you can work the salvaging hook.")
        quest.setQuestStage(access, ABOARD)
        boardIntroBoat()
    }

    private suspend fun Dialogue.boardIntroBoat() {
        mesbox("You board Will and Anne's boat and sail south through the Bay of Sarim, past the Karamja Shipyard, until you reach the wreck.")
        access.teleport(INTRO_DECK)
    }

    private fun Dialogue.onIntroBoat(): Boolean = player.coords.level == INTRO_DECK.level && player.coords.chebyshevDistance(INTRO_DECK) <= INTRO_DECK_RADIUS

    private suspend fun ProtectedAccess.deployHook() {
        if (stage(this) != ABOARD) {
            mes("There's nothing left down there worth salvaging.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You need a free inventory space for whatever the hook brings up.")
            return
        }
        mes("You lower the salvaging hook over the wreck...")
        delay(HOOK_DELAY)
        if (stage(this) != ABOARD) {
            return
        }
        if (!PandemoniumExchanges.haulUpCup(this)) {
            mes("You need a free inventory space for whatever the hook brings up.")
            return
        }
        quest.setQuestStage(this, OVERBOARD)
        mes("...and haul up an old cup and a strange medallion.")
        startDialogue {
            chatNpcSpecific(WILL_NAME, WILL, shocked, "That medallion... Anne, that's it! That's the one!")
            chatNpcSpecific(WILL_NAME, WILL, happy, "I'll be taking that, thank you very much. You can keep the cup.")
            chatNpcSpecific(ANNE_NAME, ANNE, laugh, "And I'm afraid this is where your job ends. Mind the water!")
        }
        deckAnne?.anim(ANNE_PUSH)
        anim(HUMAN_OVERBOARD)
        delay(OVERBOARD_DELAY)
        teleport(WASHED_UP)
        resetAnim()
        mesbox("You wash up, soaked, on the shore of the Pandemonium.")
    }

    private fun ProtectedAccess.leaveIntroBoat() {
        mes("Will and Anne drop you back at the Port Sarim docks.")
        teleport(PORT_SARIM_DOCK)
    }

    private suspend fun Dialogue.steve() {
        when (stage(access)) {
            OVERBOARD -> {
                chatNpc(angry, "Squawk! Another one washed up! What are you doing on my island?")
                chatPlayer(sad, "Will and Anne hired me to salvage a wreck, then pushed me overboard.")
                chatNpc(angry, "Will and Anne! Those two stole a map to that wreck from right under my nose. There's only one rule on the Pandemonium: you don't steal from the crew.")
                chatNpc(neutral, "Ask Ribs, just north of the bar, what that map was. He knows more about old wrecks than anyone.")
                quest.setQuestStage(access, MET_STEVE)
            }
            MET_STEVE -> chatNpc(neutral, "Go and ask Ribs about that map. Squawk!")
            HEARD_RIBS -> {
                chatPlayer(worried, "Ribs says the medallion unleashed a curse, and that I should come back to you.")
                chatPlayer(quiz, "Where might Will and Anne have gone?")
                chatNpc(neutral, "Old Grog could track those two anywhere on the seas, but you'll not get far without a boat of your own.")
                chatNpc(happy, "Junior Jim on the docks will sort you out. Tell him Steve sent you.")
                quest.setQuestStage(access, SENT_TO_JIM)
            }
            SENT_TO_JIM, DELIVERING -> chatNpc(neutral, "Junior Jim's on the docks. He'll get you a boat. Squawk!")
            DELIVERED -> finish()
            COMPLETE -> chatNpc(happy, "Welcome back to the Pandemonium, captain! Squawk!")
            else -> chatNpc(neutral, "Welcome to the Pandemonium! Mind your manners and your coin purse.")
        }
    }

    private suspend fun Dialogue.finish() {
        chatNpc(happy, "Jim tells me you made your first delivery. Not bad for a Billy-no-boats!")
        chatNpc(neutral, "Old Grog's agreed to keep an eye out for Will and Anne. When he hears something, you'll be the first to know.")
        if (!quest.completeQuest(access)) {
            chatNpc(neutral, "Come back when you've got room in your pack for your reward. Squawk!")
        }
    }

    private suspend fun Dialogue.ribs() {
        if (stage(access) != MET_STEVE) {
            chatNpc(neutral, "Pull up a stool. Every wreck in these waters has a story, and I know most of them.")
            return
        }
        chatPlayer(quiz, "Steve said you know about the map Will and Anne stole?")
        chatNpc(worried, "That map led to the wreck of the Crimson Kraken, Barnacle Barnaby's ship. Nobody's touched it in years, and for good reason.")
        chatNpc(worried, "If they've taken his medallion, they've let loose his curse. You'd best tell Steve.")
        quest.setQuestStage(access, HEARD_RIBS)
    }

    private suspend fun Dialogue.juniorJim() {
        when (stage(access)) {
            SENT_TO_JIM -> tradeForRaft()
            DELIVERING -> checkDelivery()
            DELIVERED -> chatNpc(happy, "Crate's in, thanks! Go and tell Steve you're one of us now.")
            COMPLETE -> chatNpc(happy, "Need a bigger boat? I can build you one if you've got the coin.")
            else -> chatNpc(neutral, "I build boats, I fix boats, and I don't give them away. Unless you've got something worth trading.")
        }
    }

    private suspend fun Dialogue.tradeForRaft() {
        chatPlayer(neutral, "Steve sent me. I need a boat.")
        chatNpc(laugh, "A Billy-no-boats, eh? What have you got to trade?")
        if (TaskSlots.freeSlot(player, access.statBase(STAT_SAILING)) == null) {
            chatNpc(neutral, "Clear one of your port tasks first. I've got a job for you once you've got your raft.")
            return
        }
        if (PandemoniumExchanges.CUP in access.inv) {
            chatPlayer(neutral, "Just this old cup.")
            chatNpc(happy, "From the Crimson Kraken? Now that's worth a raft! Deal.")
        } else {
            chatPlayer(sad, "Nothing, I'm afraid. I lost everything when I went overboard.")
            chatNpc(neutral, "Well, Steve vouches for you. I'll put the raft on your tab.")
        }
        if (PandemoniumExchanges.takeJimsJob(access) == JimsJob.NoFreeSlot) {
            chatNpc(neutral, "Clear one of your port tasks first. I've got a job for you once you've got your raft.")
            return
        }
        quest.setQuestStage(access, DELIVERING)
        chatNpc(happy, "Your raft's moored and ready at any gangplank. Board it, take the helm, and set the sails.")
        mesbox(CourierTasks.PANDEMONIUM.flavour)
    }

    private suspend fun Dialogue.checkDelivery() {
        val task = CourierTasks.PANDEMONIUM
        if (TaskSlots.active(player).any { it.taskId == task.id }) {
            chatNpc(neutral, "That crate of ship parts won't deliver itself. It's at the Port Sarim ledger table. Bring it back to the ledger here.")
            return
        }
        chatNpc(happy, "The crate's in! You'll make a sailor yet. Go and tell Steve.")
        quest.setQuestStage(access, DELIVERED)
    }

    private suspend fun Dialogue.oldGrog() {
        chatNpc(neutral, "Arr. Will and Anne, is it? I'll find 'em. The sea always gives up its secrets to old Grog, sooner or later.")
    }

    private companion object {
        private const val NOT_STARTED = 0
        private const val ABOARD = 5
        private const val OVERBOARD = 15
        private const val MET_STEVE = 16
        private const val HEARD_RIBS = 17
        private const val SENT_TO_JIM = 18
        private const val DELIVERING = 25
        private const val DELIVERED = 40
        private const val COMPLETE = 50

        private const val HOOK_DELAY = 3
        private const val OVERBOARD_DELAY = 2
        private const val INTRO_DECK_RADIUS = 6

        private const val WILL_NAME = "Will"
        private const val ANNE_NAME = "Anne"
        private const val WILL = "npc.sailing_intro_will_vis"
        private const val ANNE = "npc.sailing_intro_anne_vis"
        private const val WILL_BOAT = "npc.sailing_intro_will_boat"
        private const val ANNE_BOAT = "npc.sailing_intro_anne_boat"
        private const val STEVE = "npc.steve_beanie"
        private const val STEVE_TRADER = "npc.steve_beanie_2ops"
        private const val RIBS = "npc.sailing_intro_ribs_vis"
        private const val JIM = "npc.junior_jim"
        private const val JIM_SHIPWRIGHT = "npc.junior_jim_4ops"
        private const val GROG = "npc.sailing_intro_grog_vis"
        private const val GROG_BASE = "npc.sailing_intro_grog"

        private const val HOOK = "loc.sailing_intro_salvaging_hook_ops"
        private const val INTRO_HELM = "loc.sailing_intro_helm"
        private const val ANNE_PUSH = "seq.sailing_intro_anne_push"
        private const val HUMAN_OVERBOARD = "seq.sailing_intro_human_overboard"

        private val STEVE_VARIANTS = listOf("npc.steve_beanie_1op", STEVE_TRADER)
        private val JIM_VARIANTS = listOf("npc.junior_jim_1op", JIM_SHIPWRIGHT)

        private val INTRO_DECK = CoordGrid(1499, 4511, 1)
        private val ANNE_DECK = CoordGrid(1498, 4513, 1)
        private val WILL_DECK = CoordGrid(1498, 4512, 1)
        private val WASHED_UP = CoordGrid(3060, 2987, 0)
        private val PORT_SARIM_DOCK = CoordGrid(3026, 3207, 0)
        private val STEVE_SPAWN = CoordGrid(3047, 2979, 0)
        private val GROG_SPAWN = CoordGrid(3048, 2980, 0)
        private val JIM_SPAWN = CoordGrid(3064, 2987, 0)

        private val STATIC_SPAWNS =
            listOf(
                STEVE to STEVE_SPAWN,
                JIM to JIM_SPAWN,
                GROG_BASE to GROG_SPAWN,
                WILL_BOAT to WILL_DECK,
            )
    }
}
