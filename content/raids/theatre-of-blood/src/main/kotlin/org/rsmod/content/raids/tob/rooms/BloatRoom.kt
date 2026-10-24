package org.rsmod.content.raids.tob.rooms

import kotlin.math.max
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.content.raids.tob.TobArea
import org.rsmod.content.raids.tob.TobBarrier
import org.rsmod.content.raids.tob.TobContext
import org.rsmod.content.raids.tob.TobRaid
import org.rsmod.content.raids.tob.TobRoom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid

/**
 * The Pestilent Bloat walks laps around its chamber, taking half damage and sending flies at
 * anyone in its line of sight. Every so often it shuts down for 32 ticks, then stomps the raiders
 * around it as it wakes. Below 90% hitpoints mutilated flesh falls from the ceiling while it walks.
 */
internal class BloatRoom(raid: TobRaid, ctx: TobContext) : TobRoom(raid, ctx, 1) {
    override val displayName = "The Pestilent Bloat"
    override val entry = CoordGrid(3322, 4447, 0)
    override val arena = TobArea(3288, 4440, 3303, 4455)
    override val barriers =
        listOf(
            TobBarrier(vertical = true, line = 3304, insideStep = -1),
            TobBarrier(vertical = true, line = 3287, insideStep = 1),
        )
    override val passageTiles = setOf(CoordGrid(3269, 4447), CoordGrid(3269, 4448))

    private var bloat: Npc? = null
    private var waypoint = 0
    private var asleep = false
    private var phaseEnds = 0
    private var firstWalk = true
    private var nextFlesh = 0
    private var attacksTaken = 0

    override fun bossNpc(): Npc? = bloat

    override fun onPrepare() {
        bloat = spawn(BLOAT, LAP[0])
    }

    override fun onStart() {
        startWalking()
    }

    override fun onTick() {
        val boss = bloat ?: return
        if (boss.hitpoints <= 0) return
        if (asleep) {
            if (tick >= phaseEnds) wake(boss)
            return
        }
        walk(boss)
        flies(boss)
        flesh(boss)
        if (tick >= phaseEnds) sleep(boss)
    }

    private fun startWalking() {
        asleep = false
        val duration = if (firstWalk) FIRST_WALK else WALK
        firstWalk = false
        phaseEnds = tick + ctx.roll(duration)
        bloat?.clearIdleAnim()
    }

    private fun walk(boss: Npc) {
        val target = at(LAP[waypoint])
        if (boss.coords == target) {
            waypoint = (waypoint + 1) % LAP.size
        }
        boss.defaultMoveSpeed = speed(boss)
        boss.walk(at(LAP[waypoint]))
    }

    private fun speed(boss: Npc): MoveSpeed {
        val percent = boss.hitpoints * 100 / max(1, boss.baseHitpointsLvl)
        return when {
            percent > 60 -> MoveSpeed.Walk
            percent > 40 -> MoveSpeed.Run
            attacksTaken % 2 == 0 -> MoveSpeed.Walk
            else -> MoveSpeed.Run
        }
    }

    private fun flies(boss: Npc) {
        for (player in targets()) {
            if (!ctx.lineOfSight(boss.coords, boss.size, player.coords)) continue
            player.spotanim("spotanim.tob_bloat_flies_small")
            ctx.hitPlayer(boss, player, HitType.Ranged, ctx.roll(FLIES_DAMAGE), penetration = FLIES_PENETRATION)
        }
    }

    private fun flesh(boss: Npc) {
        if (boss.hitpoints * 10 > boss.baseHitpointsLvl * 9 || tick < nextFlesh) return
        nextFlesh = tick + FLESH_RATE
        val tiles = ctx.shuffled(arena.tiles()).take(ctx.roll(FLESH_COUNT))
        for (static in tiles) {
            val tile = at(static)
            ctx.spotanim(tile, ctx.random.pick(FLESH_SPOTS))
            ctx.worldQueue(FLESH_DELAY) {
                if (!isActive) return@worldQueue
                for (player in targets()) {
                    if (player.coords != tile) continue
                    ctx.hitPlayer(null, player, HitType.Typeless, ctx.roll(FLESH_DAMAGE))
                    player.spotanim("spotanim.tob_bloat_stunned")
                    CombatEffects.freeze(player, FLESH_STUN)
                }
            }
        }
    }

    private fun sleep(boss: Npc) {
        asleep = true
        phaseEnds = tick + SLEEP_TICKS
        boss.abortRoute()
        boss.anim("seq.tob_bloat_sleep")
        boss.setIdleAnim("seq.tob_bloat_sleep")
    }

    private fun wake(boss: Npc) {
        boss.clearIdleAnim()
        boss.anim("seq.tob_bloat_ready")
        for (player in targets()) {
            if (boss.distanceTo(player) > 1) continue
            ctx.hitPlayer(boss, player, HitType.Typeless, ctx.roll(STOMP_DAMAGE))
        }
        if (boss.defenceLvl < boss.baseDefenceLvl) boss.defenceLvl = boss.baseDefenceLvl
        startWalking()
    }

    override fun modifyHit(event: NpcHitEvents.Modify, attacker: Player?) {
        if (event.npc !== bloat || attacker == null) return
        attacksTaken++
        if (!asleep) event.hit.damage /= 2
    }

    override fun onNpcDeath(npc: Npc): Pair<String, Int>? {
        if (npc !== bloat) return null
        npc.clearIdleAnim()
        complete()
        return "seq.tob_bloat_death" to DEATH_TICKS
    }

    private companion object {
        const val BLOAT = "npc.tob_bloat"
        val LAP =
            listOf(CoordGrid(3288, 4440), CoordGrid(3299, 4440), CoordGrid(3299, 4451), CoordGrid(3288, 4451))
        val FIRST_WALK = 39..47
        val WALK = 34..42
        const val SLEEP_TICKS = 32
        val STOMP_DAMAGE = 40..80
        val FLIES_DAMAGE = 10..20
        const val FLIES_PENETRATION = 75
        const val FLESH_RATE = 4
        val FLESH_COUNT = 3..6
        const val FLESH_DELAY = 3
        val FLESH_DAMAGE = 30..50
        const val FLESH_STUN = 2
        const val DEATH_TICKS = 6
        val FLESH_SPOTS =
            listOf(
                "spotanim.tob_bloat_falling_flesh1",
                "spotanim.tob_bloat_falling_flesh2",
                "spotanim.tob_bloat_falling_flesh3",
                "spotanim.tob_bloat_falling_flesh4",
            )
    }
}
