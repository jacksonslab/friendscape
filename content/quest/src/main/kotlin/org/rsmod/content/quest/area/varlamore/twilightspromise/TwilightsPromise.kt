package org.rsmod.content.quest.area.varlamore.twilightspromise

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.varlamore.twilightspromise.TpItems.giveFeed
import org.rsmod.content.quest.area.varlamore.twilightspromise.TpItems.giveOnce
import org.rsmod.content.quest.area.varlamore.twilightspromise.TpItems.takeOne
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.content.quest.util.QuestDoors
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class TwilightsPromise
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val players: PlayerList,
    private val doors: QuestDoors,
) :
    QuestScript(
        "quest_twilightspromise",
        "varp.vmq2_primary",
        rewards {
            xp("stat.thieving", THIEVING_XP)
            extra("Civitas illa Fortis Teleport")
            extra("Access to the Quetzal Transport System")
        },
        ItemRewardDisplay(TpObj.FEED),
        questVarbit = TpVar.STAGE,
    ) {

    private val mezanByPlayer = hashMapOf<PlayerUid, Npc>()
    private val playerByMezan = hashMapOf<Npc, PlayerUid>()
    private val mezanGuard = hashMapOf<Npc, HitType>()
    private val mezanStreak = hashMapOf<Npc, Int>()
    private val cultistsByPlayer = hashMapOf<PlayerUid, MutableSet<Npc>>()
    private val playerByCultist = hashMapOf<Npc, PlayerUid>()
    private val azaliByPlayer = hashMapOf<PlayerUid, Npc>()

    override fun ScriptContext.init() {
        check(quest.maxSteps == TpStage.COMPLETE) {
            "Twilight's Promise end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the script completes at ${TpStage.COMPLETE}."
        }

        onOpNpc1(TpNpc.ENNIUS) { startDialogue(it.npc) { twins() } }
        onOpNpc1(TpNpc.FURIA) { startDialogue(it.npc) { twins() } }
        onOpNpc1(TpNpc.METZLI) { startDialogue(it.npc) { metzliConversation() } }
        onOpNpc1(TpNpc.ITZLA) { startDialogue(it.npc) { itzlaConversation() } }
        onOpNpc1(TpNpc.SERVIUS) { startDialogue(it.npc) { itzlaConversation() } }
        onOpNpc1(TpNpc.CITIZEN) {
            startDialogue(it.npc) { chatNpc(neutral, "Please, I'm busy. Leave me be.") }
        }
        onOpNpc3(TpNpc.CITIZEN) { pickpocketCitizen(it.npc) }
        onOpNpc1(TpNpc.ARRUN) { startDialogue(it.npc) { bazaarKnights(TpNpc.ARRUN) } }
        onOpNpc1(TpNpc.CLAUDIA) { startDialogue(it.npc) { bazaarKnights(TpNpc.CLAUDIA) } }
        onOpNpc1(TpNpc.NEL) { startDialogue(it.npc) { nel() } }
        onOpNpc1(TpNpc.VELAM) { startDialogue(it.npc) { velam() } }
        onOpNpc1(TpNpc.AZALI_DRUNK) { startDialogue(it.npc) { drunkAzali() } }
        onOpNpc1(TpNpc.AZALI) { startDialogue(it.npc) { soberAzali() } }
        onOpNpc1(TpNpc.MEZAN) { startDialogue(it.npc) { mezan() } }
        onOpLoc1(TpLoc.COTHON_CRATE) { searchCrate(it.loc.coords) }
        onOpLoc1(TpLoc.COTHON_CRATES) { searchCrate(it.loc.coords) }
        onOpLoc1(TpLoc.LETTER_CHEST) { searchLetterChest() }
        for (chest in TpLoc.OTHER_CHESTS) {
            onOpLoc1(chest) {
                arriveDelay()
                mes("You search the chest but find nothing of interest.")
            }
        }
        onOpHeld1(TpObj.LETTER) { readLetter() }
        onOpHeld1(TpObj.INVITATION) { readInvitation() }
        onOpLoc1(TpLoc.TEMPLE_STAIRS_TOP) { descendTemple(it.loc.coords) }
        onOpLoc1(TpLoc.TEMPLE_STAIRS_BOTTOM) { ascendTemple(it.loc.coords) }
        onOpLoc1(TpLoc.TEMPLE_GATE) { crossTempleGate(it.loc) }

        onOpNpc1(TpNpc.REGULUS_VARROCK) { startDialogue(it.npc) { regulusVarrock() } }
        onOpNpc1(TpNpc.REGULUS_VARROCK_TRAVEL) { startDialogue(it.npc) { regulusVarrock() } }
        onOpNpc3(TpNpc.REGULUS_VARROCK_TRAVEL) { flyToFortis() }
        onOpNpc1(TpNpc.PRIMIO_VARROCK) { flyToFortis() }
        onOpNpc1(TpNpc.REGULUS_FORTIS) { startDialogue(it.npc) { regulusFortis() } }
        onOpNpc3(TpNpc.REGULUS_FORTIS) { flyToVarrock() }
        onOpNpc1(TpNpc.PRIMIO_FORTIS) { flyToVarrock() }
        val feed = checkNotNull(ServerCacheManager.getItem(TpObj.FEED.asRSCM(RSCMType.OBJ)))
        for (renu in TpNpc.RENU) {
            onOpNpcU(npcType(renu), feed) { feedRenu() }
        }

        val mezan = npcType(TpNpc.MEZAN_COMBAT)
        onModifyNpcHit(mezan) {
            val attacker = hit.sourceUid?.let { PlayerUid(it).resolve(players) }
            if (attacker == null || playerByMezan[npc] != attacker.uid) {
                hit.damage = 0
                return@onModifyNpcHit
            }
            if (hit.type == HitType.Typeless) {
                return@onModifyNpcHit
            }
            val streak = if (mezanGuard[npc] == hit.type) (mezanStreak[npc] ?: 0) + 1 else 1
            mezanGuard[npc] = hit.type
            mezanStreak[npc] = streak
            if (streak >= MEZAN_PRAYER_STREAK) {
                if (streak == MEZAN_PRAYER_STREAK) {
                    attacker.mes("The knight raises his guard against your style of attack!")
                }
                hit.damage = 0
            }
        }
        onNpcQueue(mezan, "queue.death") { mezanDefeated(npc) }

        for (cultist in TpNpc.CULTISTS.distinct()) {
            val type = npcType(cultist)
            onModifyNpcHit(type) {
                val attacker = hit.sourceUid?.let { PlayerUid(it).resolve(players) }
                val owner = playerByCultist[npc]
                if (hit.isFromPlayer && (attacker == null || owner != attacker.uid)) {
                    hit.damage = 0
                }
            }
            onNpcQueue(type, "queue.death") { cultistDefeated(npc) }
        }

        onPlayerCoordsChanged { leadAzali(player) }
        onPlayerLogin { sync(player) }
        onPlayerLogout { clearSpawns(player.uid) }
    }

    override fun subTitle(): String =
        "speaking to <col=800000>Ennius</col> or <col=800000>Furia Tullus</col> in front of the " +
            "<col=800000>Sunrise Palace</col> in <col=800000>Civitas illa Fortis</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "Someone in <red>Varlamore</red> wants <red>Servius, Teokan of Ralos</red>, " +
                    "dead. <red>Prince Itzla</red> has asked for my help finding out who."
            )
            for (step in JOURNAL_STEPS) {
                objective(step.text) {
                    visibleWhen { stage(access.player) in step.from..step.until }
                }
            }
            objective(knightsStatus(access.player)) {
                visibleWhen { stage(access.player) == TpStage.FIND_KNIGHTS }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Prince Itzla told me someone in the Varrock delegation had plotted to kill his " +
                    "father, Servius. I rounded up the six Knights of Varlamore for the Tullus " +
                    "twins."
            )
            line(
                "A letter in Velam's chest named him as the traitor, and Ennius Tullus executed " +
                    "him on the spot before he could be questioned."
            )
            line(
                "Regulus Cento gave me my own quetzal, Renu. At the Teomat, Twilight Emissary " +
                    "cultists attacked, Servius was badly wounded and Metzli vanished."
            )
        }

    private fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun ProtectedAccess.setStage(stage: Int) {
        quest.setQuestStage(this, stage)
        sync(player)
    }

    private fun set(player: Player, varbit: String, value: Int) {
        if (player.vars[varbit] != value) {
            VarPlayerIntMapSetter.set(player, varbit, value)
        }
    }

    internal fun sync(player: Player) {
        val stage = stage(player)
        if (player.vars[TpVar.FIRST_TRAVEL] == 0 &&
            QuestRequirements.hasCompleted(player, CHILDREN_OF_THE_SUN)
        ) {
            set(player, TpVar.FIRST_TRAVEL, TpVar.FIRST_TRAVEL_UNSEEN)
        }
        val pubDone = player.vars[TpVar.PUB_KNIGHTS] == TpVar.KNIGHT_RETURNED
        val drunk = player.vars[TpVar.DRUNK_KNIGHT]
        when {
            stage != TpStage.FIND_KNIGHTS || pubDone -> set(player, TpVar.DRUNK_KNIGHT, 0)
            drunk == TpVar.DRUNK_HIDDEN -> set(player, TpVar.DRUNK_KNIGHT, TpVar.DRUNK_AT_PUB)
            drunk == TpVar.DRUNK_FOLLOWING && azaliByPlayer[player.uid] == null ->
                set(player, TpVar.DRUNK_KNIGHT, TpVar.DRUNK_AT_PUB)
        }
        set(player, TpVar.TEOMAT_ITZLA, if (stage >= TpStage.FLY_TO_TEOMAT) 1 else 0)
    }

    private fun knightsStatus(player: Player): String {
        val left = mutableListOf<String>()
        if (player.vars[TpVar.BAZAAR_KNIGHTS] != TpVar.KNIGHT_RETURNED) {
            left += "<red>Arrun</red> and <red>Claudia</red> in the <red>bazaar</red>"
        }
        if (player.vars[TpVar.COTHON_KNIGHT] != TpVar.KNIGHT_RETURNED) {
            left += "<red>Nel</red> at the <red>Fortis Cothon</red>"
        }
        if (player.vars[TpVar.COLOSSEUM_KNIGHT] != TpVar.KNIGHT_RETURNED) {
            left += "<red>Mezan</red> in the <red>Fortis Colosseum</red>"
        }
        if (player.vars[TpVar.PUB_KNIGHTS] != TpVar.KNIGHT_RETURNED) {
            left += "<red>Velam</red> and <red>Azali</red> at the pub south of the city walls"
        }
        return "Knights still to find: ${left.joinToString(", ")}."
    }

    private fun allKnightsReturned(player: Player): Boolean =
        listOf(TpVar.BAZAAR_KNIGHTS, TpVar.COTHON_KNIGHT, TpVar.COLOSSEUM_KNIGHT, TpVar.PUB_KNIGHTS)
            .all { player.vars[it] == TpVar.KNIGHT_RETURNED }

    private fun ProtectedAccess.knightReturned(varbit: String) {
        set(player, varbit, TpVar.KNIGHT_RETURNED)
        if (allKnightsReturned(player)) {
            setStage(TpStage.KNIGHTS_GATHERED)
            mes("That's all six knights. You should report back to the Tullus twins.")
        }
    }

    private suspend fun Dialogue.twins() {
        when (val stage = stage(player)) {
            TpStage.NOT_STARTED -> beforeQuest()
            TpStage.FIND_METZLI -> {
                furia(neutral, "The prince is waiting at the temple south of the bazaar.")
                ennius(neutral, "Ask the Teokan of Ranul there. She'll know where he is.")
            }
            TpStage.MEET_ITZLA ->
                ennius(neutral, "Prince Itzla is below the temple. Go on, don't keep him waiting.")
            TpStage.RETURN_TO_TWINS -> assignKnights()
            TpStage.FIND_KNIGHTS -> knightsReminder()
            TpStage.KNIGHTS_GATHERED -> {
                chatPlayer(happy, "All six knights are on their way back.")
                furia(
                    happy,
                    "Good. While they gather, their belongings are sitting unguarded in the " +
                        "Kualti Headquarters, just west of here. Their rooms are on the top floor.",
                )
                ennius(neutral, "Search their chests. If one of them is the traitor, we'll know.")
                access.setStage(TpStage.SEARCH_HEADQUARTERS)
            }
            TpStage.SEARCH_HEADQUARTERS ->
                furia(
                    neutral,
                    "The knights' chests are on the top floor of the Kualti Headquarters, west " +
                        "of the palace.",
                )
            TpStage.LETTER_FOUND -> judgement()
            TpStage.JUDGED -> sendToTeomat()
            else ->
                if (stage >= TpStage.COMPLETE) {
                    furia(neutral, "Thank you again for your service to the Sun Queen.")
                } else {
                    ennius(neutral, "The prince is at the Teomat. Regulus Cento can get you there.")
                }
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        if (!QuestRequirements.hasCompleted(player, CHILDREN_OF_THE_SUN)) {
            furia(neutral, "Can we help you? We're rather busy with palace business.")
            return
        }
        furia(happy, "It's you! The one who caught those bandits in Varrock.")
        ennius(
            neutral,
            "We organised that delegation. Prince Itzla has been asking after you ever since.",
        )
        furia(quiz, "He's at the temple south of the bazaar. Will you go and meet him?")
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "Not right now, sorry.")
            ennius(neutral, "Suit yourself.")
            return
        }
        chatPlayer(happy, "Of course. I'll head over there now.")
        furia(neutral, "Speak to the Teokan of Ranul when you arrive. She'll show you the way.")
        access.setStage(TpStage.FIND_METZLI)
    }

    private suspend fun Dialogue.assignKnights() {
        chatPlayer(
            neutral,
            "The prince thinks someone from the delegation tried to kill Servius. He suspects " +
                "one of the knights.",
        )
        ennius(angry, "The knights? They swore oaths to the Queen herself!")
        furia(
            neutral,
            "Then we bring them all back and find out. Here, take this crest. It proves you act " +
                "for the Sun Queen, and the knights won't listen to you without it.",
        )
        when (access.giveOnce(TpObj.CREST)) {
            GiveResult.NoSpace -> {
                furia(neutral, "You'll need a free space in your pack for the crest first.")
                return
            }
            GiveResult.Given -> objbox(TpObj.CREST, "Furia hands you a Varlamore crest.")
            GiveResult.AlreadyHas -> Unit
        }
        set(player, TpVar.GIVEN_CREST, 1)
        access.setStage(TpStage.FIND_KNIGHTS)
        ennius(
            neutral,
            "Arrun and Claudia are in the bazaar. Nel is down at the cothon, Mezan is training " +
                "in the colosseum, and Velam and Azali are drinking at the pub south of the walls.",
        )
        furia(neutral, "Send them all back here, then come and see us.")
    }

    private suspend fun Dialogue.knightsReminder() {
        if (player.vars[TpVar.GIVEN_CREST] == 1 && !playerContainsObj(TpObj.CREST)) {
            chatPlayer(sad, "I seem to have lost the crest.")
            if (access.giveOnce(TpObj.CREST) == GiveResult.NoSpace) {
                furia(angry, "Make some room and I'll give you another.")
                return
            }
            furia(angry, "Here's another. Please take more care with it.")
        }
        ennius(neutral, knightsStatus(player).replace(Regex("<[^>]+>"), ""))
    }

    private suspend fun Dialogue.judgement() {
        if (player.vars[TpVar.MESSAGE_READ] == 0) {
            furia(quiz, "You found something? Well, what does it say?")
            mesbox("You should read the letter before handing it over.")
            return
        }
        chatPlayer(neutral, "I found this letter in Velam's chest.")
        furia(shocked, "Twilight's Promise... the Final Dawn. Velam was ordered to kill Servius.")
        ennius(angry, "Bring the knights in. All of them.")
        access.fadeOverlay(0, FADE_CLEAR, 0, FADE_BLACK, FADE_CLIENT_CYCLES)
        delay(2)
        access.fadeOverlay(0, FADE_BLACK, 0, FADE_CLEAR, FADE_CLIENT_CYCLES)
        access.closeFadeOverlay(cycles = 2)
        mesbox("The six knights are summoned before the palace.")
        knight(TpNpc.VELAM, shocked, "That letter isn't mine! I've never seen it before!")
        ennius(angry, "Traitors to the Sun Queen get no trial.")
        mesbox("Before anyone can stop him, Ennius draws his blade and strikes Velam down.")
        chatPlayer(shocked, "What have you done? We could have questioned him!")
        ennius(neutral, "This is how we deal with traitors in Varlamore.")
        access.setStage(TpStage.JUDGED)
        sendToTeomat()
    }

    private suspend fun Dialogue.sendToTeomat() {
        furia(
            neutral,
            "The prince should hear of this. He's with Servius at the Teomat on top of " +
                "Ralos' Rise.",
        )
        ennius(
            neutral,
            "It's a long way on foot. Regulus Cento, next to the quetzals east of the palace, " +
                "can get you there quicker.",
        )
        if (stage(player) == TpStage.JUDGED) {
            access.setStage(TpStage.TALK_TO_REGULUS)
        }
    }

    private suspend fun Dialogue.metzliConversation() {
        when (stage(player)) {
            TpStage.FIND_METZLI -> metzliFortis()
            TpStage.MEET_ITZLA -> metzli(neutral, "The prince waits in the crypt below. Go.")
            TpStage.FLY_TO_TEOMAT ->
                metzli(neutral, "Speak with the prince first. He is by the shrine.")
            TpStage.TALK_TO_METZLI -> metzliTeomat()
            TpStage.CULTISTS -> startCultists()
            else -> metzli(neutral, "All things end in ash, outsider. Even this conversation.")
        }
    }

    private suspend fun Dialogue.metzliFortis() {
        metzli(
            neutral,
            "Every flame gutters out in the end, outsider. Why have you come to the Sect's door?",
        )
        while (true) {
            val choice =
                menu(
                    "I'm meant to be meeting Prince Itzla here." to 0,
                    "What is the Sect of Ranul?" to 1,
                    "What is the Church of Ralos?" to 2,
                )
            when (choice) {
                0 -> {
                    chatPlayer(neutral, "I'm meant to be meeting Prince Itzla here.")
                    metzli(
                        neutral,
                        "Then you will find him beneath us, in the crypt. Take the stairs down " +
                            "and go north through the gate.",
                    )
                    access.setStage(TpStage.MEET_ITZLA)
                    return
                }
                1 -> {
                    chatPlayer(quiz, "What is the Sect of Ranul?")
                    metzli(
                        neutral,
                        "We tend to the dead and to the moon. Ralos gives light, Ranul receives " +
                            "what it leaves behind.",
                    )
                }
                else -> {
                    chatPlayer(quiz, "What is the Church of Ralos?")
                    metzli(
                        neutral,
                        "The faith of the sun, led by its Teokan, Servius. Our two faiths share " +
                            "this temple, if not always agreement.",
                    )
                }
            }
        }
    }

    private suspend fun Dialogue.metzliTeomat() {
        chatPlayer(quiz, "Servius said you could tell me about the Final Dawn.")
        metzli(
            neutral,
            "Ximoua. Our scriptures say the world burns and is reborn from its ashes, over and " +
                "over, as it always has.",
        )
        metzli(
            neutral,
            "Most of us wait for it. The Tsak Tauli, believers in the Final Dawn, think it is " +
                "their duty to hasten it. The Twilight Emissaries are said to be among them.",
        )
        chatPlayer(quiz, "So they want the world to end?")
        metzli(neutral, "They want it to begin again. To them, that is the same thing.")
        mesbox("Shouting erupts outside the library. Robed figures pour in through the doors!")
        access.setStage(TpStage.CULTISTS)
        startCultists()
    }

    private suspend fun Dialogue.startCultists() {
        val living = cultistsByPlayer[player.uid]?.filter { it.isSlotAssigned }.orEmpty()
        if (living.isNotEmpty()) {
            mesbox("The cultists are still attacking!")
            return
        }
        clearCultists(player.uid)
        val spawned = mutableSetOf<Npc>()
        for ((index, type) in TpNpc.CULTISTS.withIndex()) {
            val cultist = Npc(type, TpCoords.TEOMAT_CULTIST_SPAWNS[index])
            npcRepo.add(cultist, SPAWN_LIFESPAN)
            spawned += cultist
            playerByCultist[cultist] = player.uid
        }
        cultistsByPlayer[player.uid] = spawned
        spawned.first().opPlayer2(player, aiPlayerInteractions)
        access.mes("Defeat the cultists!")
    }

    private fun cultistDefeated(npc: Npc) {
        val uid = playerByCultist.remove(npc)
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
        uid ?: return
        val remaining = cultistsByPlayer[uid] ?: return
        remaining.remove(npc)
        if (remaining.isNotEmpty()) {
            return
        }
        cultistsByPlayer.remove(uid)
        val player = uid.resolve(players) ?: return
        if (stage(player) != TpStage.CULTISTS) {
            return
        }
        VarPlayerIntMapSetter.set(player, TpVar.STAGE, TpStage.SERVIUS_INJURED)
        sync(player)
        player.mes(
            "The last cultist falls, but Servius has been badly hurt and Metzli is nowhere to be " +
                "seen. You should speak to Prince Itzla."
        )
    }

    private fun clearCultists(uid: PlayerUid) {
        val cultists = cultistsByPlayer.remove(uid) ?: return
        for (cultist in cultists) {
            playerByCultist.remove(cultist)
            if (cultist.isSlotAssigned) {
                npcRepo.del(cultist, Int.MAX_VALUE)
            }
        }
    }

    private suspend fun Dialogue.itzlaConversation() {
        when (stage(player)) {
            TpStage.MEET_ITZLA -> crypt()
            TpStage.RETURN_TO_TWINS ->
                itzla(neutral, "Speak with the Tullus twins at the palace. They know the knights.")
            TpStage.FLY_TO_TEOMAT -> teomatReport()
            TpStage.TALK_TO_METZLI ->
                servius(neutral, "Metzli is in the library to the north. Ask her about Ximoua.")
            TpStage.CULTISTS -> itzla(angry, "Cultists! Stay close and fight!")
            TpStage.SERVIUS_INJURED -> finale()
            else ->
                if (stage(player) >= TpStage.COMPLETE) {
                    itzla(
                        neutral,
                        "My father should recover, but the ones behind this are still out there. " +
                            "I'll call on you once my mother replies.",
                    )
                } else {
                    itzla(happy, "Good to see you again, friend.")
                }
        }
    }

    private suspend fun Dialogue.crypt() {
        val name = player.displayName
        itzla(happy, "$name! Kuaini, you came. Let me introduce my father, Servius.")
        servius(neutral, "The adventurer from Varrock. My son speaks well of you.")
        itzla(
            worried,
            "The bandits you caught in Varrock were paid by a Varlamorian to kill him. Whoever " +
                "it was must have been part of our delegation.",
        )
        chatPlayer(quiz, "Who was in the delegation?")
        itzla(
            neutral,
            "Myself, my father, the Tullus twins, and six Knights of Varlamore. I'd stake my " +
                "life on the twins, so that leaves the knights.",
        )
        servius(neutral, "I won't hide in a crypt. I'm returning to the Teomat.")
        itzla(
            neutral,
            "Then I'm coming with you. $name, find the twins at the palace. They can tell you " +
                "where the knights are.",
        )
        access.setStage(TpStage.RETURN_TO_TWINS)
    }

    private suspend fun Dialogue.teomatReport() {
        chatPlayer(
            worried,
            "Velam was the traitor. Ennius killed him before we could question him.",
        )
        itzla(sad, "A traitor among the knights... and silenced before he could talk.")
        chatPlayer(quiz, "Doesn't that seem convenient? Almost like a cover-up.")
        itzla(
            neutral,
            "It does. The Tullus family has history with Imafore. I'll keep a close eye on them.",
        )
        servius(neutral, "The twins have served faithfully. Don't judge them too quickly.")
        chatPlayer(quiz, "The letter mentioned Twilight's Promise and the Final Dawn.")
        itzla(
            neutral,
            "The Twilight Emissaries are a cult obsessed with the end of the world. The Final " +
                "Dawn is the belief they cling to.",
        )
        servius(neutral, "Metzli knows more of it than either of us. She's in the library.")
        access.setStage(TpStage.TALK_TO_METZLI)
    }

    private suspend fun Dialogue.finale() {
        itzla(sad, "My father is badly wounded. The guards are taking him to the capital.")
        chatPlayer(quiz, "And Metzli?")
        itzla(angry, "Gone. Vanished in the chaos. I don't like what that suggests.")
        itzla(
            neutral,
            "You fought well. Keep Renu close, I'll send word when I know my next move.",
        )
        quest.completeQuest(access)
        sync(player)
    }

    private suspend fun Dialogue.bazaarKnights(type: String) {
        when (player.vars[TpVar.BAZAAR_KNIGHTS]) {
            TpVar.KNIGHT_UNMET -> {
                if (!crestCheck(type)) return
                knight(
                    type,
                    neutral,
                    "We're after a band of thieves. We planted an amulet as bait and one of them " +
                        "took it. A citizen in pale blue robes has been loitering about.",
                )
                knight(type, neutral, "Get that amulet back and we'll return to the palace.")
                set(player, TpVar.BAZAAR_KNIGHTS, TpVar.KNIGHT_ASKED)
            }
            TpVar.KNIGHT_ASKED -> {
                if (player.inv.count(TpObj.AMULET) == 0) {
                    knight(type, neutral, "Have you found that amulet yet?")
                    return
                }
                if (!access.takeOne(TpObj.AMULET)) return
                chatPlayer(happy, "Here's your amulet.")
                knight(
                    type,
                    happy,
                    "Now we know who to follow. We'll head back to the palace once they're dealt " +
                        "with.",
                )
                access.knightReturned(TpVar.BAZAAR_KNIGHTS)
            }
            else -> knight(type, neutral, "We'll be back at the palace shortly.")
        }
    }

    private suspend fun ProtectedAccess.pickpocketCitizen(npc: Npc) {
        if (player.isFrozen) return
        faceEntitySquare(npc)
        if (stage(player) != TpStage.FIND_KNIGHTS ||
            player.vars[TpVar.BAZAAR_KNIGHTS] != TpVar.KNIGHT_ASKED ||
            playerContainsObj(TpObj.AMULET)
        ) {
            mes("You find nothing worth taking.")
            return
        }
        anim(PICKPOCKET_SEQ)
        delay(1)
        when (giveOnce(TpObj.AMULET)) {
            GiveResult.Given -> mes("You pick the citizen's pocket and find a stolen amulet.")
            GiveResult.NoSpace -> mes("You don't have enough inventory space.")
            GiveResult.AlreadyHas -> Unit
        }
    }

    private suspend fun Dialogue.nel() {
        when (player.vars[TpVar.COTHON_KNIGHT]) {
            TpVar.KNIGHT_UNMET -> {
                if (!crestCheck(TpNpc.NEL)) return
                knight(
                    TpNpc.NEL,
                    neutral,
                    "Smugglers are shipping weapons out of this harbour. One crate here belongs " +
                        "to them, and I need the name of the ship it's bound for.",
                )
                knight(
                    TpNpc.NEL,
                    neutral,
                    "It's a single crate, not stacked, between two barrels of fish and opposite " +
                        "a bench. Find it and I'll go back with you.",
                )
                set(player, TpVar.COTHON_KNIGHT, TpVar.KNIGHT_ASKED)
            }
            TpVar.KNIGHT_ASKED ->
                knight(TpNpc.NEL, neutral, "The crate is between two fish barrels, by a bench.")
            TpVar.KNIGHT_READY -> {
                chatPlayer(happy, "The crate is marked for a ship called the Fortis Spark.")
                knight(TpNpc.NEL, happy, "Then I know who to watch. I'll head back to the palace.")
                access.knightReturned(TpVar.COTHON_KNIGHT)
            }
            else -> knight(TpNpc.NEL, neutral, "I'll be at the palace soon.")
        }
    }

    private suspend fun ProtectedAccess.searchCrate(coords: CoordGrid) {
        arriveDelay()
        if (coords == TpCoords.SPARK_CRATE &&
            player.vars[TpVar.COTHON_KNIGHT] == TpVar.KNIGHT_ASKED
        ) {
            set(player, TpVar.COTHON_KNIGHT, TpVar.KNIGHT_READY)
            startDialogue { mesbox("The crate is full of weapons and stamped 'The Fortis Spark'.") }
            return
        }
        mes("You search the crate but find nothing of interest.")
    }

    private suspend fun Dialogue.mezan() {
        when (player.vars[TpVar.COLOSSEUM_KNIGHT]) {
            TpVar.KNIGHT_UNMET,
            TpVar.KNIGHT_ASKED -> {
                if (!crestCheck(TpNpc.MEZAN)) return
                knight(
                    TpNpc.MEZAN,
                    neutral,
                    "My sparring partner never showed. Beat me in a bout and I'll go wherever " +
                        "you like. Bring more than one way of fighting, I adapt quickly.",
                )
                set(player, TpVar.COLOSSEUM_KNIGHT, TpVar.KNIGHT_ASKED)
                if (!choice2("Let's fight.", true, "Not right now.", false)) {
                    return
                }
                startMezanFight()
            }
            TpVar.KNIGHT_READY -> {
                knight(TpNpc.MEZAN, happy, "A good bout! I'll head back to the palace.")
                access.knightReturned(TpVar.COLOSSEUM_KNIGHT)
            }
            else -> knight(TpNpc.MEZAN, neutral, "I'll see you at the palace.")
        }
    }

    private fun Dialogue.startMezanFight() {
        val existing = mezanByPlayer[player.uid]
        if (existing != null && existing.isSlotAssigned) {
            existing.opPlayer2(player, aiPlayerInteractions)
            return
        }
        clearMezan(player.uid)
        val knight = Npc(TpNpc.MEZAN_COMBAT, TpCoords.MEZAN_ARENA)
        npcRepo.add(knight, SPAWN_LIFESPAN)
        mezanByPlayer[player.uid] = knight
        playerByMezan[knight] = player.uid
        knight.opPlayer2(player, aiPlayerInteractions)
    }

    private fun mezanDefeated(npc: Npc) {
        val uid = playerByMezan.remove(npc)
        mezanGuard.remove(npc)
        mezanStreak.remove(npc)
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
        uid ?: return
        mezanByPlayer.remove(uid)
        val player = uid.resolve(players) ?: return
        if (player.vars[TpVar.COLOSSEUM_KNIGHT] == TpVar.KNIGHT_ASKED) {
            VarPlayerIntMapSetter.set(player, TpVar.COLOSSEUM_KNIGHT, TpVar.KNIGHT_READY)
            player.mes("The knight yields. You should talk to him.")
        }
    }

    private fun clearMezan(uid: PlayerUid) {
        val knight = mezanByPlayer.remove(uid) ?: return
        playerByMezan.remove(knight)
        mezanGuard.remove(knight)
        mezanStreak.remove(knight)
        if (knight.isSlotAssigned) {
            npcRepo.del(knight, Int.MAX_VALUE)
        }
    }

    private suspend fun Dialogue.velam() {
        when (player.vars[TpVar.PUB_KNIGHTS]) {
            TpVar.KNIGHT_UNMET -> {
                knight(TpNpc.VELAM, neutral, "It's our day off. Whatever it is, it can wait.")
                if (!crestCheck(TpNpc.VELAM)) return
                knight(
                    TpNpc.VELAM,
                    sad,
                    "The Queen's crest... fine. But Azali's had far too much. Get her sobered up " +
                        "first. The fountain east of here should do it.",
                )
                set(player, TpVar.PUB_KNIGHTS, TpVar.KNIGHT_ASKED)
            }
            TpVar.KNIGHT_RETURNED -> knight(TpNpc.VELAM, neutral, "We're heading back now.")
            else -> knight(TpNpc.VELAM, neutral, "Take Azali to the fountain east of here.")
        }
    }

    private suspend fun Dialogue.drunkAzali() {
        if (player.vars[TpVar.PUB_KNIGHTS] != TpVar.KNIGHT_ASKED) {
            knight(TpNpc.AZALI_DRUNK, happy, "Another round! For... for the Queen!")
            return
        }
        chatPlayer(neutral, "This way, Azali.")
        knight(TpNpc.AZALI_DRUNK, happy, "Lead on, my friend! Lead on!")
        startAzaliFollowing()
    }

    private fun Dialogue.startAzaliFollowing() {
        val existing = azaliByPlayer[player.uid]
        val azali =
            if (existing != null && existing.isSlotAssigned) {
                existing
            } else {
                Npc(TpNpc.AZALI_FOLLOWER, player.coords).also {
                    npcRepo.add(it, SPAWN_LIFESPAN)
                    azaliByPlayer[player.uid] = it
                }
            }
        set(player, TpVar.DRUNK_KNIGHT, TpVar.DRUNK_FOLLOWING)
        azali.mode = NpcMode.PlayerFollow
        azali.facePlayer(player)
    }

    private fun leadAzali(player: Player) {
        val azali = azaliByPlayer[player.uid] ?: return
        if (player.vars[TpVar.DRUNK_KNIGHT] != TpVar.DRUNK_FOLLOWING) return
        if (player.coords.level != 0 ||
            player.coords.chebyshevDistance(TpCoords.FOUNTAIN) > FOUNTAIN_RADIUS
        ) {
            return
        }
        clearAzali(player.uid)
        if (!azali.isSlotAssigned) return
        VarPlayerIntMapSetter.set(player, TpVar.DRUNK_KNIGHT, TpVar.DRUNK_AT_FOUNTAIN)
        player.mes("Azali plunges her head into the fountain and comes up spluttering.")
    }

    private fun clearAzali(uid: PlayerUid) {
        val azali = azaliByPlayer.remove(uid) ?: return
        if (azali.isSlotAssigned) {
            npcRepo.del(azali, Int.MAX_VALUE)
        }
    }

    private suspend fun Dialogue.soberAzali() {
        if (player.vars[TpVar.DRUNK_KNIGHT] != TpVar.DRUNK_AT_FOUNTAIN) {
            return
        }
        knight(TpNpc.AZALI, sad, "Ugh. My head. I think I needed that.")
        chatPlayer(neutral, "The Tullus twins want all the knights back at the palace.")
        knight(TpNpc.AZALI, neutral, "Then Velam and I will be on our way.")
        access.knightReturned(TpVar.PUB_KNIGHTS)
        sync(player)
    }

    private suspend fun Dialogue.crestCheck(type: String): Boolean {
        if (stage(player) != TpStage.FIND_KNIGHTS) {
            knight(type, neutral, "Greetings, outsider.")
            return false
        }
        if (player.inv.count(TpObj.CREST) > 0) {
            chatPlayer(neutral, "I'm acting for the Sun Queen. Here's her crest.")
            return true
        }
        knight(type, neutral, "Why should I listen to you? Show me some proof of who you act for.")
        return false
    }

    private suspend fun ProtectedAccess.searchLetterChest() {
        arriveDelay()
        if (stage(player) != TpStage.SEARCH_HEADQUARTERS || playerContainsObj(TpObj.LETTER)) {
            if (stage(player) == TpStage.LETTER_FOUND && !playerContainsObj(TpObj.LETTER)) {
                if (giveOnce(TpObj.LETTER) == GiveResult.Given) {
                    mes("You find another copy of the letter in the chest.")
                    return
                }
            }
            mes("You search the chest but find nothing of interest.")
            return
        }
        when (giveOnce(TpObj.LETTER)) {
            GiveResult.NoSpace -> mes("You find a letter, but you don't have room to take it.")
            else -> {
                setStage(TpStage.LETTER_FOUND)
                startDialogue { objbox(TpObj.LETTER, "You find an incriminating letter.") }
            }
        }
    }

    private suspend fun ProtectedAccess.readLetter() {
        if (stage(player) == TpStage.LETTER_FOUND) {
            set(player, TpVar.MESSAGE_READ, 1)
        }
        startDialogue {
            mesbox(
                "<col=000080>Velam - the Teokan must not see the next full sun. Arrange it " +
                    "before the delegation returns. Twilight's Promise will be kept. The Final " +
                    "Dawn comes."
            )
        }
    }

    private suspend fun ProtectedAccess.readInvitation() {
        startDialogue {
            mesbox(
                "<col=000080>You are invited to Civitas illa Fortis by Prince Itzla Arkan. " +
                    "Present yourself to Ennius and Furia Tullus at the Sunrise Palace."
            )
        }
    }

    private suspend fun ProtectedAccess.descendTemple(coords: CoordGrid) {
        arriveDelay()
        val dest =
            if (coords.z <= TEMPLE_STAIRS_SPLIT_Z) TpCoords.CRYPT_SOUTH else TpCoords.CRYPT_NORTH
        telejump(dest)
    }

    private suspend fun ProtectedAccess.ascendTemple(coords: CoordGrid) {
        arriveDelay()
        val dest =
            if (coords.z <= CRYPT_STAIRS_SPLIT_Z) {
                TpCoords.TEMPLE_TOP_SOUTH
            } else {
                TpCoords.TEMPLE_TOP_NORTH
            }
        telejump(dest)
    }

    private suspend fun ProtectedAccess.crossTempleGate(gate: BoundLocInfo) {
        arriveDelay()
        if (stage(player) < TpStage.MEET_ITZLA) {
            mes("The gate is locked.")
            return
        }
        doors.open(this, gate, TpLoc.TEMPLE_GATE_OPEN)
        val dest =
            if (player.coords.z < TpCoords.TEMPLE_GATE_NORTH.z) TpCoords.TEMPLE_GATE_NORTH
            else TpCoords.TEMPLE_GATE_SOUTH
        delay(1)
        telejump(dest)
    }

    private suspend fun Dialogue.regulusVarrock() {
        if (player.vars[TpVar.FIRST_TRAVEL] < TpVar.FIRST_TRAVEL_DONE) {
            regulusFirstFlight()
            return
        }
        chatNpcSpecific("Regulus Cento", TpNpc.REGULUS_VARROCK, neutral, "Back to Varlamore?")
        if (choice2("Yes, please.", true, "Not today.", false)) {
            access.flyToFortis()
        }
    }

    private suspend fun Dialogue.regulusFirstFlight() {
        val speaker = TpNpc.REGULUS_VARROCK
        chatNpcSpecific(
            "Regulus Cento",
            speaker,
            happy,
            "Ah, the adventurer from the palace! Prince Itzla asked me to bring you to Varlamore.",
        )
        set(player, TpVar.FIRST_TRAVEL, TpVar.FIRST_TRAVEL_PRIMIO)
        chatNpcSpecific(
            "Regulus Cento",
            speaker,
            neutral,
            "This is Primio, my quetzal. He'll get us to Civitas illa Fortis in no time.",
        )
        if (!playerContainsObj(TpObj.INVITATION) && stage(player) == TpStage.NOT_STARTED) {
            if (access.giveOnce(TpObj.INVITATION) == GiveResult.Given) {
                objbox(TpObj.INVITATION, "Regulus hands you an invitation.")
            }
        }
        if (!choice2("Let's go.", true, "Maybe later.", false)) {
            return
        }
        set(player, TpVar.FIRST_TRAVEL, TpVar.FIRST_TRAVEL_DONE)
        access.flyToFortis()
    }

    private suspend fun Dialogue.regulusFortis() {
        when (stage(player)) {
            TpStage.TALK_TO_REGULUS -> {
                chatPlayer(quiz, "I need to get to the Teomat quickly.")
                regulus(
                    happy,
                    "Then you'll want a quetzal of your own. Meet Renu. She's younger than " +
                        "Primio, but she'll fly you anywhere in Varlamore with a landing site.",
                )
                regulus(neutral, "Here's some feed. Give it to her and she'll let you ride.")
                if (access.invAdd(access.inv, TpObj.FEED).failure) {
                    regulus(neutral, "Your pack's full. Make some space and come back.")
                    return
                }
                objbox(TpObj.FEED, "Regulus hands you some quetzal feed.")
                access.setStage(TpStage.FEED_RENU)
            }
            TpStage.FEED_RENU -> {
                if (playerContainsObj(TpObj.FEED)) {
                    regulus(neutral, "Use that feed on Renu and she'll be yours to ride.")
                    return
                }
                if (access.invAdd(access.inv, TpObj.FEED).failure) {
                    regulus(neutral, "Make some space and I'll give you more feed.")
                    return
                }
                regulus(neutral, "Lost it already? Here's some more.")
            }
            else ->
                if (stage(player) >= TpStage.COMPLETE) {
                    regulusAfterQuest()
                } else {
                    regulus(neutral, "Primio can fly you back to Varrock whenever you like.")
                }
        }
    }

    private suspend fun Dialogue.regulusAfterQuest() {
        regulus(happy, "How's Renu treating you?")
        val choice =
            menu(
                "Could I have some more quetzal feed?" to 0,
                "Can you fly me to Varrock?" to 1,
                "Fine, thanks." to 2,
            )
        when (choice) {
            0 -> {
                val given = access.giveFeed()
                if (given == 0) {
                    regulus(neutral, "You've no room for it. Clear some space first.")
                } else {
                    objbox(TpObj.FEED, "Regulus hands you $given quetzal feed.")
                    regulus(neutral, "Ten feed builds a landing site, along with the stone.")
                }
            }
            1 -> access.flyToVarrock()
            else -> regulus(happy, "Glad to hear it.")
        }
    }

    private suspend fun ProtectedAccess.feedRenu() {
        when {
            stage(player) == TpStage.FEED_RENU -> {
                if (!takeOne(TpObj.FEED)) return
                setStage(TpStage.FLY_TO_TEOMAT)
                mes("Renu gobbles up the feed. She'll let you ride her now.")
            }
            stage(player) < TpStage.FEED_RENU -> mes("Renu eyes you warily.")
            else -> mes("Renu doesn't seem hungry right now.")
        }
    }

    private suspend fun ProtectedAccess.flyToFortis() {
        if (player.vars[TpVar.FIRST_TRAVEL] < TpVar.FIRST_TRAVEL_DONE) {
            mes("You should speak to Regulus Cento first.")
            return
        }
        fly(TpCoords.FORTIS_LANDING, "Primio flies you to Civitas illa Fortis.")
    }

    private suspend fun ProtectedAccess.flyToVarrock() {
        fly(TpCoords.VARROCK_LANDING, "Primio flies you to Varrock.")
    }

    private suspend fun ProtectedAccess.fly(dest: CoordGrid, message: String) {
        ifClose()
        fadeOverlay(0, FADE_CLEAR, 0, FADE_BLACK, FADE_CLIENT_CYCLES)
        delay(2)
        telejump(dest)
        delay(1)
        fadeOverlay(0, FADE_BLACK, 0, FADE_CLEAR, FADE_CLIENT_CYCLES)
        closeFadeOverlay(cycles = 2)
        mes(message)
    }

    private fun clearSpawns(uid: PlayerUid) {
        clearMezan(uid)
        clearCultists(uid)
        clearAzali(uid)
    }

    private fun npcType(internal: String) =
        ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC)) ?: error("Missing $internal")

    private fun CoordGrid.chebyshevDistance(other: CoordGrid): Int =
        maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    private class JournalStep(val from: Int, val until: Int, val text: String)

    private companion object {
        const val CHILDREN_OF_THE_SUN = "quest_childrenofthesun"
        const val THIEVING_XP = 3000.0
        const val MEZAN_PRAYER_STREAK = 4
        const val SPAWN_LIFESPAN = 1000
        const val FOUNTAIN_RADIUS = 3
        const val TEMPLE_STAIRS_SPLIT_Z = 3086
        const val CRYPT_STAIRS_SPLIT_Z = 9486
        const val PICKPOCKET_SEQ = "seq.human_pickpocket"
        const val FADE_CLEAR = 255
        const val FADE_BLACK = 0
        const val FADE_CLIENT_CYCLES = 50

        val JOURNAL_STEPS =
            listOf(
                JournalStep(
                    TpStage.FIND_METZLI,
                    TpStage.FIND_METZLI,
                    "The <red>Tullus twins</red> sent me to meet <red>Prince Itzla</red> at the " +
                        "temple south of the bazaar. I should ask the <red>Teokan of Ranul</red>.",
                ),
                JournalStep(
                    TpStage.MEET_ITZLA,
                    TpStage.MEET_ITZLA,
                    "<red>Metzli</red> told me the prince is in the <red>crypt</red> beneath the " +
                        "temple, north through the gate.",
                ),
                JournalStep(
                    TpStage.RETURN_TO_TWINS,
                    TpStage.RETURN_TO_TWINS,
                    "Someone in the delegation plotted to kill <red>Servius</red>. The prince " +
                        "suspects the knights. I should speak to the <red>Tullus twins</red>.",
                ),
                JournalStep(
                    TpStage.FIND_KNIGHTS,
                    TpStage.FIND_KNIGHTS,
                    "The twins gave me a <red>Varlamore crest</red>. I need to send all six " +
                        "<red>Knights of Varlamore</red> back to the palace.",
                ),
                JournalStep(
                    TpStage.KNIGHTS_GATHERED,
                    TpStage.KNIGHTS_GATHERED,
                    "All six knights are returning. I should report to the <red>Tullus " +
                        "twins</red>.",
                ),
                JournalStep(
                    TpStage.SEARCH_HEADQUARTERS,
                    TpStage.SEARCH_HEADQUARTERS,
                    "I should search the knights' chests on the top floor of the <red>Kualti " +
                        "Headquarters</red>, west of the palace.",
                ),
                JournalStep(
                    TpStage.LETTER_FOUND,
                    TpStage.LETTER_FOUND,
                    "I found an <red>incriminating letter</red>. Once I've read it I should show " +
                        "the <red>Tullus twins</red>.",
                ),
                JournalStep(
                    TpStage.JUDGED,
                    TpStage.TALK_TO_REGULUS,
                    "Ennius executed <red>Velam</red>. I should tell the prince at the " +
                        "<red>Teomat</red>. <red>Regulus Cento</red> east of the palace can help.",
                ),
                JournalStep(
                    TpStage.FEED_RENU,
                    TpStage.FEED_RENU,
                    "Regulus gave me my own quetzal, <red>Renu</red>. I should give her the " +
                        "<red>quetzal feed</red>.",
                ),
                JournalStep(
                    TpStage.FLY_TO_TEOMAT,
                    TpStage.FLY_TO_TEOMAT,
                    "I should fly with Renu to the <red>Teomat</red> and find <red>Prince " +
                        "Itzla</red> by the shrine.",
                ),
                JournalStep(
                    TpStage.TALK_TO_METZLI,
                    TpStage.TALK_TO_METZLI,
                    "I should ask <red>Metzli</red> in the Teomat library about the " +
                        "<red>Final Dawn</red>.",
                ),
                JournalStep(
                    TpStage.CULTISTS,
                    TpStage.CULTISTS,
                    "<red>Cultists</red> have attacked the Teomat! I need to defeat them.",
                ),
                JournalStep(
                    TpStage.SERVIUS_INJURED,
                    TpStage.SERVIUS_INJURED,
                    "The cultists are dead, but <red>Servius</red> was wounded. I should speak " +
                        "to <red>Prince Itzla</red>.",
                ),
            )
    }
}
