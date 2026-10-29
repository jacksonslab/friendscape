package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.enums.FriendscapePortCoinBagEnums.friendscape_port_coin_bag_max
import org.rsmod.api.enums.FriendscapePortCoinBagEnums.friendscape_port_coin_bag_min
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
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
    private val shipbuilding: Shipbuilding,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (port in Ports.ALL) {
            port.noticeBoard?.let { board -> onOpLoc1(locName(board)) { noticeBoard(it.loc) } }
        }
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

    private suspend fun ProtectedAccess.noticeBoard(loc: BoundLocInfo) {
        val port = Ports.byNoticeBoard(loc.id) ?: return
        val level = statBase(STAT_SAILING)
        val active = TaskSlots.active(player)
        val capacity = TaskSlots.capacity(level)
        val action =
            choose(
                listOf(
                    "Take a new courier task",
                    "Review my tasks (${active.size}/$capacity)",
                    "Cancel a task",
                    "Commission a boat",
                ),
                listOf(BoardAction.Take, BoardAction.Review, BoardAction.Cancel, BoardAction.Shipwright),
                title = "${port.name} notice board",
            ) ?: return
        when (action) {
            BoardAction.Take -> takeTask(port, level)
            BoardAction.Review -> reviewTasks()
            BoardAction.Cancel -> cancelTask()
            BoardAction.Shipwright -> shipbuilding.commission(this)
        }
    }

    private suspend fun ProtectedAccess.takeTask(port: Port, level: Int) {
        val slot = TaskSlots.freeSlot(player, level)
        if (slot == null) {
            mesbox("You can't take on any more port tasks right now. Complete or cancel one first.")
            return
        }
        val held = TaskSlots.active(player).map { it.taskId }.toSet()
        val offers =
            CourierTasks.offeredAt(port)
                .filter { it.level <= level && it.id !in held }
                .sortedByDescending { it.level }
                .take(MAX_OFFERS)
        if (offers.isEmpty()) {
            mesbox("There are no courier tasks on this board you can take at your Sailing level.")
            return
        }
        val task = choose(offers.map(::describe), offers, title = "Choose a courier task") ?: return
        if (TaskSlots.get(player, slot).taskId != 0 || task.id in TaskSlots.active(player).map { it.taskId }) {
            return
        }
        TaskSlots.set(player, TaskSlot(slot, task.id, taken = 0, delivered = 0))
        mesbox(task.flavour)
        mes("Collect ${crateWord(task.crates)} from the ledger table at ${portName(task.cargoPort)}.")
    }

    private suspend fun ProtectedAccess.reviewTasks() {
        val active = TaskSlots.active(player)
        if (active.isEmpty()) {
            mesbox("You don't have any port tasks.")
            return
        }
        for (slot in active) {
            val task = slot.task ?: continue
            mes(
                "${task.name}: ${slot.delivered}/${task.crates} delivered, ${slot.taken - slot.delivered} in transit. " +
                    "Collect at ${portName(task.cargoPort)}, deliver to ${portName(task.destination)}.",
            )
        }
    }

    private suspend fun ProtectedAccess.cancelTask() {
        val active = TaskSlots.active(player).filter { it.task != null }
        val slot = choose(active.map { it.task!!.name }, active, title = "Cancel which task?") ?: return
        val task = slot.task ?: return
        val confirmed = choice2("Yes, cancel it.", true, "No, keep it.", false, title = "Cancel ${task.name}?")
        if (!confirmed || TaskSlots.get(player, slot.index).taskId != task.id) {
            return
        }
        removeCrates(task)
        TaskSlots.clear(player, slot.index)
        cargo.refreshCarrying(player)
        mes("You cancel the ${task.name}.")
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

    private fun describe(task: CourierTask): String =
        "${task.name} (lvl ${task.level}, ${task.xp} xp): ${portName(task.cargoPort)} to ${portName(task.destination)}"

    private fun portName(dockRowId: Int): String = Ports.byDock(dockRowId)?.name ?: "another port"

    private fun crateWord(count: Int): String = if (count == 1) "a crate" else "$count crates"

    private fun cargoHoldVariants(): List<Pair<String, String>> =
        BoatTypes.ALL.mapNotNull { it.cargoHold }
            .mapNotNull { ServerCacheManager.getObject(RSCM.getRSCM(it.loc))?.transforms }
            .filter { it.size >= 2 && it[0] > 0 && it[1] > 0 }
            .map { locName(it[0]) to locName(it[1]) }
            .distinct()

    private enum class BoardAction {
        Take,
        Review,
        Cancel,
        Shipwright,
    }

    private enum class TakeMode {
        First,
        Last,
        Any,
    }

    private companion object {
        private const val MAX_OFFERS = 12
        private const val MAX_BAG_TIER = 4

        private fun coinBag(tier: Int): String = "obj.port_task_lootsack_t${tier}_coins"

        private fun locName(id: Int): String = RSCM.getReverseMapping(RSCMType.LOC, id)

        private fun objName(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)
    }
}
