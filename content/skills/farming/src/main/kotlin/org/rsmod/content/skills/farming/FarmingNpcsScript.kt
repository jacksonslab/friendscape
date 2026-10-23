package org.rsmod.content.skills.farming

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FarmingNpcsScript @Inject constructor(private val patches: FarmingPatches) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(LEPRECHAUN) { startDialogue(it.npc) { leprechaun() } }
        onOpNpc3(LEPRECHAUN) { openStore() }
        onOpNpc4(LEPRECHAUN) { depositAll() }
        onOpNpcU(LEPRECHAUN) { useOnLeprechaun(it.objType.internalName) }

        for (tool in FarmingTool.entries) {
            onIfModalButton("component.farming_tools:${tool.component}") { withdraw(tool, it.op) }
            onIfModalButton("component.farming_tools_side:${tool.component}") { store(tool, it.op) }
        }

        for ((farmer, farmerPatches) in FarmingData.patches.filter { it.farmer != null }.groupBy { it.farmer!! }) {
            onOpNpc1(farmer) { startDialogue(it.npc) { farmer(farmerPatches) } }
            for (patch in farmerPatches) {
                when (patch.farmerOp) {
                    3 -> onOpNpc3(farmer) { startDialogue(it.npc) { pay(patch) } }
                    4 -> onOpNpc4(farmer) { startDialogue(it.npc) { pay(patch) } }
                }
            }
        }
    }

    private suspend fun Dialogue.leprechaun() {
        chatNpc(happy, "Ah, 'tis a foine day to be sure! Were yez wantin' me to store yer tools, or maybe ye might be wantin' yer stuff noted?")
        val topic =
            choice3(
                "Yes please.",
                1,
                "What can you store?",
                2,
                "No thanks, I'll keep hold of my stuff.",
                3,
            )
        when (topic) {
            1 -> access.openStore()
            2 -> {
                chatPlayer(quiz, "What can you store?")
                chatNpc(
                    neutral,
                    "We'll hold onto yer rake, seed dibber, spade, secateurs, waterin' can and trowel - " +
                        "but mind it's not one of them fancy trowels only archaeologists use! We'll " +
                        "take a few buckets off yer hands too, and even yer compost and plant cure.",
                )
                chatNpc(
                    neutral,
                    "And if ye hand me any crops ye've grown, I'll swap 'em for banknotes so ye can " +
                        "carry more.",
                )
            }
            else -> chatPlayer(neutral, "No thanks, I'll keep hold of my stuff.")
        }
    }

    private fun ProtectedAccess.openStore() {
        ifOpenMainSidePair(STORE, STORE_SIDE)
        for (tool in FarmingTool.entries) {
            ifSetEvents("component.farming_tools:${tool.component}", -1..-1, *STORE_EVENTS)
            ifSetEvents("component.farming_tools_side:${tool.component}", -1..-1, *STORE_EVENTS)
        }
    }

    private suspend fun ProtectedAccess.withdraw(tool: FarmingTool, op: IfButtonOp) {
        val amount = amount(tool, op) ?: return
        ToolStoreFlows.withdraw(this, tool, amount)
    }

    private suspend fun ProtectedAccess.store(tool: FarmingTool, op: IfButtonOp) {
        val amount = amount(tool, op) ?: return
        ToolStoreFlows.store(this, tool, amount)
    }

    /**
     * The store's clientscripts list Remove-1/5/X/All (or a single Remove for one-slot tools) in
     * that order while the quantity varbit is at its default, which is all this server sends.
     */
    private suspend fun ProtectedAccess.amount(tool: FarmingTool, op: IfButtonOp): Int? {
        if (ToolStore.capacity(tool) <= 1 || tool == FarmingTool.WateringCan) {
            return if (op == IfButtonOp.Op1) 1 else null
        }
        return when (op) {
            IfButtonOp.Op1 -> 1
            IfButtonOp.Op2 -> 5
            IfButtonOp.Op3 -> countDialog()
            IfButtonOp.Op4 -> Int.MAX_VALUE
            else -> null
        }
    }

    private fun ProtectedAccess.depositAll() {
        var stored = false
        for (tool in FarmingTool.entries) {
            stored = ToolStoreFlows.store(this, tool, Int.MAX_VALUE, quiet = true) || stored
        }
        if (!stored) {
            mes("You haven't got anything that the leprechaun can store.")
        }
    }

    private fun ProtectedAccess.useOnLeprechaun(obj: String) {
        val tool = FarmingTool.byObj(obj)
        if (tool != null) {
            ToolStoreFlows.store(this, tool, Int.MAX_VALUE)
            return
        }
        ToolStoreFlows.note(this, obj)
    }

    private suspend fun Dialogue.farmer(farmerPatches: List<Patch>) {
        chatNpc(neutral, "Hello there. I look after the farming patches around here.")
        val topic =
            choice2(
                "Would you look after my crops for me?",
                true,
                "That's all, thanks.",
                false,
            )
        if (!topic) {
            chatPlayer(neutral, "That's all, thanks.")
            return
        }
        chatPlayer(quiz, "Would you look after my crops for me?")
        val patch =
            if (farmerPatches.size == 1) {
                farmerPatches.single()
            } else {
                choice2(
                    "The ${farmerPatches[0].label} patch.",
                    farmerPatches[0],
                    "The ${farmerPatches[1].label} patch.",
                    farmerPatches[1],
                )
            }
        pay(patch)
    }

    /** A paid farmer guards a growing crop from disease until it is harvested or cleared. */
    private suspend fun Dialogue.pay(patch: Patch) {
        access.let { patches.update(it.player) }
        val state = patches.state(player, patch)
        val crop = patches.crop(state)
        when {
            crop == null || state.stump -> {
                chatNpc(neutral, "You don't have anything planted in that patch. Plant something and I might agree to look after it for you.")
                return
            }
            crop.payment == null -> {
                chatNpc(neutral, "Sorry, I don't look after crops like that.")
                return
            }
            state.protected -> {
                chatNpc(neutral, "I don't know what you're talking about - I'm already looking after that patch for you.")
                return
            }
            state.health == Health.Dead -> {
                chatNpc(sad, "That patch is dead. There's nothing I can do with it now.")
                return
            }
            state.health == Health.Diseased -> {
                chatNpc(neutral, "The crop's diseased. Cure it first and then I'll keep an eye on it.")
                return
            }
            state.stage >= crop.grownStage -> {
                chatNpc(neutral, "That crop is fully grown already. It doesn't need looking after.")
                return
            }
        }
        val payment = crop!!.payment!!
        val name = access.ocName(access.ocCert(payment).let { access.ocUncert(it) })
        val wanted = "${crop.paymentCount} x $name"
        if (!ToolStoreFlows.hasPayment(access, payment, crop.paymentCount)) {
            chatNpc(neutral, "I want $wanted for that. You don't have enough, though.")
            return
        }
        chatNpc(neutral, "If you like, but I want $wanted for that.")
        val accept = choice2("Okay, it's a deal.", true, "No, that's too much.", false)
        if (!accept) {
            chatPlayer(neutral, "No, that's too much.")
            return
        }
        if (!ToolStoreFlows.takePayment(access, patches, patch, payment, crop.paymentCount)) {
            chatNpc(neutral, "Hold on - you don't seem to have it any more.")
            return
        }
        chatNpc(happy, "That'll do nicely. Leave it with me - I'll make sure that patch grows for you.")
    }

    companion object {
        const val LEPRECHAUN = "npc.farming_tools_leprechaun"
        const val STORE = "interface.farming_tools"
        const val STORE_SIDE = "interface.farming_tools_side"

        private val STORE_EVENTS =
            arrayOf(IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op9, IfEvent.Op10)
    }
}

