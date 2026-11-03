package org.rsmod.content.skills.sailing

import dev.openrune.types.ItemServerType
import org.rsmod.api.enums.FriendscapeEnums.friendscape_bounty_task_xp
import org.rsmod.api.table.PortTaskRow
import org.rsmod.game.entity.Player

class BountyTask(row: PortTaskRow, val xp: Int) : PortTask {
    override val id: Int = row.taskId
    override val rowId: Int = row.rowId
    override val name: String = row.name
    override val level: Int = row.levelRequired
    override val board: Int = row.startingPort?.rowId ?: row.endingPort.rowId
    val target: Int = checkNotNull(row.bountyTargetAlive) { "Bounty task $id has no target." }.id
    val item: ItemServerType = checkNotNull(row.bountyObject) { "Bounty task $id has no bounty item." }
    val amount: Int = row.bountyObjectAmount ?: 1
    val rarity: Int = row.bountyObjectRarity ?: 1
    override val cancellable: Boolean = row.cancellable != false

    override fun toString(): String = "BountyTask($id, $name)"
}

object BountyTasks {
    val ALL: List<BountyTask> by lazy {
        PortTaskRow.all()
            .filter { it.taskType == BOUNTY && it.bountyObject != null && it.bountyTargetAlive != null }
            .mapNotNull { row -> friendscape_bounty_task_xp[row.bountyObject!!]?.let { BountyTask(row, it) } }
    }

    private val byId by lazy { ALL.associateBy { it.id } }

    operator fun get(id: Int): BountyTask? = byId[id]

    fun offeredAt(board: Port): List<BountyTask> = ALL.filter { it.board == board.dockRowId }

    /**
     * The first held bounty on [target] that still needs parts. Later bounties on the same creature
     * wait their turn, so a kill never rolls for more than one task.
     */
    fun collecting(player: Player, target: Int): Pair<TaskSlot, BountyTask>? =
        TaskSlots.active(player).firstNotNullOfOrNull { slot ->
            val bounty = slot.bounty ?: return@firstNotNullOfOrNull null
            if (bounty.target != target || player.inv.count(bounty.item.internalName) >= bounty.amount) {
                null
            } else {
                slot to bounty
            }
        }

    private const val BOUNTY = 1
}
