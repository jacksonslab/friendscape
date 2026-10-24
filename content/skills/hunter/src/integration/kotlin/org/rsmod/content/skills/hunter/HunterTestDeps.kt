package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.ObjInteractions
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.traps.HunterTraps
import org.rsmod.content.skills.hunter.traps.Trap
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

class HunterTestDeps
@Inject
constructor(
    val traps: HunterTraps,
    val held: HeldInteractions,
    val objInteractions: ObjInteractions,
    val objRepo: ObjRepository,
    val objRegistry: ObjRegistry,
    val collision: CollisionFlagMap,
)

val TRAP_TILE = CoordGrid(0, 50, 50, 36, 36)

const val SNARE = "obj.hunting_ojibway_bird_snare"
const val BOX_TRAP = "obj.hunting_box_trap"

fun obj(internal: String): ItemServerType = ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))!!

fun npcType(internal: String): NpcServerType = ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))!!

fun GameTestScope.preparePlayer(
    deps: HunterTestDeps,
    hunterLevel: Int,
    coords: CoordGrid = TRAP_TILE,
): Player {
    allocZoneCollision(coords)
    deps.clearCollision(coords)
    player.teleport(coords)
    player.clearInv()
    player.observerUUID = player.uuid
    player.setBaseLevel("stat.hunter", hunterLevel)
    player.setCurrentLevel("stat.hunter", hunterLevel)
    return player
}

fun GameTestScope.opHeld1(deps: HunterTestDeps, slot: Int, target: Player = player) {
    target.withProtectedAccess { deps.held.interact(this, inv, slot, HeldOp.Op1) }
}

fun GameTestScope.layFromSlot(deps: HunterTestDeps, slot: Int): Trap {
    val coords = player.coords
    opHeld1(deps, slot)
    advance(ticks = 5)
    return checkNotNull(deps.traps[coords]) { "No trap laid at $coords" }
}

fun Trap.bound(): BoundLocInfo {
    val type = ServerCacheManager.getObject(loc.id)!!
    return BoundLocInfo(loc, type)
}

fun HunterTestDeps.groundCount(coords: CoordGrid, internal: String): Int {
    val id = internal.asRSCM(RSCMType.OBJ)
    return objRegistry.findAll(coords).filter { it.type == id }.sumOf { it.count }
}

fun GameTestScope.expectMessage(text: String, target: Player = player, timeoutTicks: Int = 8) {
    fun sent() = runCatching { assertMessageSent(text, target) }.isSuccess
    if (sent()) {
        return
    }
    advanceUntil(::sent, timeoutTicks) { "Message not sent within $timeoutTicks ticks: $text" }
}

fun HunterTestDeps.clearCollision(center: CoordGrid, radius: Int = 3) {
    for (x in center.x - radius..center.x + radius) {
        for (z in center.z - radius..center.z + radius) {
            collision[x, z, center.level] = 0
        }
    }
}
