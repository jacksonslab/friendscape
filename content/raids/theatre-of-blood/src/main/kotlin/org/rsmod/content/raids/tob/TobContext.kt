package org.rsmod.content.raids.tob

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Singleton
internal class TobContext
@Inject
constructor(
    val random: GameRandom,
    val npcRepo: NpcRepository,
    val locRepo: LocRepository,
    val worldRepo: WorldRepository,
    val playerList: PlayerList,
    val clock: MapClock,
    val collision: CollisionFlagMap,
    private val worldQueues: WorldQueueList,
    private val playerHitModifier: PlayerHitModifier,
    private val npcHitModifier: NpcHitModifier,
) {
    private val rayCast = RayCastValidator(collision)

    val cycle: Int
        get() = clock.cycle

    fun spawnNpc(type: String, coords: CoordGrid, hpFactor: Double = 1.0): Npc? {
        val npcType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return null
        val npc = Npc(npcType, coords)
        npc.mode = NpcMode.None
        npc.respawns = false
        npc.ignoreCombatInteractions = true
        npcRepo.add(npc, Int.MAX_VALUE)
        if (hpFactor != 1.0) {
            val hp = TobScaling.scaledHitpoints(npcType.hitpoints, hpFactor)
            npc.baseHitpointsLvl = hp
            npc.hitpoints = hp
        }
        return npc
    }

    fun removeNpc(npc: Npc) {
        if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
    }

    fun transmog(npc: Npc, type: String) {
        val npcType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return
        npc.transmog(npcType, Int.MAX_VALUE)
    }

    fun addLoc(coords: CoordGrid, loc: String, angle: Int = 0, shape: LocShape = LocShape.CentrepieceStraight): LocInfo =
        locRepo.add(coords, loc, Int.MAX_VALUE, LocAngle[angle], shape)

    fun removeLoc(loc: LocInfo) {
        locRepo.del(loc, Int.MAX_VALUE)
    }

    fun spotanim(coords: CoordGrid, spot: String, height: Int = 0, delay: Int = 0) {
        worldRepo.spotanimMap(SpotanimType(spot.asRSCM(RSCMType.SPOTANIM)), coords, height, delay)
    }

    /**
     * Sends [spot] from [from] (an npc's centre tile when given an npc) to [to] and returns the
     * number of server ticks until it lands.
     */
    fun projectile(
        spot: String,
        from: CoordGrid,
        to: CoordGrid,
        targetPlayer: Player? = null,
        sourceNpc: Npc? = null,
        startHeight: Int = 80,
        endHeight: Int = 30,
        delay: Int = 30,
        stepCycles: Int = 5,
        curve: Int = 16,
    ): Int {
        val distance = max(1, from.chebyshevDistance(to))
        val endTime = delay + stepCycles * distance + 10
        val proj =
            ProjAnim(
                spotanim = spot.asRSCM(RSCMType.SPOTANIM),
                startHeight = startHeight,
                endHeight = endHeight,
                startTime = delay,
                endTime = endTime,
                angle = curve,
                progress = 0,
                sourceIndex = sourceNpc?.let { it.slotId + 1 } ?: 0,
                targetIndex = targetPlayer?.let { -(it.slotId + 1) } ?: 0,
                startCoord = from,
                endCoord = targetPlayer?.coords ?: to,
            )
        worldRepo.projAnim(proj)
        return proj.serverCycles
    }

    fun hitPlayer(source: Npc?, target: Player, type: HitType, damage: Int, delay: Int = 1, penetration: Int = 0) {
        if (damage < 0) return
        if (source != null) {
            target.queueCombatRetaliate(source)
            target.queueHit(source, max(1, delay), type, damage, playerHitModifier, penetration = penetration)
        } else {
            target.queueHit(max(1, delay), type, damage, playerHitModifier)
        }
    }

    fun hitNpc(target: Npc, damage: Int, delay: Int = 1) {
        if (damage <= 0 || target.hitpoints <= 0) return
        target.queueHit(max(1, delay), HitType.Typeless, damage, npcHitModifier)
    }

    fun lineOfSight(from: CoordGrid, size: Int, to: CoordGrid): Boolean {
        if (from.level != to.level) return false
        return rayCast.hasLineOfSight(
            from,
            to,
            srcWidth = size,
            srcLength = size,
            extraFlag = CollisionFlag.BLOCK_PLAYERS,
        )
    }

    fun walkable(coords: CoordGrid): Boolean = !collision.isWalkBlocked(coords)

    fun roll(range: IntRange): Int = random.of(range)

    fun chance(numerator: Int, denominator: Int): Boolean = random.of(denominator) < numerator

    fun teleport(player: Player, dest: CoordGrid) {
        player.clearInteraction()
        player.abortRoute()
        PathingEntityCommon.telejump(player, collision, dest)
    }

    fun worldQueue(ticks: Int, action: () -> Unit) = worldQueues.add(max(1, ticks), action)

    fun <T> shuffled(list: List<T>): List<T> {
        val copy = list.toMutableList()
        for (i in copy.indices.reversed()) {
            val j = random.of(i + 1)
            val swap = copy[i]
            copy[i] = copy[j]
            copy[j] = swap
        }
        return copy
    }
}
