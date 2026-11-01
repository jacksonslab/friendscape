package org.rsmod.content.skills.mining.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.skills.mining.drops.CamdozaalDropTables
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CamdozaalForge : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(CRUSHER) { crushDeposits() }
        onOpLocU(CRUSHER, DEPOSIT) { crushDeposits() }
        onOpNpc1(RAMARNO) { startDialogue(it.npc) { ramarno() } }
    }

    private suspend fun ProtectedAccess.crushDeposits() {
        if (player.smithingLvl < CRUSH_LEVEL) {
            mes("You need a Smithing level of $CRUSH_LEVEL to crush barronite deposits.")
            return
        }
        if (!hasHammer()) {
            mes("You need a hammer to crush the deposits.")
            return
        }
        if (DEPOSIT !in inv) {
            mes("You need barronite deposits to use the crusher.")
            return
        }
        while (DEPOSIT in inv && hasHammer()) {
            anim(if ("obj.imcando_hammer" in inv || IMCANDO_OFFHAND in inv) IMCANDO_ANIM else CRUSH_ANIM)
            delay(CRUSH_TICKS)
            if (!hasHammer() || DEPOSIT !in inv) {
                return
            }
            val reward = rollCrush()
            if (!swapDeposit(reward)) {
                mes("You don't have enough inventory space to crush the deposit.")
                return
            }
            statAdvance("stat.smithing", CRUSH_XP)
            mes("You crush a barronite deposit with your hammer.")
            mes(rewardMessage(reward))
        }
    }

    private fun ProtectedAccess.hasHammer(): Boolean =
        "obj.hammer" in inv || "obj.imcando_hammer" in inv || IMCANDO_OFFHAND in inv

    private fun ProtectedAccess.rollCrush(): Reward {
        val drop =
            when (val result = CamdozaalDropTables.crusher.roll(player, ArgMap()).flatten()) {
                is RollResult.Single -> result.result
                is RollResult.ListOf -> result.results.firstOrNull { !it.isNothing }
                is RollResult.Nothing -> null
            }
        if (drop == null || drop.isNothing || drop.obj.isEmpty()) {
            return Reward(SHARDS, random.of(6, 8))
        }
        return Reward(drop.obj, rollCount(drop))
    }

    private fun ProtectedAccess.rollCount(drop: DropRollItem): Int =
        random.of(drop.count.first, maxOf(drop.count.first, drop.count.last))

    private fun ProtectedAccess.swapDeposit(reward: Reward): Boolean =
        player.invTransaction(inv) {
            val inventory = select(inv)
            delete {
                from = inventory
                obj = DEPOSIT.asRSCM(RSCMType.OBJ)
                strictCount = 1
            }
            insert {
                into = inventory
                obj = reward.obj.asRSCM(RSCMType.OBJ)
                strictCount = reward.count
            }
        }.success

    private fun rewardMessage(reward: Reward): String =
        if (reward.obj == SHARDS) {
            "You receive ${reward.count} barronite shards."
        } else {
            "You find ${getInvObj(InvObj(reward.obj)).name.lowercase().withArticle()} inside the deposit!"
        }

    private fun String.withArticle(): String = if (first() in "aeiou") "an $this" else "a $this"

    private suspend fun Dialogue.ramarno() {
        chatNpc(happy, "Welcome to Camdozaal, ${player.displayName}. What can I do for you?")
        when (
            menu(
                "Can you make me a barronite mace?" to Service.Mace,
                "Can you repair my Imcando hammer?" to Service.Hammer,
                "Nothing, thanks." to Service.None,
            )
        ) {
            Service.Mace ->
                forge(
                    request = "Can you make me a barronite mace?",
                    parts = MACE_PARTS,
                    shards = MACE_SHARDS,
                    product = "obj.barronite_mace",
                    missing =
                        "Bring me a barronite head, handle and guard, along with $MACE_SHARDS " +
                            "barronite shards, and I'll forge you a mace.",
                )
            Service.Hammer ->
                forge(
                    request = "Can you repair my Imcando hammer?",
                    parts = listOf("obj.imcando_hammer_broken"),
                    shards = HAMMER_SHARDS,
                    product = "obj.imcando_hammer",
                    missing =
                        "Bring me a broken Imcando hammer and $HAMMER_SHARDS barronite shards, and " +
                            "the Sacred Forge will make it whole again.",
                )
            Service.None -> chatPlayer(neutral, "Nothing, thanks.")
        }
    }

    private suspend fun Dialogue.forge(
        request: String,
        parts: List<String>,
        shards: Int,
        product: String,
        missing: String,
    ) {
        chatPlayer(quiz, request)
        if (parts.any { it !in player.inv } || player.inv.count(SHARDS) < shards) {
            chatNpc(neutral, missing)
            return
        }
        if (!player.forge(parts, shards, product)) {
            chatNpc(neutral, "You'll need to make some room before I hand that over.")
            return
        }
        objbox(product, "Ramarno works the Sacred Forge and hands you the finished piece.")
    }

    private data class Reward(val obj: String, val count: Int)

    private enum class Service {
        Mace,
        Hammer,
        None,
    }

    companion object {
        const val CRUSHER = "loc.camdozaal_anvil"
        const val DEPOSIT = "obj.camdozaal_barronite_deposit"
        const val SHARDS = "obj.camdozaal_barronite_shard"
        const val RAMARNO = "npc.camdozaal_ramarno_multi"
        const val CRUSH_LEVEL = 14
        const val CRUSH_XP = 30.0
        const val CRUSH_TICKS = 4
        const val MACE_SHARDS = 1500
        const val HAMMER_SHARDS = 1250
        private const val IMCANDO_OFFHAND = "obj.imcando_hammer_offhand"
        private const val CRUSH_ANIM = "seq.human_smithing"
        private const val IMCANDO_ANIM = "seq.human_smithing_imcando_hammer"
        val MACE_PARTS =
            listOf("obj.barronite_mace_1", "obj.barronite_mace_2", "obj.barronite_mace_3")
    }
}

internal fun Player.forge(parts: List<String>, shards: Int, product: String): Boolean =
    invTransaction(inv) {
        val inventory = select(inv)
        for (part in parts) {
            delete {
                from = inventory
                obj = part.asRSCM(RSCMType.OBJ)
                strictCount = 1
            }
        }
        delete {
            from = inventory
            obj = CamdozaalForge.SHARDS.asRSCM(RSCMType.OBJ)
            strictCount = shards
        }
        insert {
            into = inventory
            obj = product.asRSCM(RSCMType.OBJ)
            strictCount = 1
        }
    }.success
