package org.rsmod.content.skills.sailing

import dev.openrune.types.ItemServerType
import org.rsmod.api.enums.FriendscapeEnums.friendscape_courier_task_xp
import org.rsmod.api.enums.NamedEnums.friendscape_port_bag_tier_xp
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.PortTaskRow
import org.rsmod.game.entity.Player

sealed interface PortTask {
    val id: Int
    val rowId: Int
    val name: String
    val level: Int
    val board: Int
    val cancellable: Boolean
}

class CourierTask(row: PortTaskRow, val xp: Int) : PortTask {
    override val id: Int = row.taskId
    override val rowId: Int = row.rowId
    override val name: String = row.name
    override val level: Int = row.levelRequired
    override val board: Int = row.startingPort?.rowId ?: row.endingPort.rowId
    val cargoPort: Int = row.cargoPort?.rowId ?: board
    val destination: Int = row.endingPort.rowId
    val crate: ItemServerType = row.cargo.first().t0
    val crates: Int = row.cargo.first().t1
    val flavour: String = row.flavourText
    override val cancellable: Boolean = row.cancellable != false

    val coinBagTier: Int
        get() = (1..MAX_BAG_TIER).count { tier -> xp >= (friendscape_port_bag_tier_xp[tier] ?: Int.MAX_VALUE) }

    override fun toString(): String = "CourierTask($id, $name)"

    private companion object {
        private const val MAX_BAG_TIER = 4
    }
}

object CourierTasks {
    val ALL: List<CourierTask> by lazy {
        PortTaskRow.all()
            .filter { it.taskType == COURIER && it.cargo.isNotEmpty() }
            .mapNotNull { row -> friendscape_courier_task_xp[row.taskId]?.let { CourierTask(row, it) } }
    }

    private val byId by lazy { ALL.associateBy { it.id } }

    val crateIds: Set<Int> by lazy { ALL.mapTo(HashSet()) { it.crate.id } }

    operator fun get(id: Int): CourierTask? = byId[id]

    val PANDEMONIUM: CourierTask by lazy { byId.getValue(PortTaskRow.getRow(PANDEMONIUM_ROW).taskId) }

    fun offeredAt(board: Port): List<CourierTask> = ALL.filter { it.board == board.dockRowId && it !== PANDEMONIUM }

    private const val COURIER = 0
    private const val PANDEMONIUM_ROW = "dbrow.port_task_sailing_intro"
}

data class TaskSlot(val index: Int, val taskId: Int, val taken: Int, val delivered: Int) {
    val task: CourierTask?
        get() = CourierTasks[taskId]

    val bounty: BountyTask?
        get() = BountyTasks[taskId]
}

/** Courier progress kept in Jagex's own `port_task_slot_*` varbits. */
object TaskSlots {
    const val COUNT = 5

    private val UNLOCK_LEVELS = intArrayOf(1, 7, 28, 56, 84)

    fun capacity(sailingLevel: Int): Int = UNLOCK_LEVELS.count { sailingLevel >= it }

    fun get(player: Player, index: Int): TaskSlot =
        TaskSlot(
            index = index,
            taskId = player.vars["varbit.port_task_slot_${index}_id"],
            taken = player.vars["varbit.port_task_slot_${index}_cargo_taken"],
            delivered = player.vars["varbit.port_task_slot_${index}_cargo_delivered"],
        )

    fun active(player: Player): List<TaskSlot> = (0 until COUNT).map { get(player, it) }.filter { it.taskId != 0 }

    fun set(player: Player, slot: TaskSlot) {
        VarPlayerIntMapSetter.set(player, "varbit.port_task_slot_${slot.index}_id", slot.taskId)
        VarPlayerIntMapSetter.set(player, "varbit.port_task_slot_${slot.index}_cargo_taken", slot.taken)
        VarPlayerIntMapSetter.set(player, "varbit.port_task_slot_${slot.index}_cargo_delivered", slot.delivered)
    }

    fun clear(player: Player, index: Int) {
        set(player, TaskSlot(index, 0, 0, 0))
    }

    fun freeSlot(player: Player, sailingLevel: Int): Int? =
        (0 until capacity(sailingLevel)).firstOrNull { get(player, it).taskId == 0 }
}
