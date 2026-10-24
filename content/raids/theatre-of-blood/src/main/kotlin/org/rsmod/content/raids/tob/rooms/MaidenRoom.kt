package org.rsmod.content.raids.tob.rooms

import kotlin.math.max
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.npc.heal
import org.rsmod.api.player.stat.statSub
import org.rsmod.content.raids.tob.TobArea
import org.rsmod.content.raids.tob.TobBarrier
import org.rsmod.content.raids.tob.TobContext
import org.rsmod.content.raids.tob.TobRaid
import org.rsmod.content.raids.tob.TobRoom
import org.rsmod.content.raids.tob.TobScaling
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/**
 * The Maiden of Sugadinti. She alternates a magic attack on the closest raider with a volley of
 * blood splats (three at whoever stands furthest away), every splat that lands heals her, and some
 * become blood spawns that trail healing blood. At 70/50/30% she summons Nylocas Matomenos, which
 * walk to her and heal her for twice their remaining hitpoints unless they're frozen and killed.
 */
internal class MaidenRoom(raid: TobRaid, ctx: TobContext) : TobRoom(raid, ctx, 0) {
    override val displayName = "The Maiden of Sugadinti"
    override val entry = ENTRY
    override val arena = TobArea(3160, 4436, 3184, 4457)
    override val barriers = listOf(TobBarrier(vertical = true, line = 3185, insideStep = -1))
    override val passageTiles = setOf(CoordGrid(3176, 4422), CoordGrid(3177, 4422))

    private var maiden: Npc? = null
    private var nextAttack = 0
    private var throwNext = false
    private var summonStage = 0
    private var bonusDamage = 0
    private val matomenos = mutableListOf<Npc>()
    private val spawnedAt = HashMap<Npc, Int>()
    private val spawns = mutableListOf<Npc>()
    private val pools = HashMap<CoordGrid, Pair<LocInfo, Int>>()

    override fun bossNpc(): Npc? = maiden

    override fun onPrepare() {
        maiden = spawn(MAIDEN, CoordGrid(3162, 4444))?.also { it.faceSquare(at(3175, 4446)) }
    }

    override fun onStart() {
        nextAttack = tick + ATTACK_RATE
        maiden?.anim("seq.maiden_spawn")
    }

    override fun onTick() {
        val boss = maiden ?: return
        if (boss.hitpoints <= 0) return
        checkSummons(boss)
        tickMatomenos(boss)
        tickSpawns()
        tickPools(boss)
        if (tick >= nextAttack) {
            nextAttack = tick + ATTACK_RATE
            if (throwNext) bloodThrow(boss) else magicAttack(boss)
            throwNext = !throwNext
        }
    }

    private fun magicAttack(boss: Npc) {
        val target = TobMoves.closest(boss, targets()) ?: return
        boss.anim("seq.maiden_attack_special")
        boss.faceSquare(target.coords)
        val landing = ctx.projectile("spotanim.maiden_shadow_proj", TobMoves.centre(boss), target.coords, target, boss)
        val damage = ctx.roll(0..MAGIC_MAX + bonusDamage)
        ctx.hitPlayer(boss, target, HitType.Magic, damage, landing)
        if (damage > 0 && ctx.chance(1, 2)) {
            target.statSub(drainedStat(target), constant = (damage + 1) / 5, percent = 0)
        }
    }

