package org.rsmod.content.skills.sailing

/**
 * What a port's notice board pins up: the player's own tasks from this board first, then the
 * highest tasks they can take, then the nearest locked ones so the next unlocks are visible.
 * Jagex's `port_task_board` clientscripts draw at most [NOTICES] rows.
 */
object PortTaskBoard {
    const val NOTICES = 8

    /** Children `port_task_draw_notice` creates per notice; the clickable one comes first. */
    const val COMPONENTS_PER_NOTICE = 6

    /** Bounty targets don't spawn yet, so boards only show bounties a player already holds. */
    const val BOUNTIES_ON_BOARD = false

    fun notices(port: Port, level: Int, held: Set<Int>, bounties: Boolean = BOUNTIES_ON_BOARD): List<PortTask> {
        val candidates: List<PortTask> =
            CourierTasks.offeredAt(port) + BountyTasks.offeredAt(port).filter { bounties || it.id in held }
        val (mine, open) = candidates.partition { it.id in held }
        val (available, locked) = open.partition { it.level <= level }
        return (
            mine +
                available.sortedWith(compareByDescending<PortTask> { it.level }.thenBy { it.id }) +
                locked.sortedWith(compareBy<PortTask> { it.level }.thenBy { it.id })
        ).take(NOTICES)
    }

    fun noticeIndex(comsub: Int): Int? = if (comsub < 0) null else comsub / COMPONENTS_PER_NOTICE
}
