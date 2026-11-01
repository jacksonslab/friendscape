package org.rsmod.content.interfaces.trade

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.GameMessage
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifCloseModals
import org.rsmod.api.player.ui.ifOpenMainSidePair
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.advanced.onOpPlayer4
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.thisPlayerRemoved by intVarBit("varbit.trade_this_player_removed")
private var Player.otherPlayerRemoved by intVarBit("varbit.trade_other_player_removed")

class TradeScript
@Inject
constructor(private val eventBus: EventBus, private val sessions: TradeSessions) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpPlayer4 { request(it.target) }
        onIfModalButton("component.tradeside:side_layer") { offer(it.comsub, it.op) }
        onIfModalButton("component.trademain:your_offer") { remove(it.comsub, it.op) }
        onIfModalButton("component.trademain:accept") { accept(TradeStage.Offer) }
        onIfModalButton("component.trademain:decline") { decline() }
        onIfModalButton("component.tradeconfirm:trade2accept") { accept(TradeStage.Confirm) }
        onIfModalButton("component.tradeconfirm:trade2decline") { decline() }
        onIfClose("interface.trademain") { closed(player, TradeStage.Offer) }
        onIfClose("interface.tradeconfirm") { closed(player, TradeStage.Confirm) }
        onPlayerLogout { leave(player) }
        onPlayerLogin { if (player.tradeOffer.isNotEmpty()) TradeExchange.returnOffer(player) }
    }

    private fun ProtectedAccess.request(target: Player) {
        if (IronmanRestrictions.blockTrade(player, target)) {
            return
        }
        if (sessions[player] != null) {
            return
        }
        if (sessions[target] != null) {
            mes("Other player is busy at the moment.")
            return
        }
        if (sessions.hasRequested(target, player, mapClock)) {
            open(sessions.start(target, player))
            return
        }
        sessions.request(player, target, mapClock)
        mes("Sending trade offer...")
        GameMessage.requestMes(target, "wishes to trade with you.", player.displayName, ChatType.TradeReq)
    }

    private fun open(session: TradeSession) {
        for (player in listOf(session.first, session.second)) {
            val partner = session.partner(player)
            player.ifOpenMainSidePair("interface.trademain", "interface.tradeside", -1, -1, eventBus)
            player.thisPlayerRemoved = 0
            player.otherPlayerRemoved = 0
            player.startInvTransmit(player.tradeOffer)
            ClientScripts.interfaceInvInit(
                player,
                player.inv,
                SIDE_INV,
                objRowCount = 4,
                objColCount = 7,
                op1 = "Offer",
                op2 = "Offer-5",
                op3 = "Offer-10",
                op4 = "Offer-All",
                op5 = "Offer-X",
            )
            player.ifSetEvents(SIDE_INV, 0..27, *ITEM_OPS)
            player.ifSetEvents(YOUR_OFFER, 0..27, *ITEM_OPS)
            player.ifSetEvents(OTHER_OFFER, 0..27, IfEvent.Op10)
            player.ifSetText("component.trademain:title", "Trading with: ${partner.displayName}")
            player.runClientScript(TRADE_PARTNER_SET, partner.displayName)
        }
        refresh(session)
    }

    private suspend fun ProtectedAccess.offer(slot: Int, op: IfButtonOp) {
        val session = sessions[player]?.takeIf { it.stage == TradeStage.Offer } ?: return
        val obj = inv[slot] ?: return
        if (!getInvObj(obj).tradeable) {
            mes("You can't trade that item.")
            return
        }
        val count = requestedCount(op, inv.count(getInvObj(obj).internalName)) ?: return
        if (sessions[player] !== session || session.stage != TradeStage.Offer) {
            return
        }
        if (TradeExchange.move(player, inv, player.tradeOffer, slot, count) > 0) {
            changed(session)
        }
    }

    private suspend fun ProtectedAccess.remove(slot: Int, op: IfButtonOp) {
        val session = sessions[player]?.takeIf { it.stage == TradeStage.Offer } ?: return
        val offer = player.tradeOffer
        val obj = offer[slot] ?: return
        val count = requestedCount(op, offer.count(getInvObj(obj).internalName)) ?: return
        if (sessions[player] !== session || session.stage != TradeStage.Offer) {
            return
        }
        if (TradeExchange.move(player, offer, inv, slot, count) > 0) {
            player.thisPlayerRemoved = 1
            session.partner(player).otherPlayerRemoved = 1
            changed(session)
        }
    }

    private suspend fun ProtectedAccess.requestedCount(op: IfButtonOp, available: Int): Int? =
        when (op) {
            IfButtonOp.Op1 -> 1
            IfButtonOp.Op2 -> 5
            IfButtonOp.Op3 -> 10
            IfButtonOp.Op4 -> available
            IfButtonOp.Op5 -> countDialog().takeIf { it > 0 }
            else -> null
        }

    private fun changed(session: TradeSession) {
        session.resetAccepts()
        refresh(session)
    }

    private fun refresh(session: TradeSession) {
        for (player in listOf(session.first, session.second)) {
            val partner = session.partner(player)
            UpdateInventory.updateInvOther(player, partner.tradeOffer)
            player.ifSetText(
                "component.trademain:free_space_text",
                "${partner.displayName} has ${partner.inv.freeSpace()} free inventory slots.",
            )
            player.ifSetText(statusComponent(session), statusText(session, player))
        }
    }

    private fun statusComponent(session: TradeSession): String =
        if (session.stage == TradeStage.Confirm) CONFIRM_STATUS else OFFER_STATUS

    private fun statusText(session: TradeSession, player: Player): String =
        when {
            session.hasAccepted(player) -> "Waiting for other player..."
            session.hasAccepted(session.partner(player)) -> "Other player has accepted."
            session.stage == TradeStage.Confirm -> "Are you sure you want to make this trade?"
            else -> ""
        }

    private fun ProtectedAccess.accept(stage: TradeStage) {
        val session = sessions[player]?.takeIf { it.stage == stage } ?: return
        if (!TradeExchange.complete(session.first, session.second, commit = false)) {
            mes("One of you doesn't have enough inventory space for this trade.")
            return
        }
        if (!session.accept(player)) {
            refresh(session)
            return
        }
        when (stage) {
            TradeStage.Offer -> confirm(session)
            TradeStage.Confirm -> finish(session)
            TradeStage.Done -> Unit
        }
    }

    private fun confirm(session: TradeSession) {
        session.resetAccepts()
        session.stage = TradeStage.Confirm
        for (player in listOf(session.first, session.second)) {
            val partner = session.partner(player)
            player.ifOpenMainSidePair("interface.tradeconfirm", "interface.tradeside", -1, -1, eventBus)
            ClientScripts.interfaceInvInit(player, player.inv, SIDE_INV, objRowCount = 4, objColCount = 7)
            player.ifSetText("component.tradeconfirm:tradeopponent", "Trading with:<br>${partner.displayName}")
        }
        refresh(session)
    }

    private fun finish(session: TradeSession) {
        session.stage = TradeStage.Done
        sessions.end(session)
        val completed = TradeExchange.complete(session.first, session.second, commit = true)
        for (player in listOf(session.first, session.second)) {
            if (!completed) {
                TradeExchange.returnOffer(player)
            }
            player.stopInvTransmit(player.tradeOffer)
            player.ifCloseModals(eventBus)
            player.mes(if (completed) "Accepted trade." else "Trade failed: not enough inventory space.")
        }
    }

    private fun ProtectedAccess.decline() {
        val session = sessions[player] ?: return
        cancel(session, decliner = player)
    }

    private fun closed(player: Player, stage: TradeStage) {
        val session = sessions[player]?.takeIf { it.stage == stage } ?: return
        cancel(session, decliner = player)
    }

    private fun leave(player: Player) {
        sessions.forget(player)
        val session = sessions[player] ?: return
        cancel(session, decliner = player)
    }

    private fun cancel(session: TradeSession, decliner: Player) {
        session.stage = TradeStage.Done
        sessions.end(session)
        for (player in listOf(session.first, session.second)) {
            TradeExchange.returnOffer(player)
            player.stopInvTransmit(player.tradeOffer)
            player.ifCloseModals(eventBus)
        }
        session.partner(decliner).mes("Other player declined trade.")
    }

    private companion object {
        const val SIDE_INV = "component.tradeside:side_layer"
        const val YOUR_OFFER = "component.trademain:your_offer"
        const val OTHER_OFFER = "component.trademain:other_offer"
        const val OFFER_STATUS = "component.trademain:status"
        const val CONFIRM_STATUS = "component.tradeconfirm:title"
        const val TRADE_PARTNER_SET = 209
        val ITEM_OPS =
            arrayOf(IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op10)
    }
}
