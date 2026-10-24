package org.rsmod.content.quest.area.draynor.vampyreslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

private var Player.morganThanked by intVarBit("varbit.morgan_postquest_dialogue")
private var Player.harlowGivenBeer by intVarBit("varbit.harlow_given_beer")
private var Player.harlowGivenStake by intVarBit("varbit.harlow_given_stake")

class VampyreSlayerQuest
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val playerList: PlayerList,
    private val npcDeath: NpcDeath,
    private val aiPlayerInteractions: AiPlayerInteractions,
) :
    QuestScript(
        "quest_vampyreslayer",
        "varp.vampire",
        rewards { xp("stat.attack", ATTACK_XP) },
        ItemRewardDisplay(STAKE, zoom = 250),
        questVarbit = "varbit.vampyre_slayer_progress",
    ) {

    private val countsByPlayer = hashMapOf<PlayerUid, Npc>()
    private val huntersByCount = hashMapOf<Npc, PlayerUid>()

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Vampyre Slayer end state is ${quest.maxSteps} in the cache dbrow, " +
                "but staking the Count writes $STAGE_COMPLETE."
        }

        onOpNpc1("npc.morgan") { startDialogue(it.npc) { morgan() } }
        onOpNpc1(HARLOW) { startDialogue(it.npc) { harlow() } }
        onOpNpcU(HARLOW) {
            if (it.objType.isType(BEER) && wantsBeer(player)) {
                startDialogue(it.npc) { giveBeer() }
            } else {
                mes("Nothing interesting happens.")
            }
        }
        onOpLoc1("loc.garliccupboardshut") { openCupboard(it.loc) }
        onOpLoc1("loc.garliccupboardopen") { searchCupboard() }
        onOpLoc2("loc.garliccupboardopen") { closeCupboard(it.loc) }
        onOpLoc1(COFFIN) { openCoffin(it.loc) }
        val countType =
            ServerCacheManager.getNpc(COUNT.asRSCM(RSCMType.NPC)) ?: error("Missing $COUNT")
        onNpcQueue(countType, "queue.death") { countDefeated() }
        onPlayerQueue(STAKE_QUEUE) { stakeCount() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Morgan<col=000080> who can be found in " +
            "<col=800000>Draynor Village<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            line(
                "I spoke to <red>Morgan</red> at his home in <red>Draynor Village</red>. He " +
                    "told me about <red>Count Draynor</red>, a vampyre living in " +
                    "<red>Draynor Manor</red> north of the village. Morgan suggested I begin by " +
                    "seeking out <red>Dr Harlow</red>, a retired vampyre hunter, at the " +
                    "<red>Blue Moon Inn</red> in <red>Varrock</red>."
            )
            if (stage(access.player) < STAGE_MET_HARLOW) return@questJournal
            if (access.player.harlowGivenStake == 0) {
                line(
                    "I found Dr Harlow, but he doesn't seem willing to talk to me unless I buy " +
                        "him a <red>beer</red> first."
                )
                return@questJournal
            }
            line(
                "Dr Harlow gave me a <red>stake</red> to stop the vampyre regenerating. I need " +
                    "to find <red>Count Draynor</red> in the basement of Draynor Manor and kill " +
                    "him, with the stake and a <red>hammer</red> in my inventory. Some " +
                    "<red>garlic</red> should weaken him. Morgan might have some."
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Dr Harlow gave me a stake, and with it and a hammer I killed Count Draynor in " +
                    "the basement of Draynor Manor."
            )
        }

    private fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun wantsBeer(player: Player): Boolean =
        stage(player) == STAGE_MET_HARLOW && player.harlowGivenBeer == 0

    private suspend fun Dialogue.morgan() {
        when {
            quest.isQuestCompleted(player) -> morganThanks()
            quest.isQuestInProgress(player) -> morganProgress()
            else -> morganStart()
        }
    }

    private suspend fun Dialogue.morganStart() {
        chatNpc(shocked, "Could it be? A bold adventurer! Please, you must help us!")
        chatPlayer(quiz, "What is it? What's the problem?")
        chatNpc(
            shocked,
            "It's the evil vampyre, Count Draynor! For too long he's terrorised us from his " +
                "manor to the north. Someone finally needs to put a stop to him once and for all!",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(
                worried,
                "An evil vampyre? I don't think this is the job for me. It sounds far too scary!",
            )
            chatNpc(
                sad,
                "I understand... Hopefully someone else will come along to finally save us from " +
                    "this evil.",
            )
            return
        }
        chatPlayer(neutral, "Sounds like a job for me. Where should I start?")
        quest.setQuestStage(access, STAGE_STARTED)
        chatNpc(
            happy,
            "Oh, thank goodness! I've been hoping this day would come for a long time, so I've " +
                "made sure to do my research. I've heard of a retired vampyre hunter called Dr " +
                "Harlow who lives in Varrock.",
        )
        chatNpc(
            happy,
            "If you speak to him, I'm sure he'll be able to help. I hear he's a bit of an old " +
                "soak these days, so he spends most of his time in the Blue Moon Inn.",
        )
        morganOptions("What else can you tell me about this vampyre?")
    }

    private suspend fun Dialogue.morganProgress() {
        chatNpc(quiz, "Adventurer! Has Count Draynor been dealt with yet?")
        if (player.harlowGivenStake == 1) {
            chatPlayer(
                neutral,
                "I'm still working on it, but I could use some garlic to help me out.",
            )
            chatNpc(
                happy,
                "Ah, yes! I heard vampyres didn't like garlic. I keep some in the cupboard " +
                    "upstairs, just in case Count Draynor comes and tries anything!",
            )
            val lore =
                menu(
                    "What else can you tell me about Count Draynor?" to true,
                    "Perfect, thanks." to false,
                )
            if (lore) {
                morganLore("What else can you tell me about Count Draynor?")
            } else {
                chatPlayer(happy, "Perfect, thanks.")
                chatNpc(happy, "Good luck in Draynor Manor, adventurer.")
            }
            return
        }
        chatPlayer(neutral, "I'm still working on it.")
        chatNpc(worried, "Please hurry! I'm sure Dr Harlow in Varrock's Blue Moon Inn can help.")
        morganOptions("What else can you tell me about Count Draynor?")
    }

    private suspend fun Dialogue.morganOptions(loreOption: String) {
        if (menu(loreOption to true, "Alright, I'll see about paying him a visit." to false)) {
            morganLore(loreOption)
        } else {
            chatPlayer(neutral, "Alright, I'll see about paying him a visit.")
            chatNpc(happy, "Thank you, brave adventurer.")
        }
    }

    private suspend fun Dialogue.morganLore(question: String) {
        chatPlayer(quiz, question)
        chatNpc(happy, "All sorts of things! I've studied the legends extensively!")
        chatPlayer(confused, "Wait... legends? What do you mean legends?")
        chatNpc(
            neutral,
            "The legends of Count Draynor! It's said that long ago, he travelled here from dark " +
                "lands in the east. He built Draynor Manor, and from there he began his campaign " +
                "of darkness against our peaceful lands!",
        )
        chatPlayer(
            quiz,
            "Long ago? If this has been going on for so long, why has no one tried to stop him?",
        )
        chatNpc(
            sad,
            "Many have tried. All have failed. Vampyres are not killed easily. Only those with " +
                "the right skills and equipment have any hope of slaying one.",
        )
        chatPlayer(quiz, "Like this Dr Harlow? How come he's never tried to kill this Count Draynor?")
        chatNpc(confused, "I'm... not sure. Perhaps he was always too busy killing other vampyres?")
        chatPlayer(
            shifty,
            "Hmm... Something seems amiss here. Are you sure you're telling me everything?",
        )
        chatNpc(
            worried,
            "Well... it is true that some claim Count Draynor hasn't left his manor for " +
                "generations now.",
        )
        chatPlayer(quiz, "So he's not actually attacked the village?")
        chatNpc(worried, "Er... no. At least, not for a long time! He used to though!")
        chatPlayer(quiz, "When?")
        chatNpc(worried, "Well... not since I've moved here at least.")
        chatPlayer(
            quiz,
            "So if there's no actual danger, why are you so keen for someone to kill this " +
                "vampyre?",
        )
        chatNpc(
            angry,
            "Because what if he does start attacking again? Are we just meant to live our lives " +
                "constantly fearing the future?",
        )
        chatPlayer(neutral, "It doesn't really sound like anyone is living in fear apart from you.")
        chatNpc(
            angry,
            "More fool them! Count Draynor has cast a shadow over Draynor Village for " +
                "generations. They even named the place after him for Saradomin's sake!",
        )
        chatNpc(
            angry,
            "Even if the people here don't realise it, we will never be at peace until we know " +
                "he's dead!",
        )
        chatPlayer(neutral, "If you're sure... I guess I'll go and see this Dr Harlow.")
        chatNpc(happy, "Thank you, brave adventurer.")
    }

    private suspend fun Dialogue.morganThanks() {
        if (player.morganThanked == 0) {
            chatPlayer(happy, "I have some good news. Count Draynor is no more!")
            player.morganThanked = 1
            chatNpc(
                happy,
                "He's really gone? Finally, we can live without fear! Thank you, thank you! " +
                    "You're a true hero!",
            )
            chatPlayer(happy, "I'm happy to have helped.")
            return
        }
        chatNpc(happy, "Once again, thank you for slaying that vampyre! You will always be a hero!")
        chatPlayer(happy, "Don't mention it.")
    }

    private suspend fun Dialogue.harlow() {
        when {
            stage(player) == STAGE_STARTED -> harlowFirstMeeting()
            wantsBeer(player) -> harlowWantsBeer()
            stage(player) == STAGE_MET_HARLOW -> harlowReturn()
            else -> {
                chatNpc(drunk, "Buy me a drink pleassh...")
                chatPlayer(neutral, "I think you've had enough.")
            }
        }
    }

    private suspend fun Dialogue.harlowFirstMeeting() {
        chatNpc(drunk, "Buy me a drink pleassh...")
        val help =
            menu(
                "I need your help dealing with a vampyre." to true,
                "I think you've had enough." to false,
            )
        if (!help) {
            chatPlayer(neutral, "I think you've had enough.")
            return
        }
        chatPlayer(neutral, "I need your help dealing with a vampyre.")
        chatNpc(drunk, "A vampyre you shhay...?")
        chatPlayer(neutral, "Not just any vampyre. Count Draynor.")
        chatNpc(drunk, "Draynor? Well, buy me a beer firsht...")
        chatPlayer(confused, "Are you sure you've not had enough?")
        quest.setQuestStage(access, STAGE_MET_HARLOW)
        chatNpc(drunk, "Huh? No, I don't think ssho. Now, buy ush a beer.")
        if (player.inv.count(BEER) == 0) return
        if (menu("Alright, here you go." to true, "No. I think you've had enough." to false)) {
            chatPlayer(neutral, "Alright, here you go.")
            giveBeer()
        } else {
            chatPlayer(neutral, "No. I think you've had enough.")
            chatNpc(drunk, "No help for yoush then.")
        }
    }

    private suspend fun Dialogue.harlowWantsBeer() {
        chatNpc(drunk, "Have yoush got me a beer?")
        val hasBeer = player.inv.count(BEER) > 0
        val give =
            menu(
                (if (hasBeer) "Yes, here you go." else "I'll go and get you one.") to hasBeer,
                "No. I think you've had enough." to null,
            )
        when (give) {
            true -> {
                chatPlayer(neutral, "Yes, here you go.")
                giveBeer()
            }
            false -> {
                chatPlayer(neutral, "I'll go and get you one.")
                chatNpc(drunk, "Cheersh, matey...")
            }
            null -> {
                chatPlayer(neutral, "No. I think you've had enough.")
                chatNpc(drunk, "No help for yoush then.")
            }
        }
    }

    private suspend fun Dialogue.giveBeer() {
        if (!swapBeerForStake(access)) {
            chatNpc(drunk, "Yoush need shome room in your pack for what I'm giving you...")
            return
        }
        player.harlowGivenBeer = 1
        player.harlowGivenStake = 1
        objbox(BEER, "You give a beer to Dr Harlow.")
        chatNpc(drunk, "Cheersh, matey...")
        chatPlayer(quiz, "Now, about Count Draynor...")
        chatNpc(
            drunk,
            "Yesh, Count Draynor! The evil nashty vampyre that no one's ever sheen! Every nowsh " +
                "and then, some adventurer... theysh go and try to kill him, but none of 'em ever " +
                "comesh back.",
        )
        chatNpc(
            drunk,
            "You want to havesh a go? Then don't be likesh them! Be prepared! Vampyres... " +
                "Theysh hard to kill. Some... maybe imposhible.",
        )
        chatPlayer(quiz, "So how do I make sure I'm prepared?")
        chatNpc(
            drunk,
            "Most vampyres regenerate. Yoush need to stop them. I knowsh a few ways, but the " +
                "easiest ish a stake. Here, You can havesh thish one.",
        )
        objbox(STAKE, "Dr Harlow hands you a stake.")
        harlowInstructions(first = true)
        harlowQuestions(returning = false)
    }

    private suspend fun Dialogue.harlowInstructions(first: Boolean) {
        val opening = if (first) "Takesh that to Draynor Manor." else "Go to Draynor Manor."
        val stake = if (first) "the stake" else "that stake I gave you"
        chatNpc(
            drunk,
            "$opening Find the vampyre inshide and show him what for! When hesh weak, use a " +
                "hammer to drive $stake in! Mosht general stores have them.",
        )
        chatNpc(
            drunk,
            "Oh, and yoush should take some garlic with you as well. Vampyres don't likesh garlic.",
        )
        chatPlayer(quiz, "Garlic? Hmm... I'll see if Morgan knows where I can get some.")
    }

    private suspend fun Dialogue.harlowReturn() {
        chatNpc(drunk, "Yoush back! Killed that vampyre yet?")
        if (!access.playerContainsObj(STAKE)) {
            chatPlayer(sad, "No. I lost that stake you gave me.")
            chatNpc(drunk, "Oh, hangsh on then...")
            if (access.invAdd(access.inv, STAKE).failure) {
                chatNpc(drunk, "Yoush need shome room in your pack firsht.")
                return
            }
            objbox(STAKE, "Dr Harlow hands you a stake.")
            chatNpc(drunk, "Good job I've gotsh some spares.")
            harlowInstructions(first = true)
        }
        harlowQuestions(returning = true)
    }

    private suspend fun Dialogue.harlowQuestions(returning: Boolean) {
        var askedOnce = returning
        val leave = if (returning) "Not yet. I'd best get going." else "I'd best get going."
        while (true) {
            val options = buildList {
                if (askedOnce) add("What do I need to do again?" to HarlowTopic.Again)
                add("You said no one's ever seen Count Draynor?" to HarlowTopic.NeverSeen)
                add("So you were once a proper vampyre hunter?" to HarlowTopic.Hunter)
                add(leave to HarlowTopic.Leave)
            }
            when (menu(options)) {
                HarlowTopic.Again -> {
                    chatPlayer(quiz, "What do I need to do again?")
                    harlowInstructions(first = false)
                }
                HarlowTopic.NeverSeen -> neverSeen()
                HarlowTopic.Hunter -> properHunter()
                HarlowTopic.Leave -> {
                    chatPlayer(neutral, leave)
                    chatNpc(drunk, "Good luck, matey...")
                    return
                }
            }
            askedOnce = true
        }
    }

    private suspend fun Dialogue.neverSeen() {
        chatPlayer(quiz, "You said no one's ever seen Count Draynor?")
        chatNpc(drunk, "No, butsh every now and then someonesh tried to kill him.")
        chatPlayer(
            quiz,
            "But they've never come back? Meaning someone or something killed them most likely. " +
                "Presumably Count Draynor?",
        )
        chatNpc(drunk, "Yesh, I'd say so.")
        chatPlayer(quiz, "So why does he never leave his manor?")
        chatNpc(drunk, "Who knowsh. Most likely he's grown too weak to do ssho.")
        chatPlayer(quiz, "Weak? So he shouldn't be too hard to kill then?")
        chatNpc(
            drunk,
            "Even a weak vampyre ish very dangerous. Just go ashk those other adventurers thatsh " +
                "never came back.",
        )
    }

    private suspend fun Dialogue.properHunter() {
        chatPlayer(quiz, "So you were once a proper vampyre hunter?")
        chatNpc(
            drunk,
            "Yesh! I travelled their homeland to the easht. Morytania! I killed lotsh of vampyres " +
                "over there!",
        )
        chatPlayer(quiz, "What was the most dangerous vampyre you killed?")
        chatNpc(drunk, "Dangerous? Oh, well... Ish never killed one of the really dangerous ones.")
        chatPlayer(quiz, "Why not?")
        chatNpc(drunk, "Can't. Theysh too strong. Nothing cansh hurt them.")
        chatPlayer(worried, "Oh... But what if Count Draynor's like that?")
        chatNpc(
            drunk,
            "Yoush don't get many vampyres around here. Pretty much none, actshually. There's " +
                "speshial magic that stops them coming over here. Any that are here... theysh " +
                "weak... and trapped.",
        )
        chatPlayer(quiz, "So how come you've never tried to kill him yourself?")
        chatNpc(
            drunk,
            "Why bother? Hesh not killed anyone in a long time, so no one really caresh about " +
                "him that much, apart from the odd adventurer thatsh looking to prove themshelves.",
        )
        chatPlayer(neutral, "I see. Fair enough.")
    }

    private suspend fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim("seq.human_openchest")
        soundSynth("synth.cupboard_open")
        locRepo.change(cupboard, "loc.garliccupboardopen", CUPBOARD_OPEN_TICKS)
        mes("You open the cupboard.")
    }

    private suspend fun ProtectedAccess.closeCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim("seq.human_closechest")
        locRepo.change(cupboard, "loc.garliccupboardshut", CUPBOARD_OPEN_TICKS)
        mes("You close the cupboard.")
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        if (invAdd(inv, GARLIC).failure) {
            mes("The cupboard contains garlic, but you don't have room to take any.")
            return
        }
        mes("The cupboard contains garlic. You take a clove.")
    }

    private suspend fun ProtectedAccess.openCoffin(coffin: BoundLocInfo) {
        arriveDelay()
        if (!quest.isQuestInProgress(player)) {
            mes(
                if (quest.isQuestCompleted(player)) "The coffin is empty."
                else "The coffin is sealed shut."
            )
            return
        }
        val existing = countsByPlayer[player.uid]
        if (existing != null && existing.isSlotAssigned && existing.hitpoints > 0) {
            mes("You should probably deal with the vampyre first!")
            return
        }
        faceSquare(coffin.coords)
        locRepo.change(coffin, "loc.vampcoffin_animated", COFFIN_RISE_TICKS)
        val rising = Npc("npc.count_draynor_coffin", coffin.coords)
        npcRepo.add(rising, COFFIN_RISE_TICKS)
        rising.anim("seq.vampireslayer_arise")
        delay(COFFIN_RISE_TICKS)
        val count = Npc(COUNT, COUNT_SPAWN)
        npcRepo.add(count, COUNT_LIFESPAN)
        countsByPlayer[player.uid] = count
        huntersByCount[count] = player.uid
        if (inv.count(GARLIC) > 0) {
            mes("The vampyre seems to weaken from the smell of your garlic.")
            count.attackLvl = count.attackLvl * GARLIC_WEAKEN_PERCENT / 100
            count.strengthLvl = count.strengthLvl * GARLIC_WEAKEN_PERCENT / 100
            count.defenceLvl = count.defenceLvl * GARLIC_WEAKEN_PERCENT / 100
        }
        count.opPlayer2(player, aiPlayerInteractions)
    }

    private suspend fun StandardNpcAccess.countDefeated() {
        val hunter = huntersByCount[npc]?.resolve(playerList)
        val armed =
            hunter != null &&
                quest.isQuestInProgress(hunter) &&
                hunter.inv.count(STAKE) > 0 &&
                hunter.inv.count(HAMMER) > 0
        if (hunter == null || !armed) {
            if (hunter != null && hunter.inv.count(STAKE) > 0) {
                hunter.mes("You need a hammer to drive the stake in.")
            }
            hunter?.mes("The vampyre seems to regenerate!")
            npc.heal(npc.baseHitpointsLvl)
            return
        }
        hunter.strongQueue(STAKE_QUEUE, 1)
        huntersByCount.remove(npc)
        countsByPlayer.remove(hunter.uid)
        npcDeath.deathNoDrops(this)
    }

    private fun ProtectedAccess.stakeCount() {
        if (!quest.isQuestInProgress(player) || inv.count(STAKE) == 0) {
            return
        }
        anim("seq.human_stake")
        mes("You hammer the stake into the vampyre's chest!")
        if (invDel(inv, STAKE).failure) {
            return
        }
        quest.completeQuest(this)
    }

    private enum class HarlowTopic {
        Again,
        NeverSeen,
        Hunter,
        Leave,
    }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_MET_HARLOW = 2
        const val STAGE_COMPLETE = 3

        const val BEER = "obj.beer"
        const val STAKE = "obj.stake"

        private const val HAMMER = "obj.hammer"
        private const val GARLIC = "obj.garlic"
        private const val HARLOW = "npc.dr_harlow"
        private const val COUNT = "npc.count_draynor"
        private const val COFFIN = "loc.vampcoffin"
        private const val STAKE_QUEUE = "queue.vampyre_slayer_stake"

        private const val ATTACK_XP = 4825.0
        private const val GARLIC_WEAKEN_PERCENT = 80
        private const val COFFIN_RISE_TICKS = 4
        private const val COUNT_LIFESPAN = 500
        private const val CUPBOARD_OPEN_TICKS = 300

        private val COUNT_SPAWN = CoordGrid(3078, 9774, 0)

        /** Takes one beer and hands over the stake in one transaction, or changes nothing. */
        fun swapBeerForStake(access: ProtectedAccess): Boolean =
            access.player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                delete {
                    from = inventory
                    obj = BEER.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = STAKE.asRSCM()
                    strictCount = 1
                }
            }.success
    }
}