/**
 * The item-moving halves of the leprechaun and the farmers, kept apart from the NPC wiring so the
 * inventory-safety tests can drive them directly. Every flow moves items in one transaction and
 * only then touches the store varbits, so a failed transaction leaves both untouched.
 */
object ToolStoreFlows {
    fun withdraw(access: ProtectedAccess, tool: FarmingTool, requested: Int): Boolean =
        with(access) {
            if (tool == FarmingTool.WateringCan) {
                val can = ToolStore.storedCan(player) ?: return false.also { mes("You don't have a watering can stored.") }
                if (invAdd(inv, can, 1).failure) {
                    mes("You don't have enough inventory space.")
                    return false
                }
                ToolStore.setStoredCan(player, null)
                return true
            }
            val stored = ToolStore.stored(player, tool)
            if (stored <= 0) {
                mes("You don't have any of those stored.")
                return false
            }
            val stackable = ocStackable(tool.obj)
            val space = if (stackable) Int.MAX_VALUE else inv.freeSpace()
            val amount = minOf(requested, stored, space)
            if (amount <= 0) {
                mes("You don't have enough inventory space.")
                return false
            }
            if (invAdd(inv, tool.obj, amount).failure) {
                mes("You don't have enough inventory space.")
                return false
            }
            ToolStore.setStored(player, tool, stored - amount)
            true
        }

