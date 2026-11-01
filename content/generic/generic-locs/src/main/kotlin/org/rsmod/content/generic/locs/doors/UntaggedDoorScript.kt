package org.rsmod.content.generic.locs.doors

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.events.interact.LocUnimplementedEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class UntaggedDoorScript
@Inject
constructor(private val locRepo: LocRepository, private val doors: DoorScript) : PluginScript() {
    override fun ScriptContext.startup() {
        val pairs = DoorPairs(ServerCacheManager.getObjects().values)
        for ((id, next) in pairs.nextStage) {
            val nextType = ServerCacheManager.getObject(next) ?: continue
            if (id in pairs.closed) {
                onProtectedEvent<LocUnimplementedEvents.Op1>(id) { open(it.loc, it.type, nextType) }
            } else {
                onProtectedEvent<LocUnimplementedEvents.Op1>(id) { close(it.loc, it.type, nextType) }
            }
        }
        for (id in pairs.closed) {
            if (id in pairs.nextStage) {
                continue
            }
            val type = ServerCacheManager.getObject(id) ?: continue
            if (type.name.contains(LOCKED, ignoreCase = true)) {
                onProtectedEvent<LocUnimplementedEvents.Op1>(id) { mes("It's locked.") }
                continue
            }
            onProtectedEvent<LocUnimplementedEvents.Op1>(id) { walkThrough(it.loc, it.type) }
        }
    }

    private suspend fun ProtectedAccess.open(
        door: BoundLocInfo,
        type: ObjectServerType,
        next: ObjectServerType,
    ) = with(doors) { openDoor(door, type, next) }

    private suspend fun ProtectedAccess.close(
        door: BoundLocInfo,
        type: ObjectServerType,
        next: ObjectServerType,
    ) = with(doors) { closeDoor(door, type, next) }

    private suspend fun ProtectedAccess.walkThrough(door: BoundLocInfo, type: ObjectServerType) {
        if (door.shape != LocShape.WallStraight) {
            mes("The ${type.name.lowercase()} won't budge.")
            return
        }
        val inside = door.coords
        val outside = DoorTranslations.translateOpen(inside, door.shape, door.angle)
        val destination = if (coords == inside) outside else inside
        soundSynth(type.param(params.opensound))
        locRepo.del(door, PASS_DURATION)
        teleport(destination)
        delay(1)
    }

    private companion object {
        const val LOCKED = "locked"
        const val PASS_DURATION = 3
    }
}
