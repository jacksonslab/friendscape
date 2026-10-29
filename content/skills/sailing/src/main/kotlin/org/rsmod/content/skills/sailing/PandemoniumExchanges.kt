package org.rsmod.content.skills.sailing

import org.rsmod.api.player.protect.ProtectedAccess

enum class JimsJob {
    Assigned,
    NoFreeSlot,
    AlreadyHeld,
}

object PandemoniumExchanges {
    const val CUP = "obj.sailing_intro_cup"

    fun haulUpCup(access: ProtectedAccess): Boolean =
        access.inv.freeSpace() > 0 && access.invAdd(access.inv, CUP).success

    /** Hands over the old cup, when the player still has it, in exchange for Jim's delivery job. */
    fun takeJimsJob(access: ProtectedAccess): JimsJob {
        val player = access.player
        val task = CourierTasks.PANDEMONIUM
        if (TaskSlots.active(player).any { it.taskId == task.id }) {
            return JimsJob.AlreadyHeld
        }
        val slot = TaskSlots.freeSlot(player, access.statBase(STAT_SAILING)) ?: return JimsJob.NoFreeSlot
        if (CUP in access.inv && !access.invDel(access.inv, CUP, 1).success) {
            return JimsJob.NoFreeSlot
        }
        TaskSlots.set(player, TaskSlot(slot, task.id, taken = 0, delivered = 0))
        return JimsJob.Assigned
    }
}
