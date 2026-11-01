package org.rsmod.content.skills.firemaking

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.obj.isSuccess
import org.rsmod.game.MapClock
import org.rsmod.game.obj.Obj

internal fun ProtectedAccess.coatLogs(firelighter: Int, coloredLogs: Int): Boolean =
    player.invTransaction(inv) {
        val inventory = select(inv)
        delete { from = inventory; obj = "obj.logs".firemakingObjId(); strictCount = 1 }
        delete { from = inventory; obj = firelighter; strictCount = 1 }
        insert { into = inventory; obj = coloredLogs; strictCount = 1 }
    }.success

internal fun ProtectedAccess.dropFiremakingLog(
    log: String,
    objects: ObjRegistry,
    clock: MapClock,
): Obj? {
    val take = invDel(inv, log, autoCommit = false, ignoreVirtualStorage = true)
    if (!take.success) return null
    val ground = Obj.fromServer(clock, coords, log, 1)
    if (!objects.add(ground).isSuccess()) return null
    take.commitAll()
    return ground
}

internal inline fun ProtectedAccess.consumeFiremakingLog(log: String, changeWorld: () -> Boolean): Boolean {
    val take = invDel(inv, log, autoCommit = false, ignoreVirtualStorage = true)
    if (!take.success || !changeWorld()) return false
    take.commitAll()
    return true
}