    private fun drainedStat(player: Player): String =
        ctx.random.pick(listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic"))

    private fun bloodThrow(boss: Npc) {
        val players = targets()
        if (players.isEmpty()) return
        boss.anim("seq.maiden_attack_blood")
        val furthest = TobMoves.furthest(boss, players)
        val tiles = mutableListOf<CoordGrid>()
        for (player in players) {
            tiles += player.coords
            if (player === furthest) {
                repeat(EXTRA_SPLATS) { tiles += ctx.random.of(player.coords, 1) }
            }
        }
        val origin = TobMoves.centre(boss)
        for (tile in tiles) {
            val landing = ctx.projectile("spotanim.maiden_blood_proj", origin, tile, sourceNpc = boss, stepCycles = 6)
            ctx.worldQueue(landing) { landSplat(boss, tile) }
        }
    }

    private fun landSplat(boss: Npc, tile: CoordGrid) {
        if (!isActive || boss.hitpoints <= 0) return
        ctx.spotanim(tile, "spotanim.tob_bloat_blood_splat")
        for (player in targets()) {
            if (player.coords != tile) continue
            val damage = ctx.roll(SPLAT_DAMAGE)
            ctx.hitPlayer(boss, player, HitType.Typeless, damage)
            player.statSub("stat.prayer", constant = damage / 2, percent = 0)
            boss.heal(damage, showHitsplat = true)
        }
        placePool(tile)
        if (spawns.size < MAX_SPAWNS && ctx.chance(1, 3)) {
            spawn(BLOOD_SPAWN, staticOf(tile))?.let(spawns::add)
        }
    }

    private fun placePool(tile: CoordGrid) {
        if (!inArena(tile) || pools.containsKey(tile)) return
        val loc = addLoc(staticOf(tile), "loc.tob_maiden_blood")
        pools[tile] = loc to (tick + POOL_TICKS)
    }

    private fun tickPools(boss: Npc) {
        val expired = pools.filterValues { (_, until) -> tick >= until }
        for ((tile, pool) in expired) {
            removeLoc(pool.first)
            pools.remove(tile)
        }
        if (pools.isEmpty()) return
        for (player in targets()) {
            if (!pools.containsKey(player.coords)) continue
            val damage = ctx.roll(POOL_DAMAGE)
            ctx.hitPlayer(null, player, HitType.Typeless, damage)
            boss.heal(damage)
        }
    }

    private fun tickSpawns() {
        spawns.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        for (spawn in spawns) {
            if (tick % 2 == 0) {
                val dest = ctx.random.of(spawn.coords, 3)
                if (inArena(dest)) TobMoves.stepToward(ctx, spawn, dest)
            }
            placePool(spawn.coords)
        }
    }

    private fun checkSummons(boss: Npc) {
        val percent = boss.hitpoints * 100 / max(1, boss.baseHitpointsLvl)
        val stage =
            when {
                percent <= 30 -> 3
                percent <= 50 -> 2
                percent <= 70 -> 1
                else -> 0
            }
        while (summonStage < stage) {
            summonStage++
            ctx.transmog(boss, TRANSMOGS[summonStage])
            summonMatomenos()
        }
    }

    private fun summonMatomenos() {
        val count = TobScaling.matomenosCount(targets().size)
        val points = ctx.shuffled(SPAWN_POINTS).take(count)
        for (point in points) {
            val npc = spawn(MATOMENOS, point) ?: continue
            spawnedAt[npc] = tick
            matomenos += npc
        }
    }

    private fun tickMatomenos(boss: Npc) {
        matomenos.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        val reached = mutableListOf<Npc>()
        for (npc in matomenos) {
            if (tick - (spawnedAt[npc] ?: 0) < MATOMENOS_DELAY || CombatEffects.isFrozen(npc)) continue
            if (npc.distanceTo(boss) <= 1) {
                reached += npc
                continue
            }
            if (tick % 2 == 0) TobMoves.stepToward(ctx, npc, TobMoves.centre(boss))
        }
        for (npc in reached) {
            boss.heal(npc.hitpoints * 2, showHitsplat = true)
            bonusDamage += MATOMENOS_BONUS
            matomenos.remove(npc)
            spawnedAt.remove(npc)
            despawn(npc)
        }
    }

    override fun onNpcDeath(npc: Npc): Pair<String, Int>? {
        if (npc === maiden) {
            ctx.transmog(npc, "npc.tob_maiden_dying_a")
            matomenos.toList().forEach(::despawn)
            spawns.toList().forEach(::despawn)
            pools.values.forEach { removeLoc(it.first) }
            pools.clear()
            complete()
            return "seq.maiden_death_a" to DEATH_TICKS
        }
        if (npc.isType(BLOOD_SPAWN)) return "seq.tob_blood_spawn_death" to 2
        return null
    }

    override fun onRaiderDeath(player: Player) = Unit

    companion object {
        val ENTRY = CoordGrid(3219, 4456, 0)

        private const val MAIDEN = "npc.tob_maiden_100"
        private const val MATOMENOS = "npc.maiden_elemental"
        private const val BLOOD_SPAWN = "npc.maiden_blood_slug"
        private val TRANSMOGS =
            listOf("npc.tob_maiden_100", "npc.tob_maiden_70", "npc.tob_maiden_50", "npc.tob_maiden_30")

        private const val ATTACK_RATE = 10
        private const val MAGIC_MAX = 18
        private const val MATOMENOS_BONUS = 3
        private const val EXTRA_SPLATS = 2
        private val SPLAT_DAMAGE = 10..25
        private val POOL_DAMAGE = 3..6
        private const val POOL_TICKS = 12
        private const val MAX_SPAWNS = 8
        private const val MATOMENOS_DELAY = 4
        private const val DEATH_TICKS = 5

        private val SPAWN_POINTS =
            listOf(3170, 3173, 3176, 3179, 3182).flatMap { x ->
                listOf(CoordGrid(x, 4456), CoordGrid(x, 4436))
            }
    }
}
