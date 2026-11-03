package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import org.rsmod.api.enums.FriendscapePortCoinBagEnums.friendscape_port_coin_bag_max
import org.rsmod.api.enums.FriendscapePortCoinBagEnums.friendscape_port_coin_bag_min
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PortTaskScript
@Inject
constructor(
    private val cargo: CargoService,
    private val boats: BoatManager,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val bounties: BountyRewards,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (port in Ports.ALL) {
            port.noticeBoard?.let { board -> onOpLoc1(locName(board)) { openBoard(it.loc) } }
            port.portMaster?.let { master ->
                onOpNpc3(npcName(master)) { claimBounties() }
                onOpNpc4(npcName(master)) { cancelTask() }
            }
        }
        onIfModalButton("component.port_task_board:container") { selectNotice(it.comsub) }
        onOpLoc1("loc.dock_loading_bay_ledger_table_withdraw") { takeCargo(it.loc, TakeMode.First) }
        onOpLoc2("loc.dock_loading_bay_ledger_table_withdraw") { takeCargo(it.loc, TakeMode.Last) }
        onOpLoc3("loc.dock_loading_bay_ledger_table_withdraw") { takeCargo(it.loc, TakeMode.Any) }
        onOpLoc1("loc.dock_loading_bay_ledger_table_deposit") { depositCargo(it.loc) }

        for (hold in cargoHoldVariants()) {
            val (empty, loaded) = hold
            onOpLoc1(empty) { withdrawFromHold() }
            onOpLoc2(empty) { loadHold() }
            onOpLoc1(loaded) { loadHold() }
            onOpLoc2(loaded) { loadHold() }
        }
        for (tier in 0..MAX_BAG_TIER) {
            onOpHeld1(coinBag(tier)) { openCoinBag(tier) }
        }
    }

    private fun ProtectedAccess.openBoard(loc: BoundLocInfo) {
        val port = Ports.byNoticeBoard(loc.id) ?: return
        player.taskBoardPort = port.dockRowId
        ifOpenMainModal("interface.port_task_board")
        ifSetEvents(
            "component.port_task_board:container",
            0 until PortTaskBoard.NOTICES * PortTaskBoard.COMPONENTS_PER_NOTICE,
            IfEvent.Op1,
        )
        drawBoard(port)
    }

    private fun ProtectedAccess.drawBoard(port: Port) {
        val rows = notices(port).map { it.rowId }
        val args = Array<Any>(PortTaskBoard.NOTICES) { rows.getOrElse(it) { -1 } }
        runClientScript(BOARD_INIT.asRSCM(RSCMType.CLIENTSCRIPT), *args)
    }

    private fun ProtectedAccess.notices(port: Port): List<PortTask> =
        PortTaskBoard.notices(port, statBase(STAT_SAILING), TaskSlots.active(player).map { it.taskId }.toSet())

    private suspend fun ProtectedAccess.selectNotice(comsub: Int) {
        val port = Ports.byDock(player.taskBoardPort) ?: return
        val index = PortTaskBoard.noticeIndex(comsub) ?: return
        val task = notices(port).getOrNull(index) ?: return
        ifOpenSub("interface.port_task_info", BOARD_POPUP, IfSubType.Overlay)
        ifSetEvents("component.port_task_info:select", -1..-1, IfEvent.Op1)
        ifSetEvents("component.port_task_info:cancel", -1..-1, IfEvent.Op1)
        runClientScript(INFO_DRAW.asRSCM(RSCMType.CLIENTSCRIPT), BOARD_POPUP.asRSCM(RSCMType.COMPONENT), task.rowId, -1, -1)
        val confirmed = interfaceCountResume() == 1
        ifCloseSub("interface.port_task_info")
        if (!confirmed) {
            return
        }
        val held = TaskSlots.active(player).firstOrNull { it.taskId == task.id }
        if (held != null) {
            cancel(held)
        } else {
            accept(task)
        }
        drawBoard(port)
    }

    private fun ProtectedAccess.accept(task: PortTask) {
        val level = statBase(STAT_SAILING)
        if (task.level > level) {
            mes("You need a Sailing level of ${task.level} to take on the ${task.name}.")
            return
        }
        val slot = TaskSlots.freeSlot(player, level)
        if (slot == null) {
            mes("You can't take on any more port tasks right now. Complete or cancel one first.")
            return
        }
        TaskSlots.set(player, TaskSlot(slot, task.id, taken = 0, delivered = 0))
        when (task) {
            is CourierTask ->
                mes("Collect ${crateWord(task.crates)} from the ledger table at ${portName(task.cargoPort)}.")
            is BountyTask ->
                mes(
                    "Bring ${task.amount} x ${task.item.name.lowercase()} from " +
                        "${ServerCacheManager.getNpc(task.target)?.name?.lowercase() ?: "sea creatures"} " +
                        "to any port master.",
                )
        }
    }

    private fun ProtectedAccess.cancel(slot: TaskSlot) {
        val task: PortTask = slot.task ?: slot.bounty ?: return
        if (!task.cancellable) {
            mes("You can't cancel the ${task.name}.")
            return
        }
        if (TaskSlots.get(player, slot.index).taskId != task.id) {
            return
        }
        if (task is CourierTask) {
            removeCrates(task)
        }
        TaskSlots.clear(player, slot.index)
        cargo.refreshCarrying(player)
        mes("You cancel the ${task.name}.")
    }

    private fun ProtectedAccess.claimBounties() {
        if (bounties.claim(this) > 0) {
            return
        }
        val held = TaskSlots.active(player).mapNotNull { it.bounty }
        if (held.isEmpty()) {
            mes("You don't have any bounty tasks to hand in.")
            return
        }
        for (bounty in held) {
            mes("${bounty.name}: ${inv.count(bounty.item.internalName)}/${bounty.amount} ${bounty.item.name.lowercase()} collected.")
        }
    }

    private suspend fun ProtectedAccess.cancelTask() {
        val active = TaskSlots.active(player).filter { (it.task ?: it.bounty)?.cancellable == true }
        if (active.isEmpty()) {
            mes("You don't have any port tasks you can cancel.")
            return
        }
        val names = active.map { (it.task ?: it.bounty)!!.name }
        val slot = choose(names, active, title = "Cancel which task?") ?: return
        val name = (slot.task ?: slot.bounty)?.name ?: return
        val confirmed = choice2("Yes, cancel it.", true, "No, keep it.", false, title = "Cancel $name?")
        if (confirmed) {
            cancel(slot)
        }
    }

    private fun ProtectedAccess.removeCrates(task: CourierTask) {
        val type = objName(task.crate.id)
        for (inv in listOf(player.inv, cargo.hold(this))) {
            val count = invTotal(inv, type)
            if (count > 0) {
                invDel(inv, type, count)
            }
        }
    }

    private suspend fun ProtectedAccess.takeCargo(loc: BoundLocInfo, mode: TakeMode) {
        val port = Ports.byLedger(loc.id) ?: return
        if (cargo.carriedCrate(player) != null) {
            mes("Your hands are full. Load the crate you're carrying into your cargo hold first.")
            return
        }
        val waiting = TaskSlots.active(player).filter { it.task?.cargoPort == port.dockRowId && it.taken < it.task!!.crates }
        if (waiting.isEmpty()) {
            mes("There's no cargo waiting here for any of your tasks.")
            return
        }
        val slot =
            when (mode) {
                TakeMode.First -> waiting.first()
                TakeMode.Last ->
                    waiting.firstOrNull { it.index == player.vars["varbit.port_task_last_cargo_taken"] } ?: waiting.first()
                TakeMode.Any -> choose(waiting.map { it.task!!.name }, waiting, title = "Take which cargo?") ?: return
            }
        val task = slot.task ?: return
        if (TaskSlots.get(player, slot.index) != slot || cargo.carriedCrate(player) != null) {
            return
        }
        if (!invAdd(inv, objName(task.crate.id)).success) {
            mes("You need a free inventory space to carry the crate.")
            return
        }
        TaskSlots.set(player, slot.copy(taken = slot.taken + 1))
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varbit.port_task_last_cargo_taken", slot.index)
        cargo.refreshCarrying(player)
        mes("You take a crate of cargo for the ${task.name}.")
    }

    private fun ProtectedAccess.depositCargo(loc: BoundLocInfo) {
        val port = Ports.byLedger(loc.id) ?: return
        val crate = cargo.carriedCrate(player) ?: return
        val slot =
            TaskSlots.active(player).firstOrNull {
                val task = it.task
                task != null && task.crate.id == crate.id && it.taken > it.delivered
            }
        val task = slot?.task
        if (slot == null || task == null) {
            mes("None of your port tasks need this crate.")
            return
        }
        if (task.destination != port.dockRowId) {
            mes("This crate needs to go to ${portName(task.destination)}.")
            return
        }
        if (!invDel(inv, objName(crate.id), 1).success) {
            return
        }
        val delivered = slot.copy(delivered = slot.delivered + 1)
        cargo.refreshCarrying(player)
        if (delivered.delivered < task.crates) {
            TaskSlots.set(player, delivered)
            mes("You deliver a crate. ${task.crates - delivered.delivered} still to go for the ${task.name}.")
            return
        }
        TaskSlots.clear(player, slot.index)
        complete(task)
    }

    private fun ProtectedAccess.complete(task: CourierTask) {
        statAdvance(STAT_SAILING, task.xp * xpMods.get(player, STAT_SAILING))
        player.portTasksCompleted++
        val bag = coinBag(task.coinBagTier)
        if (!invAddOrDrop(objRepo, bag)) {
            mes("Your reward is placed on the ground as your inventory is full.")
        }
        mes("You complete the ${task.name}.")
    }

    private fun ProtectedAccess.loadHold() {
        val crate = cargo.carriedCrate(player)
        if (crate == null) {
            mes("You aren't carrying any cargo.")
            return
        }
        val boat = boats.boatOf(player)
        if (boat == null || !boat.isOwnedBy(player)) {
            mes("You can only load cargo into your own boat.")
            return
        }
        val type = objName(crate.id)
        val hold = cargo.hold(this)
        val moved =
            player.invTransaction(inv, hold) {
                val from = select(inv)
                val into = select(hold)
                delete(from, crate.id, 1)
                add(into, crate.id, 1)
            }
        if (!moved.success) {
            mes("Your cargo hold is full.")
            return
        }
        cargo.refreshCarrying(player)
        mes("You load the ${ServerCacheManager.getItem(crate.id)?.name?.lowercase() ?: type} into the cargo hold.")
    }

    private suspend fun ProtectedAccess.withdrawFromHold() {
        val boat = boats.boatOf(player)
        if (boat == null || !boat.isOwnedBy(player)) {
            mes("You can only open your own cargo hold.")
            return
        }
        val hold = cargo.hold(this)
        val crates = hold.filterNotNull().filter { it.id in CourierTasks.crateIds }.map { it.id }.distinct()
        if (crates.isEmpty()) {
            mes("Your cargo hold is empty.")
            return
        }
        if (cargo.carriedCrate(player) != null) {
            mes("Your hands are full.")
            return
        }
        val crateId =
            if (crates.size == 1) {
                crates.single()
            } else {
                choose(crates.map { ServerCacheManager.getItem(it)?.name ?: objName(it) }, crates) ?: return
            }
        val moved =
            player.invTransaction(hold, inv) {
                val from = select(hold)
                val into = select(inv)
                delete(from, crateId, 1)
                add(into, crateId, 1)
            }
        if (!moved.success) {
            mes("You need a free inventory space to carry the crate.")
            return
        }
        cargo.refreshCarrying(player)
    }

    private fun ProtectedAccess.openCoinBag(tier: Int) {
        val bag = coinBag(tier)
        val min = friendscape_port_coin_bag_min[tier] ?: return
        val max = friendscape_port_coin_bag_max[tier] ?: return
        val coins = random.of(min, max)
        val opened =
            player.invTransaction(inv) {
                val target = select(inv)
                delete(target, RSCM.getRSCM(bag), 1)
                add(target, RSCM.getRSCM("obj.coins"), coins)
            }
        if (!opened.success) {
            mes("You don't have enough inventory space to open the coin bag.")
            return
        }
        mes("You open the coin bag and find $coins coins.")
    }

    private fun portName(dockRowId: Int): String = Ports.byDock(dockRowId)?.name ?: "another port"

    private fun crateWord(count: Int): String = if (count == 1) "a crate" else "$count crates"

    private fun cargoHoldVariants(): List<Pair<String, String>> =
        BoatTypes.ALL.mapNotNull { it.cargoHold }
            .mapNotNull { ServerCacheManager.getObject(RSCM.getRSCM(it.loc))?.transforms }
            .filter { it.size >= 2 && it[0] > 0 && it[1] > 0 }
            .map { locName(it[0]) to locName(it[1]) }
            .distinct()

    private enum class TakeMode {
        First,
        Last,
        Any,
    }

    private companion object {
        private const val MAX_BAG_TIER = 4

        private fun coinBag(tier: Int): String = "obj.port_task_lootsack_t${tier}_coins"

        private const val BOARD_INIT = "clientscript.port_task_board_init"
        private const val INFO_DRAW = "clientscript.port_task_info_draw"
        private const val BOARD_POPUP = "component.port_task_board:popup"

        private fun locName(id: Int): String = RSCM.getReverseMapping(RSCMType.LOC, id)

        private fun npcName(id: Int): String = RSCM.getReverseMapping(RSCMType.NPC, id)

        private fun objName(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)
    }
}
