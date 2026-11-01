package org.rsmod.content.skills.firemaking

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.obj.isSuccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.obj.Obj
import org.rsmod.routefinder.loc.LocLayerConstants
import org.rsmod.map.CoordGrid

internal fun String.firemakingObjId(): Int = asRSCM(RSCMType.OBJ)

@Singleton
class FiremakingFires @Inject constructor(
    private val locs: LocRepository,
    private val objects: ObjRegistry,
    private val clock: MapClock,
) {
    private val fires = mutableMapOf<CoordGrid, Fire>()

    fun contains(player: Player, log: Obj): Boolean =
        log.count == 1 && log.isVisibleTo(player) && objects.findAll(log.coords).any { it === log }

    fun light(player: Player, log: Obj, fire: String, duration: Int): Boolean {
        if (!contains(player, log) || !objects.del(log).isSuccess()) return false
        val loc = LocInfo(
            LocLayerConstants.GROUND, log.coords,
            LocEntity(fire.asRSCM(RSCMType.LOC), LocShape.CentrepieceStraight.id, LocAngle.West.id),
        )
        if (spawn(loc, duration, campfire = false)) return true
        check(objects.add(log).isSuccess()) { "Could not restore unburned log at ${log.coords}" }
        return false
    }

    fun convert(fire: BoundLocInfo, campfire: String, duration: Int): Boolean {
        val current = managed(fire) ?: return false
        if (current.campfire) return false
        val replacement = LocInfo(
            fire.layer, fire.coords,
            LocEntity(campfire.asRSCM(RSCMType.LOC), fire.shape.id, fire.angle.id),
        )
        return spawn(replacement, duration, campfire = true)
    }

    fun remaining(campfire: BoundLocInfo): Int =
        managed(campfire)?.takeIf { it.campfire }?.let { (it.expiry - clock.cycle).coerceAtLeast(0) } ?: 0

    fun extend(campfire: BoundLocInfo, ticks: Int): Boolean {
        val current = managed(campfire)?.takeIf { it.campfire } ?: return false
        val duration = ((current.expiry - clock.cycle).toLong() + ticks).coerceIn(1, 300).toInt()
        return spawn(current.loc, duration, campfire = true, identity = current.identity)
    }

    fun identity(campfire: BoundLocInfo): Any? =
        managed(campfire)?.takeIf { it.campfire }?.identity

    private fun managed(bound: BoundLocInfo): Fire? {
        val fire = fires[bound.coords] ?: return null
        if (fire.expiry <= clock.cycle || fire.loc.entity != bound.entity) return null
        val current = locs.findExact(bound.coords, bound.shape) ?: return null
        return fire.takeIf { current.entity == fire.loc.entity }
    }

    private fun spawn(loc: LocInfo, duration: Int, campfire: Boolean, identity: Any = Any()): Boolean {
        require(duration > 0)
        val fire = Fire(loc, clock + duration, campfire, identity)
        if (!locs.add(loc, duration) {
                // A replaced fire must never expire its successor or produce a second ash pile.
                if (fires[loc.coords] === fire) {
                    fires.remove(loc.coords)
                    objects.add(Obj.fromServer(clock, loc.coords, "obj.ashes", 1))
                }
            }) return false
        fires.entries.removeIf { it.value.expiry < clock.cycle }
        fires[loc.coords] = fire
        return true
    }

    private class Fire(val loc: LocInfo, val expiry: Int, val campfire: Boolean, val identity: Any)
}
