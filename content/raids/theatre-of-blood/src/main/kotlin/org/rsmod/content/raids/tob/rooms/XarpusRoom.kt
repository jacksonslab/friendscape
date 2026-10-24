package org.rsmod.content.raids.tob.rooms

import kotlin.math.max
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.content.raids.tob.TobArea
import org.rsmod.content.raids.tob.TobBarrier
import org.rsmod.content.raids.tob.TobContext
import org.rsmod.content.raids.tob.TobRaid
import org.rsmod.content.raids.tob.TobRoom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/**
 * Xarpus. He opens weakened, feeding on exhumed remains that rise one at a time; a raider standing
 * on an exhumed stops him healing from it. Then he spits poison that leaves permanent acidic pools
 * and splashes the 3x3 around his target. At 25% he screeches and stares at one quadrant at a time,
 * punishing anyone who attacks him from the quadrant he faces. Every exhumed he absorbs makes the
 * poison hit harder.
 */
internal class XarpusRoom(raid: TobRaid, ctx: TobContext) : TobRoom(raid, ctx, 4) {
    override val displayName = "Xarpus"
    override val entry = CoordGrid(3170, 4376, 1)
    override val arena = TobArea(3163, 4380, 3177, 4394, 1)
    override val barriers =
        listOf(
            TobBarrier(vertical = false, line = 4379, insideStep = 1),
            TobBarrier(vertical = false, line = 4395, insideStep = -1),
        )
    override val passageTiles = setOf(CoordGrid(3169, 4400, 1))

    private enum class Phase {
        Feeding,
        Poison,
        Staring,
    }

    private var xarpus: Npc? = null
    private var phase = Phase.Feeding
    private var absorbed = 0
    private var exhumedSpawned = 0
    private var nextExhumed = 0
    private val exhumeds = mutableListOf<Pair<LocInfo, Int>>()
    private val pools = HashMap<CoordGrid, LocInfo>()
    private var nextSpit = 0
    private var facing = 0
    private var nextTurn = 0

    override fun bossNpc(): Npc? = xarpus

    override fun onPrepare() {
        xarpus = spawn(STATIC, FEEDING_TILE)
    }

    override fun onStart() {
        val npc = xarpus ?: return
        ctx.transmog(npc, FEEDING)
        npc.hitpoints = npc.baseHitpointsLvl * START_PERCENT / 100
        nextExhumed = tick + 2
    }

    override fun onTick() {
        val npc = xarpus ?: return
        if (npc.hitpoints <= 0) return
        when (phase) {
            Phase.Feeding -> tickFeeding(npc)
            Phase.Poison -> tickPoison(npc)
            Phase.Staring -> tickStaring(npc)
        }
        hurtStandingInPools(npc)
    }

    private fun tickFeeding(npc: Npc) {
        if (exhumedSpawned < EXHUMEDS && tick >= nextExhumed) {
            nextExhumed = tick + EXHUMED_INTERVAL
            exhumedSpawned++
            val tile = ctx.random.pick(arena.tiles().filter { !TobMoves.under(npc, at(it)) })
            exhumeds += addLoc(tile, "loc.tob_xarpus_exhumed") to (tick + EXHUMED_TICKS)
        }
        val occupied = targets().map { it.coords }.toSet()
        for ((loc, until) in exhumeds.toList()) {
            if (tick >= until) {
                exhumeds.remove(loc to until)
                removeLoc(loc)
                continue
            }
            if (loc.coords in occupied) continue
            if (tick % 2 == 0) {
                ctx.projectile("spotanim.tob_xarpus_exhumed_energyorb", loc.coords, TobMoves.centre(npc), startHeight = 0)
                npc.heal(EXHUMED_HEAL, showHitsplat = true)
                absorbed++
            }
        }
        if (exhumedSpawned >= EXHUMEDS && exhumeds.isEmpty()) beginCombat(npc)
    }

    private fun beginCombat(feeding: Npc) {
        val hp = feeding.hitpoints
        val max = feeding.baseHitpointsLvl
        despawn(feeding)
        val combat = spawn(COMBAT, COMBAT_TILE) ?: return
        combat.baseHitpointsLvl = max
        combat.hitpoints = hp
        xarpus = combat
        phase = Phase.Poison
        nextSpit = tick + SPIT_RATE
    }