    fun store(access: ProtectedAccess, tool: FarmingTool, requested: Int, quiet: Boolean = false): Boolean =
        with(access) {
            if (tool == FarmingTool.WateringCan) {
                val can = ToolStore.WATERING_CANS.lastOrNull { inv.count(it) > 0 } ?: return false
                if (ToolStore.storedCan(player) != null) {
                    if (!quiet) mes("You already have a watering can stored.")
                    return false
                }
                if (invDel(inv, can, 1).failure) {
                    return false
                }
                ToolStore.setStoredCan(player, can)
                return true
            }
            val carried = inv.count(tool.obj)
            if (carried <= 0) {
                if (!quiet) mes("You haven't got any of those to store.")
                return false
            }
            val stored = ToolStore.stored(player, tool)
            val room = ToolStore.capacity(tool) - stored
            val amount = minOf(requested, carried, room)
            if (amount <= 0) {
                if (!quiet) mes("The leprechaun can't hold any more of those.")
                return false
            }
            if (invDel(inv, tool.obj, amount).failure) {
                return false
            }
            ToolStore.setStored(player, tool, stored + amount)
            true
        }

    /** Grown produce handed to the leprechaun comes back as banknotes, one for one. */
    fun note(access: ProtectedAccess, obj: String): Boolean =
        with(access) {
            val produce = FarmingData.crops.any { it.produce == obj }
            val cert = ocCert(obj)
            if (!produce || cert.internalName == obj) {
                mes("The leprechaun can only note produce you've grown.")
                return false
            }
            val count = inv.count(obj)
            if (count <= 0) {
                return false
            }
            val id = obj.asRSCM(RSCMType.OBJ)
            val result =
                player.invTransaction(inv) {
                    val target = select(inv)
                    delete(target, id, count)
                    add(target, id, count, cert = true)
                }
            if (result.failure) {
                mes("You don't have enough inventory space for the banknotes.")
                return false
            }
            mes("The leprechaun exchanges your items for banknotes.")
            true
        }

    fun hasPayment(access: ProtectedAccess, payment: String, count: Int): Boolean =
        with(access) { inv.count(payment) + inv.count(ocCert(payment).internalName) >= count }

    /** Farmers take payment unnoted first, then noted, all in one transaction. */
    fun takePayment(
        access: ProtectedAccess,
        patches: FarmingPatches,
        patch: Patch,
        payment: String,
        count: Int,
    ): Boolean =
        with(access) {
            val state = patches.state(player, patch)
            if (state.protected || state.empty) {
                return false
            }
            val cert = ocCert(payment).internalName
            val plain = minOf(inv.count(payment), count)
            val noted = count - plain
            if (noted > 0 && (cert == payment || inv.count(cert) < noted)) {
                return false
            }
            val result =
                player.invTransaction(inv) {
                    val target = select(inv)
                    if (plain > 0) delete(target, payment.asRSCM(RSCMType.OBJ), plain)
                    if (noted > 0) delete(target, cert.asRSCM(RSCMType.OBJ), noted)
                }
            if (result.failure) {
                return false
            }
            patches.store(player, patch, state.copy(protected = true))
            true
        }

    private fun ProtectedAccess.ocStackable(obj: String): Boolean =
        dev.openrune.ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.isStackable == true
}
