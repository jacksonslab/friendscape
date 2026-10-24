package org.rsmod.content.quest.area.varrock.demonslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

private var Player.silverlightTaken by intVarBit("varbit.delrith_silverlight_case")
private var Player.drainKey by intVarBit("varbit.delrith_drain_key")
private var Player.circleDestroyed by intVarBit("varbit.delrith_seen_summoning_cutscene")
private var Player.traibornBones by intVarBit("varbit.demon_slayer_traiborn_bones")

class DemonSlayerQuest
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val players: PlayerList,
) :
    QuestScript(
        "quest_demonslayer",
        "varp.demonstart",
        rewards { extra("Silverlight") },
        ItemRewardDisplay(SILVERLIGHT, zoom = 400),
        questVarbit = "varbit.demonslayer_main",
    ) {

    private val delrithByPlayer = hashMapOf<PlayerUid, Npc>()
    private val playerByDelrith = hashMapOf<Npc, PlayerUid>()

    override fun ScriptContext.init() {
        check(quest.maxSteps == STAGE_COMPLETE) {
            "Demon Slayer end state is ${quest.maxSteps} in the cache dbrow, " +
                "but banishing Delrith writes $STAGE_COMPLETE."
        }

        onOpNpc1("npc.aris") { startDialogue(it.npc) { aris() } }
        onOpNpc1(PRYSIN) { startDialogue(it.npc) { sirPrysin() } }
        onOpNpc1("npc.captain_rovin") { startDialogue(it.npc) { captainRovin() } }
        onOpLoc1("loc.qip_ds_questdrain_key") { searchDrain(withKey = true) }
        onOpLoc1("loc.qip_ds_questdrain_nokey") { searchDrain(withKey = false) }
        onOpLocU("loc.questdrain", WATER) { pourWater() }
        onOpLocU("loc.qip_ds_questdrain_key", WATER) { pourWater() }
        onOpLoc1("loc.qip_ds_rustykey_mud") { takeDrainKey() }
        onOpLoc1("loc.qip_ds_keypipe") { takeDrainKey() }
        onOpNpc1(WEAKENED_DELRITH) { startDialogue(it.npc) { banish(it.npc) } }
        onPlayerCoordsChanged { approachCircle(player) }
        onPlayerLogout { clearDemon(player.uid) }
        val delrith = npcType(DELRITH)
        onModifyNpcHit(delrith) {
            val attacker = hit.sourceUid?.let { PlayerUid(it).resolve(players) }
            if (attacker == null || playerByDelrith[npc] != attacker.uid ||
                attacker.worn.count(SILVERLIGHT) == 0
            ) {
                hit.damage = 0
            }
        }
        onNpcQueue(delrith, "queue.death") { delrithWeakened() }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Gypsy<col=000080> in <col=800000>Varrock Square<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            strike("Aris the gypsy told me the demon Delrith is about to be summoned again.")
            line("The incantation to banish him is: <red>${incantation(p).joinToString(" ")}</red>")
            if (stage(p) == STAGE_STARTED) {
                line(
                    "I should talk to <red>Sir Prysin</red> in <red>Varrock Palace</red> about " +
                        "the sword <red>Silverlight</red>."
                )
                return@questJournal
            }
            if (!access.playerContainsObj(SILVERLIGHT)) {
                line("Sir Prysin needs all three keys to open <red>Silverlight's</red> case:")
                key(KEY_TRAIBORN, "<red>Wizard Traiborn</red> in the Wizards' Tower has one.")
                key(KEY_ROVIN, "<red>Captain Rovin</red> at the top of the palace has one.")
                key(KEY_DRAIN, "One fell down the <red>drain</red> by the palace kitchen.")
                return@questJournal
            }
            strike("I have Silverlight.")
            line(
                "I need to go to the <red>stone circle</red> south of Varrock, defeat " +
                    "<red>Delrith</red> and banish him with the incantation."
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Using Silverlight and the incantation from Aris, I banished the demon Delrith " +
                    "back to the void."
            )
        }

    private fun QuestJournalBuilder.key(key: String, text: String) {
        if (access.playerContainsObj(key)) strike(text.replace("<red>", "").replace("</red>", ""))
        else line(text)
    }

    private fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun npcType(internal: String) =
        ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC)) ?: error("Missing $internal")

    private fun incantation(player: Player): List<String> =
        INCANTATION_VARBITS.map { WORDS.getOrElse(player.vars[it] - 1) { WORDS[0] } }

    private fun ProtectedAccess.rollIncantation() {
        val order = WORDS.indices.toMutableList()
        for (i in order.lastIndex downTo 1) {
            val j = random.of(i + 1)
            order[i] = order[j].also { order[j] = order[i] }
        }
        INCANTATION_VARBITS.forEachIndexed { index, varbit ->
            VarPlayerIntMapSetter.set(player, varbit, order[index] + 1)
        }
    }

    private suspend fun Dialogue.aris() {
        when {
            quest.isQuestCompleted(player) -> {
                chatNpc(happy, "Greetings young one. You're a hero now. That was a good bit of " +
                    "demon-slaying.")
                chatPlayer(happy, "Thanks.")
            }
            quest.isQuestInProgress(player) -> {
                chatNpc(quiz, "Greetings. How goes thy quest?")
                chatPlayer(neutral, "I'm still working on it.")
                chatNpc(
                    neutral,
                    "Well if you need any advice I'm always here, young one. Remember, the " +
                        "incantation is: ${incantation(player).joinToString(" ")}.",
                )
            }
            else -> fortune()
        }
    }

    private suspend fun Dialogue.fortune() {
        chatNpc(happy, "Hello, young one.")
        chatNpc(
            happy,
            "Cross my palm with silver and the future will be revealed to you.",
        )
        val pay =
            menu(
                "Okay, here you go." to true,
                "Who are you calling young one?!" to false,
            )
        if (!pay) {
            chatPlayer(angry, "Who are you calling young one?!")
            chatNpc(neutral, "You have been warned...")
            return
        }
        chatPlayer(neutral, "Okay, here you go.")
        if (access.invDel(access.inv, COINS).failure) {
            chatPlayer(sad, "Oh dear. I don't have any money.")
            return
        }
        chatNpc(neutral, "Come closer and listen carefully to what the future holds, as I peer " +
            "into the swirling mists of the crystal ball.")
        chatNpc(neutral, "I can see images forming. I can see you.")
        chatNpc(shocked, "You are holding a very impressive looking sword. I'm sure I recognise " +
            "that sword...")
        chatNpc(worried, "There is a big dark shadow appearing now.")
        chatNpc(worried, "Aaargh!")
        chatPlayer(quiz, "Very interesting. What does the Aaargh bit mean?")
        chatNpc(
            worried,
            "It's Delrith! Delrith is coming! Delrith is a powerful demon. 150 years ago he " +
                "almost destroyed the city of Varrock. The great hero Wally banished him with " +
                "the magic sword Silverlight.",
        )
        chatNpc(
            worried,
            "Now the evil dark wizards are summoning him again, in the stone circle south of " +
                "the city. And you are the one destined to stop him!",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(worried, "I don't think I'm up to that.")
            chatNpc(sad, "Then Varrock is doomed...")
            return
        }
        chatPlayer(quiz, "How am I meant to fight a demon who can destroy cities?!")
        access.rollIncantation()
        quest.setQuestStage(access, STAGE_STARTED)
        chatNpc(
            neutral,
            "If you face Delrith while he is still weak from being summoned, and use the correct " +
                "weapon, you will not find the task too arduous.",
        )
        chatNpc(
            neutral,
            "Wally used the sword Silverlight. Sir Prysin of the palace guard is his " +
                "descendant; he should know where it is now.",
        )
        chatNpc(
            neutral,
            "Once Delrith is weakened, you must banish him with the magic incantation. Listen " +
                "carefully: ${incantation(player).joinToString(" ")}.",
        )
        mesbox("Aris's incantation is now in your quest journal.")
    }

    private suspend fun Dialogue.sirPrysin() {
        when {
            quest.isQuestCompleted(player) -> prysinAfterQuest()
            stage(player) == STAGE_STARTED -> prysinExplains()
            stage(player) == STAGE_KEYS -> prysinKeys()
            else -> {
                chatNpc(neutral, "Hello, who are you?")
                chatPlayer(neutral, "I am a mighty adventurer.")
                chatNpc(neutral, "Well, I'm Sir Prysin of the palace guard. Good day to you.")
            }
        }
    }

    private suspend fun Dialogue.prysinExplains() {
        chatPlayer(neutral, "Aris said I should come and talk to you.")
        chatNpc(quiz, "Aris? Is she still alive? I remember her from my youth...")
        chatPlayer(neutral, "I need to find Silverlight. Delrith is being summoned again.")
        chatNpc(
            shocked,
            "Delrith? Then you'll need Silverlight indeed. I have it here, locked in a case, but " +
                "it takes three keys to open, and I don't have any of them on me.",
        )
        chatNpc(
            neutral,
            "Captain Rovin, at the top of the palace, has one. My friend Wizard Traiborn, in the " +
                "Wizards' Tower, has another.",
        )
        chatNpc(
            sad,
            "And I'm afraid I dropped the third down the drain outside the palace kitchen. It " +
                "will have gone into the sewers by now, if it went anywhere.",
        )
        quest.setQuestStage(access, STAGE_KEYS)
        chatPlayer(neutral, "Then I'll go and get the keys.")
    }

    private suspend fun Dialogue.prysinKeys() {
        if (access.playerContainsObj(SILVERLIGHT)) {
            chatNpc(neutral, "Go and banish Delrith, quickly!")
            return
        }
        if (player.silverlightTaken != 0) {
            if (access.invAdd(access.inv, SILVERLIGHT).failure) {
                chatNpc(neutral, "Make some room and I'll give you another Silverlight.")
                return
            }
            objbox(SILVERLIGHT, "Sir Prysin hands you a replacement Silverlight.")
            return
        }
        chatNpc(quiz, "So how are you doing with getting the keys?")
        val held = KEYS.count { player.inv.count(it) > 0 }
        if (held < KEYS.size) {
            chatPlayer(neutral, "I've got $held of the 3 keys so far.")
            chatNpc(
                neutral,
                "Remember: Captain Rovin has one, Wizard Traiborn has one, and one went down the " +
                    "drain by the palace kitchen.",
            )
            return
        }
        chatPlayer(happy, "I've got all three keys!")
        if (!openCase(access)) {
            chatNpc(neutral, "You'll need a free space for the sword.")
            return
        }
        player.silverlightTaken = 1
        chatNpc(happy, "Excellent! Now I can give you Silverlight.")
        objbox(SILVERLIGHT, "Sir Prysin unlocks the case and hands you Silverlight.")
        chatNpc(neutral, "Now go and banish Delrith, at the stone circle south of Varrock.")
    }

    private suspend fun Dialogue.prysinAfterQuest() {
        chatNpc(happy, "Hello. I've heard you stopped the demon, well done.")
        if (access.playerContainsObj(SILVERLIGHT)) {
            chatPlayer(happy, "Yes, that's right.")
            return
        }
        chatPlayer(sad, "I'm afraid I've lost Silverlight.")
        if (access.invAdd(access.inv, SILVERLIGHT).failure) {
            chatNpc(neutral, "Make some room and I'll hand you another one I had made.")
            return
        }
        objbox(SILVERLIGHT, "Sir Prysin hands you another Silverlight.")
    }

    private suspend fun Dialogue.captainRovin() {
        chatNpc(angry, "What are you doing up here? Only the palace guards are allowed up here.")
        if (stage(player) != STAGE_KEYS || access.playerContainsObj(KEY_ROVIN) ||
            access.playerContainsObj(SILVERLIGHT)
        ) {
            chatPlayer(neutral, "I'll be on my way.")
            return
        }
        val topic =
            menu(
                "I am one of the palace guards." to 1,
                "What about the King?" to 2,
                "Yes I know, but this is important." to 3,
            )
        when (topic) {
            1 -> {
                chatPlayer(neutral, "I am one of the palace guards.")
                chatNpc(angry, "No, you're not! I know all the palace guards.")
            }
            2 -> {
                chatPlayer(quiz, "What about the King?")
                chatNpc(neutral, "Well, the King is allowed up here too.")
            }
            else -> {
                chatPlayer(neutral, "Yes I know, but this is important.")
                chatNpc(quiz, "Ok, I'm listening. Tell me what's so important.")
                chatPlayer(
                    worried,
                    "There's a demon who wants to invade this city. Sir Prysin sent me for your " +
                        "key to Silverlight's case.",
                )
                chatNpc(
                    neutral,
                    "Sir Prysin? If he sent you, then I suppose it must be true. Here, take my key.",
                )
                if (access.invAdd(access.inv, KEY_ROVIN).failure) {
                    chatNpc(neutral, "Make some room first.")
                    return
                }
                objbox(KEY_ROVIN, "Captain Rovin hands you a key.")
            }
        }
    }

    fun traibornKeyTopic(player: Player): String? {
        if (stage(player) != STAGE_KEYS) return null
        if (player.inv.count(KEY_TRAIBORN) > 0 || player.inv.count(SILVERLIGHT) > 0) return null
        if (player.worn.count(SILVERLIGHT) > 0) return null
        return when (player.traibornBones) {
            0 -> "I need to get a key given to you by Sir Prysin."
            BONES_ASKED + BONES_NEEDED -> "About that key you summoned..."
            else -> "I've got some bones for you."
        }
    }

    suspend fun Dialogue.traibornKey() {
        if (player.traibornBones == 0) {
            chatPlayer(neutral, "I need to get a key given to you by Sir Prysin.")
            chatNpc(
                confused,
                "Sir Prysin? Who's that? What would I want his key for? Ah, that key! I've " +
                    "hidden it with a spell. I'll need $BONES_NEEDED sets of bones to cast it " +
                    "again.",
            )
            player.traibornBones = BONES_ASKED
        }
        val owed = BONES_ASKED + BONES_NEEDED - player.traibornBones
        if (owed > 0) {
            val given = minOf(player.inv.count(BONES), owed)
            if (given == 0) {
                chatNpc(neutral, "Bring me $owed more bones and I'll get that key back for you.")
                return
            }
            if (access.invDel(access.inv, BONES, given).failure) {
                return
            }
            player.traibornBones += given
            mesbox("You give Traiborn $given set${if (given == 1) "" else "s"} of bones.")
            if (given < owed) {
                chatNpc(neutral, "I still need ${owed - given} more.")
                return
            }
            chatNpc(happy, "Hurrah! That's all $BONES_NEEDED sets of bones.")
            mesbox("Traiborn moves his hands in mysterious patterns and mumbles a spell.")
        }
        if (access.invAdd(access.inv, KEY_TRAIBORN).failure) {
            chatNpc(neutral, "Your pack's too full for the key. Come back with some room.")
            return
        }
        objbox(KEY_TRAIBORN, "Traiborn hands you a key.")
        chatPlayer(happy, "Thank you very much.")
    }

    private suspend fun ProtectedAccess.searchDrain(withKey: Boolean) {
        arriveDelay()
        if (!withKey) {
            mes("You search the drain but find nothing.")
            return
        }
        startDialogue {
            mesbox("You search the drain and can see a key, but you can't reach it.")
            chatPlayer(
                neutral,
                "If I could wash it down the drain, I might be able to find it in the sewers.",
            )
        }
    }

    private suspend fun ProtectedAccess.pourWater() {
        arriveDelay()
        if (!emptyBucket(this)) return
        if (player.drainKey != 0) {
            mes("You pour water down the drain. Nothing else happens.")
        } else {
            mes("You pour the liquid down the drain and the key is washed away.")
            player.drainKey = 1
        }
    }

    private suspend fun ProtectedAccess.takeDrainKey() {
        arriveDelay()
        if (stage(player) != STAGE_KEYS || player.drainKey == 0 ||
            playerContainsObj(KEY_DRAIN) || playerContainsObj(SILVERLIGHT)
        ) return
        if (invAdd(inv, KEY_DRAIN).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        player.drainKey = 2
        mes("You pick the rusty key out of the mud.")
    }

    private fun approachCircle(player: Player) {
        if (stage(player) != STAGE_KEYS || player.coords.level != 0) return
        if (player.coords.chebyshevDistance(STONE_TABLE) > SUMMON_RADIUS) return
        if (player.inv.count(SILVERLIGHT) == 0 && player.worn.count(SILVERLIGHT) == 0) return
        val existing = delrithByPlayer[player.uid]
        if (existing != null && existing.isSlotAssigned) return
        clearDemon(player.uid)
        val delrith = Npc(DELRITH, DELRITH_SPAWN)
        npcRepo.add(delrith, DELRITH_LIFESPAN)
        delrithByPlayer[player.uid] = delrith
        playerByDelrith[delrith] = player.uid
        player.circleDestroyed = 1
        player.mes("The dark wizards' summoning is complete! Delrith appears!")
        delrith.opPlayer2(player, aiPlayerInteractions)
    }

    private suspend fun StandardNpcAccess.delrithWeakened() {
        val hunter = playerByDelrith.remove(npc)
        val coords = npc.coords
        npcRepo.del(npc, Int.MAX_VALUE)
        if (hunter == null) return
        val weakened = Npc(WEAKENED_DELRITH, coords)
        npcRepo.add(weakened, WEAKENED_TICKS)
        delrithByPlayer[hunter] = weakened
        playerByDelrith[weakened] = hunter
    }

    private suspend fun Dialogue.banish(weakened: Npc) {
        if (playerByDelrith[weakened] != player.uid || stage(player) != STAGE_KEYS) {
            mesbox("This isn't your demon to banish.")
            return
        }
        if (player.worn.count(SILVERLIGHT) == 0 && player.inv.count(SILVERLIGHT) == 0) {
            mesbox("You need Silverlight to banish Delrith.")
            return
        }
        mesbox("As you chant, Delrith is sucked towards the vortex...")
        val wanted = incantation(player)
        var correct = true
        for (word in wanted) {
            val said =
                choice5(WORDS[0], WORDS[0], WORDS[1], WORDS[1], WORDS[2], WORDS[2], WORDS[3],
                    WORDS[3], WORDS[4], WORDS[4], title = "Which word comes next?")
            chatPlayer(neutral, "$said...")
            if (said != word) {
                correct = false
                break
            }
        }
        if (!weakened.isSlotAssigned) {
            mesbox("Delrith has recovered before you could finish the incantation.")
            clearDemon(player.uid)
            approachCircle(player)
            return
        }
        if (!correct) {
            mesbox("Suddenly the vortex closes and Delrith is still here.")
            playerByDelrith.remove(weakened)
            npcRepo.del(weakened, Int.MAX_VALUE)
            val delrith = Npc(DELRITH, weakened.coords)
            npcRepo.add(delrith, DELRITH_LIFESPAN)
            delrithByPlayer[player.uid] = delrith
            playerByDelrith[delrith] = player.uid
            delrith.opPlayer2(player, aiPlayerInteractions)
            return
        }
        playerByDelrith.remove(weakened)
        delrithByPlayer.remove(player.uid)
        npcRepo.del(weakened, Int.MAX_VALUE)
        mesbox("Delrith is sucked into the vortex... back into the dark dimension from which he " +
            "came.")
        quest.completeQuest(access)
    }

    private fun clearDemon(uid: PlayerUid) {
        val demon = delrithByPlayer.remove(uid) ?: return
        playerByDelrith.remove(demon)
        if (demon.isSlotAssigned) npcRepo.del(demon, Int.MAX_VALUE)
    }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_KEYS = 2
        const val STAGE_COMPLETE = 3

        const val SILVERLIGHT = "obj.silverlight"
        const val KEY_TRAIBORN = "obj.silverlight_key_1"
        const val KEY_ROVIN = "obj.silverlight_key_2"
        const val KEY_DRAIN = "obj.silverlight_key_3"
        val KEYS = listOf(KEY_TRAIBORN, KEY_ROVIN, KEY_DRAIN)

        private const val PRYSIN = "npc.sir_prysin"
        private const val DELRITH = "npc.delrith"
        private const val WEAKENED_DELRITH = "npc.delrith_weakened"
        private const val COINS = "obj.coins"
        private const val BONES = "obj.bones"
        private const val WATER = "obj.bucket_water"

        private const val BONES_NEEDED = 25
        private const val BONES_ASKED = 1
        private const val SUMMON_RADIUS = 6
        private const val DELRITH_LIFESPAN = 500
        private const val WEAKENED_TICKS = 50

        private val STONE_TABLE = CoordGrid(3227, 3369, 0)
        private val DELRITH_SPAWN = CoordGrid(3227, 3367, 0)
        private val WORDS = listOf("Carlem", "Aber", "Camerinthum", "Purchai", "Gabindo")
        private val INCANTATION_VARBITS =
            (1..5).map { "varbit.delrith_incantation_$it" }

        /** Swaps the three keys for Silverlight in one transaction, or changes nothing. */
        fun openCase(access: ProtectedAccess): Boolean =
            access.player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                for (key in KEYS) {
                    delete {
                        from = inventory
                        obj = key.asRSCM()
                        strictCount = 1
                    }
                }
                insert {
                    into = inventory
                    obj = SILVERLIGHT.asRSCM()
                    strictCount = 1
                }
            }.success

        fun emptyBucket(access: ProtectedAccess): Boolean =
            access.player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                delete {
                    from = inventory
                    obj = WATER.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = "obj.bucket_empty".asRSCM()
                    strictCount = 1
                }
            }.success
    }
}
