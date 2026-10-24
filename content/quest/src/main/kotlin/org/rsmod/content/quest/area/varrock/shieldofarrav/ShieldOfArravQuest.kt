package org.rsmod.content.quest.area.varrock.shieldofarrav

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.bookcases.BookcaseSearchHook
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.questItemExchange
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.ScriptContext

private var Player.phoenix by intVarp("varp.phoenixgang")
private var Player.blackArm by intVarp("varp.blackarmgang")
private var Player.shieldReturned by intVarBit("varbit.soa_shield_given")

class ShieldOfArravQuest @Inject constructor(private val locs: LocRepository) :
    QuestScript("quest_shieldofarrav", "varp.phoenixgang",
        rewards { extra("600 coins") }, ItemRewardDisplay("obj.arravcertificate", zoom = 400),
        questVarbit = "varbit.shieldofarrav") {

    override fun ScriptContext.init() {
        onOpNpc1("npc.baraek") { startDialogue(it.npc) { baraek() } }
        onOpNpc1("npc.tramppg") { startDialogue(it.npc) { charlie() } }
        onOpNpc1("npc.straven") { startDialogue(it.npc) { straven() } }
        onOpNpc1("npc.katrine") { startDialogue(it.npc) { katrine() } }
        onOpNpc1("npc.curator") { startDialogue(it.npc) { curator() } }
        onOpNpc1("npc.king_roald") { startDialogue(it.npc) { king() } }
        onOpLoc1("loc.phoenixdoor") {
            if (quest.isQuestInProgress(player) && player.blackArm == 0) crossGangDoor(it.loc)
            else mes("This is a private Phoenix Gang hideout.")
        }
        onOpLoc1("loc.blackarmdoor") {
            if (quest.isQuestInProgress(player) && player.phoenix == 0) crossGangDoor(it.loc)
            else mes("This is a private Black Arm Gang hideout.")
        }
        onOpLoc1("loc.phoenixdoor2") {
            if (inv.count(STORE_KEY) > 0) crossGangDoor(it.loc)
            else mes("The weapon store is locked. You need its key.")
        }
        onOpHeld1(BOOK) { startDialogue { readBook() } }
        onOpLoc1("loc.phoenixshutchest") { searchShield(it.loc, PHOENIX_SHIELD) }
        onOpLoc1("loc.phoenixopenchest") { searchShield(it.loc, PHOENIX_SHIELD) }
        onOpLoc1("loc.blackarmcupboardshut") { searchShield(it.loc, BLACK_ARM_SHIELD) }
        onOpLoc1("loc.blackarmcupboardopen") { searchShield(it.loc, BLACK_ARM_SHIELD) }
        onOpLoc2("loc.blackarmcupboardopen") { searchShield(it.loc, BLACK_ARM_SHIELD) }
        onOpHeldU(LEFT_CERTIFICATE, RIGHT_CERTIFICATE) {
            if (joinCertificate(this)) mes("You join the two halves of the certificate.")
            else mes("You need both certificate halves and room for the certificate.")
        }
    }

    override fun subTitle() = "talking to <col=800000>Reldo<col=000080> in <col=800000>Varrock Palace<col=000080>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        when {
            access.player.shieldReturned != 0 -> line("I should trade certificate halves with a member of the other gang, join them and show <red>King Roald</red> the complete certificate.")
            access.player.phoenix >= 9 || access.player.blackArm >= 3 -> line("I should search my gang's hideout for half of the <red>Shield of Arrav</red>, then take it to the <red>Varrock Museum curator</red>.")
            access.player.phoenix > 0 -> line("<red>Straven</red> wants an intelligence report from <red>Jonny the Beard</red> at the Blue Moon Inn.")
            access.player.blackArm > 0 -> line("<red>Katrine</red> wants two <red>Phoenix crossbows</red>. A Phoenix Gang member can help me get into their weapon store.")
            else -> line("I should read <red>The Shield of Arrav</red> in the palace library. <red>Baraek</red> knows about the Phoenix Gang; <red>Charlie the Tramp</red> knows about the Black Arm Gang. I need a partner in the other gang.")
        }
    }

    override fun completedLog(player: ProtectedAccess) = completionJournal(player) {
        line("With help from a member of the other gang, I returned the Shield of Arrav to Varrock.")
    }

    suspend fun Dialogue.offerQuest() {
        if (!quest.isQuestInProgress(player) && !quest.isQuestCompleted(player)) {
            chatNpc(neutral, "A valuable shield was stolen from the museum by a gang of thieves. There's a book about it somewhere in this library.")
            chatNpc(neutral, "You'll need to work with another adventurer, and may have to fight a dangerous thief.")
            if (startQuestPrompt(quest)) quest.setQuestStage(access, 1)
        } else {
            chatNpc(neutral, "Look for The Shield of Arrav in the library. The book describes the two rival gangs.")
        }
    }

    fun claims(player: Player, bookcase: BoundLocInfo) =
        bookcase.coords.level == 0 && bookcase.coords.x in 3207..3215 && bookcase.coords.z in 3490..3498

    suspend fun ProtectedAccess.search(bookcase: BoundLocInfo) {
        if (!quest.isQuestInProgress(player)) {
            mes("You find a book about the Shield of Arrav.")
            return
        }
        if (!playerContainsObj(BOOK) && invAdd(inv, BOOK).failure) {
            mes("You need room to take the book.")
            return
        }
        startDialogue { readBook() }
    }

    private suspend fun Dialogue.readBook() {
        mesbox("The Shield of Arrav was stolen by the Phoenix Gang. The gang later split into two: the Phoenix Gang and the Black Arm Gang. Each kept half of the shield.")
        mesbox("The King of Varrock offers a reward for its return. To recover both halves, two adventurers must join opposing gangs and help each other.")
    }

    private suspend fun Dialogue.baraek() {
        chatNpc(neutral, "Information about the Phoenix Gang will cost you 20 coins.")
        if (!quest.isQuestInProgress(player) || player.blackArm > 0) return
        if (player.phoenix == 0) {
            if (!choice2("Pay 20 coins.", true, "No thanks.", false)) return
            if (!access.questItemExchange(listOf("obj.coins" to 20), emptyList())) {
                chatPlayer(sad, "I haven't got enough money.")
                return
            }
            player.phoenix = 4
        }
        chatNpc(neutral, "Look for the VTAM Corporation in south-eastern Varrock. Straven is down in the cellar.")
    }

    private suspend fun Dialogue.charlie() {
        chatNpc(neutral, "The Black Arm Gang live down the alley behind me. Ask Katrine about joining.")
    }

    private suspend fun Dialogue.straven() {
        if (!quest.isQuestInProgress(player) || player.blackArm > 0) {
            chatNpc(neutral, "This is a private business. Move along.")
            return
        }
        if (player.phoenix < 9) {
            chatNpc(neutral, "If you want to join the Phoenix Gang, bring me the intelligence report carried by Jonny the Beard in the Blue Moon Inn.")
            player.phoenix = maxOf(8, player.phoenix)
            if (!access.questItemExchange(listOf(REPORT to 1), listOf(STORE_KEY to 1))) return
            player.phoenix = 9
            objbox(STORE_KEY, "Straven takes the report and gives you the weapon store key.")
        } else if (!access.playerContainsObj(STORE_KEY)) {
            if (access.invAdd(access.inv, STORE_KEY).failure) return
            objbox(STORE_KEY, "Straven gives you another weapon store key.")
        }
        chatNpc(neutral, "You're one of us now. Our shield half is in the chest. A Black Arm Gang member will need your help getting two crossbows from our weapon store.")
    }

    private suspend fun Dialogue.katrine() {
        if (!quest.isQuestInProgress(player) || player.phoenix > 0) {
            chatNpc(angry, "Get out of here!")
            return
        }
        if (player.blackArm < 3) {
            chatNpc(neutral, "Prove yourself. Bring me two Phoenix crossbows from the Phoenix Gang weapon store.")
            player.blackArm = 2
            if (!access.questItemExchange(listOf(CROSSBOW to 2), emptyList())) return
            player.blackArm = 3
            chatNpc(happy, "You've earned your place in the Black Arm Gang.")
        }
        chatNpc(neutral, "Our half of the Shield of Arrav is in the cupboard upstairs.")
    }

    private suspend fun ProtectedAccess.searchShield(loc: BoundLocInfo, shield: String) {
        arriveDelay()
        val joined = if (shield == PHOENIX_SHIELD) player.phoenix >= 9 else player.blackArm >= 3
        if (!joined || !quest.isQuestInProgress(player)) {
            mes("You find nothing you can take.")
            return
        }
        if (playerContainsObj(shield)) { mes("You already have this half of the shield."); return }
        if (invAdd(inv, shield).failure) { mes("You need some inventory space."); return }
        if (shield == PHOENIX_SHIELD && loc.id == "loc.phoenixshutchest".asRSCM())
            locs.change(loc, "loc.phoenixopenchest", 100)
        if (shield == BLACK_ARM_SHIELD && loc.id == "loc.blackarmcupboardshut".asRSCM())
            locs.change(loc, "loc.blackarmcupboardopen", 100)
        mes("You find half of the Shield of Arrav.")
    }

    private suspend fun Dialogue.curator() {
        if (!quest.isQuestInProgress(player)) { chatNpc(neutral, "Welcome to Varrock Museum."); return }
        val shield = if (player.phoenix >= 9) PHOENIX_SHIELD else BLACK_ARM_SHIELD
        val certificate = if (shield == PHOENIX_SHIELD) LEFT_CERTIFICATE else RIGHT_CERTIFICATE
        if (exchangeShield(access, shield, certificate)) {
            player.shieldReturned = 1
            objbox(certificate, "The curator gives you two matching certificate halves. Trade one for the other gang's half.")
        } else chatNpc(neutral, "Bring me your half of the Shield of Arrav. Then exchange certificate halves with your partner.")
    }

    private suspend fun Dialogue.king() {
        if (!quest.isQuestInProgress(player) || player.inv.count(CERTIFICATE) == 0) {
            chatNpc(neutral, "The museum curator can certify the return of the Shield of Arrav.")
            return
        }
        chatNpc(happy, "You have recovered the Shield of Arrav! Here is your reward.")
        if (!redeemCertificate(access)) { chatNpc(neutral, "Make room for your reward."); return }
        quest.completeQuest(access)
    }

    private suspend fun ProtectedAccess.crossGangDoor(door: BoundLocInfo) {
        arriveDelay()
        val eastWest = door.angleId == 0 || door.angleId == 2
        val destination = if (eastWest)
            door.coords.translateX(if (player.coords.x >= door.x) -1 else 0)
        else door.coords.translateZ(if (player.coords.z >= door.z) -1 else 0)
        locs.del(door, 2)
        walk(destination)
        delay(2)
    }

    companion object {
        const val BOOK = "obj.the_shield_of_arrav"
        const val REPORT = "obj.intelligence_report"
        const val STORE_KEY = "obj.phoenixkey2"
        const val CROSSBOW = "obj.phoenix_crossbow"
        const val PHOENIX_SHIELD = "obj.arravshield1"
        const val BLACK_ARM_SHIELD = "obj.arravshield2"
        const val LEFT_CERTIFICATE = "obj.arravcertificate_lft"
        const val RIGHT_CERTIFICATE = "obj.arravcertificate_rht"
        const val CERTIFICATE = "obj.arravcertificate"

        fun exchangeShield(access: ProtectedAccess, shield: String, certificate: String) =
            access.questItemExchange(listOf(shield to 1), listOf(certificate to 2))

        fun joinCertificate(access: ProtectedAccess) = access.questItemExchange(
            listOf(LEFT_CERTIFICATE to 1, RIGHT_CERTIFICATE to 1), listOf(CERTIFICATE to 1))

        fun redeemCertificate(access: ProtectedAccess) =
            access.questItemExchange(listOf(CERTIFICATE to 1), listOf("obj.coins" to 600))
    }
}

class ShieldOfArravBookcases @Inject constructor(private val shield: ShieldOfArravQuest) : BookcaseSearchHook {
    override fun claims(player: Player, bookcase: BoundLocInfo) = shield.claims(player, bookcase)
    override suspend fun ProtectedAccess.search(bookcase: BoundLocInfo) {
        with(shield) { search(bookcase) }
    }
}
