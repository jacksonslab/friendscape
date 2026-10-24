package org.rsmod.content.other.ironman

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.ironman.ironmanGroupId
import org.rsmod.api.player.ironman.isGroupIronman
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifOpenMainSidePair
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GroupIronmanBankScript @Inject constructor(
    private val groups: GroupIronmanService,
    private val eventBus: EventBus,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen("interface.bankmain") {
            val available = player.isGroupIronman && !player.canChooseIronman()
            player.ifSetHide("component.bankmain:gim_storage", !available)
            if (available) player.ifSetEvents("component.bankmain:gim_storage", -1..-1, IfEvent.Op1)
        }
        onIfModalButton("component.bankmain:gim_storage") { player.openSharedBank() }
        onIfClose("interface.shared_bank") {
            player.invMap[GroupIronmanService.STORAGE]?.let { player.stopInvTransmit(it) }
            player.invMap.remove(GroupIronmanService.STORAGE)
        }
        onIfModalButton("component.shared_bank:items") {
            storageOp(false, it.comsub, it.obj?.id ?: return@onIfModalButton, it.op)
        }
        onIfModalButton("component.shared_bank_side:items") {
            storageOp(true, it.comsub, it.obj?.id ?: return@onIfModalButton, it.op)
        }
        onIfModalButton("component.shared_bank:main_bank") { player.openBank(eventBus) }
        onIfModalButton("component.shared_bank:note") {
            VarPlayerIntMapSetter.toggle(player, "varbit.bank_withdrawnotes")
        }
        for ((index, suffix) in listOf("1", "5", "10", "x", "all").withIndex()) {
            onIfModalButton("component.shared_bank:quantity$suffix") {
                if (suffix == "x") {
                    val count = countDialog()
                    if (count <= 0) return@onIfModalButton
                    VarPlayerIntMapSetter.set(player, "varbit.bank_requestedquantity", count)
                }
                VarPlayerIntMapSetter.set(player, "varbit.bank_quantity_type", index)
            }
        }
        onIfModalButton("component.shared_bank:depositinv") {
            transferAndRefresh(true, -1, 0, 1)
        }
    }

    private fun Player.openSharedBank() {
        if (!isGroupIronman || canChooseIronman() || !ui.containsModal("interface.bankmain")) return
        val storage = groups.storage(this)
        if (storage == null) {
            mes("You must belong to a Group Ironman group to use shared storage.")
            return
        }
        VarPlayerIntMapSetter.set(this, "varp.if1", 0)
        VarPlayerIntMapSetter.set(this, "varp.if2", Int.MAX_VALUE)
        VarPlayerIntMapSetter.set(this, "varp.if3", storage.size)
        VarPlayerIntMapSetter.set(this, "varp.if4", 1)
        ifOpenMainSidePair("interface.shared_bank", "interface.shared_bank_side", -1, -2, eventBus)
        storage.owner = this
        startInvTransmit(storage)
        ifSetEvents("component.shared_bank:items", storage.indices, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op6, IfEvent.Op7, IfEvent.Op8, IfEvent.Op10)
        ifSetEvents("component.shared_bank_side:items", inv.indices, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op6, IfEvent.Op7, IfEvent.Op10)
        for (button in listOf("main_bank", "note", "quantity1", "quantity5", "quantity10", "quantityx", "quantityall", "depositinv")) {
            ifSetEvents("component.shared_bank:$button", -1..-1, IfEvent.Op1)
        }
        for (component in listOf("save_button", "countdown", "capacity_layer", "swap_insert", "swap_insert_graphic", "storage_unlocks", "storage_unlocks_graphic")) {
            ifSetHide("component.shared_bank:$component", true)
        }
    }

    private suspend fun ProtectedAccess.storageOp(deposit: Boolean, slot: Int, expectedId: Int, op: IfButtonOp) {
        val source = if (deposit) inv else player.invMap[GroupIronmanService.STORAGE] ?: return
        val held = source.objs.getOrNull(slot) ?: return
        if (held.id != expectedId) return
        if (op == IfButtonOp.Op10) {
            objExamine(source, slot)
            return
        }
        val total = source.filterNotNull().filter { it.id == held.id && it.vars == held.vars }
            .sumOf { it.count.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val quantity = when (op) {
            IfButtonOp.Op1 -> when (player.vars["varbit.bank_quantity_type"]) {
                1 -> 5
                2 -> 10
                3 -> player.vars["varbit.bank_requestedquantity"].coerceAtLeast(1)
                4 -> total
                else -> 1
            }
            IfButtonOp.Op2 -> 1
            IfButtonOp.Op3 -> 5
            IfButtonOp.Op4 -> 10
            IfButtonOp.Op5 -> player.vars["varbit.bank_requestedquantity"].coerceAtLeast(1)
            IfButtonOp.Op6 -> countDialog().also { if (it > 0) VarPlayerIntMapSetter.set(player, "varbit.bank_requestedquantity", it) }
            IfButtonOp.Op7 -> total
            IfButtonOp.Op8 -> if (!deposit) total - 1 else 0
            else -> 0
        }
        if (quantity > 0) transferAndRefresh(deposit, slot, expectedId, quantity, held.vars)
    }

    private fun ProtectedAccess.transferAndRefresh(deposit: Boolean, slot: Int, expectedId: Int, quantity: Int, expectedVars: Int = 0) {
        if (!player.ui.containsModal("interface.shared_bank")) return
        try {
            val notes = player.vars["varbit.bank_withdrawnotes"] != 0
            if (!groups.transfer(this, deposit, slot, expectedId, quantity, notes, expectedVars)) {
                mes("The item changed, cannot be shared, or there is not enough space for the whole transfer.")
            }
            val groupId = player.ironmanGroupId
            for (member in players) {
                if (member.ironmanGroupId != groupId || member.worldType != player.worldType || !member.ui.containsModal("interface.shared_bank")) continue
                val updated = groups.storage(member) ?: continue
                updated.owner = member
                member.invMap[GroupIronmanService.STORAGE] = updated
                UpdateInventory.updateInvFull(member, updated)
            }
        } catch (failure: Exception) {
            LOGGER.error(failure) { "Group storage transfer failed for character ${player.characterId}" }
            mes("Shared storage is temporarily unavailable. Please try again later.")
        }
    }

    private companion object {
        private val LOGGER = InlineLogger()
    }
}