    private fun tickPoison(npc: Npc) {
        if (npc.hitpoints * 100 <= npc.baseHitpointsLvl * STARE_PERCENT) {
            phase = Phase.Staring
            npc.say("Screeeeech!")
            message("<col=ff0000>Xarpus begins to stare intently.</col>")
            nextTurn = tick
            return
        }
        if (tick < nextSpit) return
        nextSpit = tick + SPIT_RATE
        val target = ctx.random.pick(targets().ifEmpty { return })
        val tile = target.coords
        npc.faceSquare(tile)
        npc.anim("seq.tob_xarpus_attack_ranged")
        val landing = ctx.projectile("spotanim.tob_xarpus_acidspit", TobMoves.centre(npc), tile, sourceNpc = npc)
        ctx.worldQueue(landing) {
            if (!isActive) return@worldQueue
            ctx.spotanim(tile, "spotanim.tob_xarpus_acidsplash")
            for (player in targets()) {
                if (player.coords.chebyshevDistance(tile) <= 1) {
                    ctx.hitPlayer(npc, player, HitType.Typeless, scaled(ctx.roll(SPLASH_DAMAGE)))
                }
            }
            if (!pools.containsKey(tile)) pools[tile] = addLoc(staticOf(tile), "loc.tob_xarpus_acidpool")
        }
    }

    private fun tickStaring(npc: Npc) {
        if (tick < nextTurn) return
        nextTurn = tick + TURN_TICKS
        facing = ctx.roll(0..3)
        val centre = TobMoves.centre(npc)
        val dx = if (facing == 0 || facing == 3) -6 else 6
        val dz = if (facing < 2) 6 else -6
        npc.faceSquare(centre.translate(dx, dz))
    }

    private fun quadrant(npc: Npc, coords: CoordGrid): Int {
        val centre = TobMoves.centre(npc)
        val west = coords.x < centre.x
        val north = coords.z >= centre.z
        return when {
            north && west -> 0
            north -> 1
            !west -> 2
            else -> 3
        }
    }

    private fun hurtStandingInPools(npc: Npc) {
        for (player in targets()) {
            if (pools.containsKey(player.coords)) {
                ctx.hitPlayer(null, player, HitType.Typeless, scaled(ctx.roll(POOL_DAMAGE)))
            } else if (phase != Phase.Feeding && TobMoves.under(npc, player.coords)) {
                ctx.hitPlayer(null, player, HitType.Typeless, ctx.roll(1..3))
            }
        }
    }

    private fun scaled(damage: Int): Int = damage + damage * absorbed / ABSORB_SCALE

    override fun modifyHit(event: NpcHitEvents.Modify, attacker: Player?) {
        if (event.npc !== xarpus) return
        if (phase == Phase.Feeding) {
            event.hit.damage = 0
            return
        }
        if (phase == Phase.Staring && attacker != null && quadrant(event.npc, attacker.coords) == facing) {
            val base = ctx.roll(STARE_DAMAGE)
            val damage = base + base * absorbed * 4 / (ABSORB_SCALE * 10)
            event.npc.anim("seq.tob_xarpus_attack_ranged")
            ctx.hitPlayer(event.npc, attacker, HitType.Typeless, damage)
        }
    }

    override fun onNpcDeath(npc: Npc): Pair<String, Int>? {
        if (npc !== xarpus) return null
        pools.values.forEach(::removeLoc)
        pools.clear()
        complete()
        return "seq.tob_xarpus_death_a" to DEATH_TICKS
    }

    private companion object {
        const val STATIC = "npc.tob_xarpus_static"
        const val FEEDING = "npc.tob_xarpus_feeding"
        const val COMBAT = "npc.tob_xarpus_combat"
        val FEEDING_TILE = CoordGrid(3169, 4386, 1)
        val COMBAT_TILE = CoordGrid(3168, 4385, 1)

        const val START_PERCENT = 75
        const val EXHUMEDS = 7
        const val EXHUMED_INTERVAL = 8
        const val EXHUMED_TICKS = 11
        const val EXHUMED_HEAL = 8
        const val ABSORB_SCALE = 20
        const val SPIT_RATE = 4
        val SPLASH_DAMAGE = 6..12
        val POOL_DAMAGE = 4..8
        const val STARE_PERCENT = 25
        const val TURN_TICKS = 8
        val STARE_DAMAGE = 50..75
        const val DEATH_TICKS = 6
    }
}
