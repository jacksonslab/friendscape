package org.rsmod.content.raids.tob.rooms

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
import org.rsmod.map.CoordGrid

/**
 * Nylocas Vasilias. Thirty-one waves of Nylocas pour in from the west, south and east lanes; each
 * only takes damage from its own style, smalls chew on the four supports, bigs split in two when
 * killed and anything left alive too long explodes. Once the room is clear the Vasilias drops in
 * and changes carapace every 10 ticks; hitting it with the wrong style hurts you and heals it.
 */
internal class NylocasRoom(raid: TobRaid, ctx: TobContext) : TobRoom(raid, ctx, 2) {
    override val displayName = "The Nylocas"
    override val entry = CoordGrid(3295, 4280, 0)
    override val arena = TobArea(3290, 4243, 3301, 4254)
    override val barriers = listOf(TobBarrier(vertical = false, line = 4255, insideStep = -1))
    override val passageTiles = setOf(CoordGrid(3303, 4275), CoordGrid(3303, 4276))

    enum class Style(val hitType: HitType, val small: String, val big: String, val boss: String, val seq: String) {
        Melee(
            HitType.Melee,
            "npc.tob_nylocas_fighting_melee",
            "npc.tob_nylocas_big_fighting_melee",
            "npc.nylocas_boss_melee",
            "top_spider_melee",
        ),
        Ranged(
            HitType.Ranged,
            "npc.tob_nylocas_fighting_ranged",
            "npc.tob_nylocas_big_fighting_ranged",
            "npc.nylocas_boss_ranged",
            "top_spider_ranged",
        ),
        Magic(
            HitType.Magic,
            "npc.tob_nylocas_fighting_magic",
            "npc.tob_nylocas_big_fighting_magic",
            "npc.nylocas_boss_magic",
            "top_spider_magic",
        ),
    }

    private class Nylo(val npc: Npc, val style: Style, val big: Boolean, val born: Int) {
        var nextAttack = 0
        var exploded = false
    }

    private val nylos = mutableListOf<Nylo>()
    private val pillars = mutableListOf<Npc>()
    private var wave = 0
    private var nextWave = 0
    private var boss: Npc? = null
    private var bossStyle = Style.Melee
    private var bossActiveAt = -1
    private var nextSwitch = 0
    private var nextBossAttack = 0

    override fun bossNpc(): Npc? = boss

    override fun onPrepare() {
        for (pillar in PILLARS) spawn(PILLAR, pillar, scaled = false)?.let(pillars::add)
    }

    override fun onStart() {
        nextWave = tick + FIRST_WAVE_DELAY
    }

    override fun onTick() {
        if (boss == null) {
            if (wave < WAVES && tick >= nextWave) spawnWave()
            if (wave >= WAVES && nylos.isEmpty()) spawnBoss()
        } else {
            tickBoss()
        }
        tickNylos()
    }

    private fun spawnWave() {
        if (nylos.size >= STALL_LIMIT) {
            nextWave = tick + 1
            return
        }
        wave++
        nextWave = tick + WAVE_INTERVAL
        val bigWave = wave % BIG_WAVE_EVERY == 0
        for (lane in LANES) {
            val count = if (bigWave) 1 else ctx.roll(1..2)
            repeat(count) { addNylo(ctx.random.pick(Style.entries), big = bigWave, at = lane) }
        }
    }

    private fun addNylo(style: Style, big: Boolean, at: CoordGrid) {
        val npc = spawn(if (big) style.big else style.small, at) ?: return
        nylos += Nylo(npc, style, big, tick).also { it.nextAttack = tick + ctx.roll(2..4) }
    }

    private fun tickNylos() {
        nylos.removeAll { !it.npc.isSlotAssigned || it.npc.hitpoints <= 0 }
        pillars.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        for (nylo in nylos.toList()) {
            if (tick - nylo.born >= EXPLODE_AFTER) {
                explode(nylo)
                continue
            }
            val pillar = if (nylo.big) null else pillars.minByOrNull { it.distanceTo(nylo.npc) }
            val player = TobMoves.closest(nylo.npc, targets())
            val inRange = { distance: Int -> if (nylo.style == Style.Melee) distance <= 1 else distance <= RANGE }
            when {
                pillar != null && (player == null || pillar.distanceTo(nylo.npc) <= player.distanceTo(nylo.npc)) -> {
                    if (inRange(pillar.distanceTo(nylo.npc))) attackPillar(nylo, pillar) else follow(nylo, pillar.coords)
                }
                player != null -> {
                    if (inRange(player.distanceTo(nylo.npc))) attackPlayer(nylo, player) else follow(nylo, player.coords)
                }
            }
        }
    }

    private fun follow(nylo: Nylo, target: CoordGrid) {
        nylo.npc.walk(target)
    }

    private fun attackPillar(nylo: Nylo, pillar: Npc) {
        if (tick < nylo.nextAttack) return
        nylo.nextAttack = tick + ATTACK_RATE
        nylo.npc.faceSquare(pillar.coords)
        nylo.npc.anim("seq.${nylo.style.seq}_attack")
        ctx.hitNpc(pillar, ctx.roll(PILLAR_DAMAGE))
    }

    private fun attackPlayer(nylo: Nylo, player: Player) {
        if (tick < nylo.nextAttack) return
        nylo.nextAttack = tick + ATTACK_RATE
        nylo.npc.faceSquare(player.coords)
        nylo.npc.anim("seq.${nylo.style.seq}_attack")
        val delay =
            if (nylo.style == Style.Melee) {
                1
            } else {
                ctx.projectile(projectile(nylo.big), TobMoves.centre(nylo.npc), player.coords, player, nylo.npc)
            }
        val max = if (nylo.big) BIG_MAX else SMALL_MAX
        ctx.hitPlayer(nylo.npc, player, nylo.style.hitType, ctx.roll(0..max), delay)
    }

    private fun projectile(big: Boolean) =
        if (big) "spotanim.tob_nylocas_rangedprojectile_size2" else "spotanim.tob_nylocas_rangedprojectile_size1"

    private fun explode(nylo: Nylo) {
        nylo.exploded = true
        nylos.remove(nylo)
        ctx.spotanim(nylo.npc.coords, "spotanim.tob_nylocas_death_${nylo.style.name.lowercase()}_detonate")
        for (player in targets()) {
            if (nylo.npc.distanceTo(player) <= 1) {
                ctx.hitPlayer(null, player, HitType.Typeless, ctx.roll(EXPLODE_DAMAGE))
            }
        }
        despawn(nylo.npc)
    }

    private fun spawnBoss() {
        val npc = spawn(BOSS_SPAWNING, BOSS_TILE) ?: return
        boss = npc
        bossActiveAt = tick + BOSS_DROP_TICKS
        bossStyle = Style.Melee
        message("<col=ff0000>The Nylocas Vasilias emerges!</col>")
    }

    private fun tickBoss() {
        val npc = boss ?: return
        if (npc.hitpoints <= 0 || tick < bossActiveAt) return
        if (tick == bossActiveAt) {
            ctx.transmog(npc, bossStyle.boss)
            nextSwitch = tick + SWITCH_TICKS
            nextBossAttack = tick + 2
        }
        if (tick >= nextSwitch) {
            bossStyle = ctx.random.pick(Style.entries.filter { it != bossStyle })
            ctx.transmog(npc, bossStyle.boss)
            nextSwitch = tick + SWITCH_TICKS
        }
        val target = TobMoves.closest(npc, targets()) ?: return
        if (bossStyle == Style.Melee && npc.distanceTo(target) > 1) {
            npc.walk(target.coords)
            return
        }
        if (tick < nextBossAttack) return
        nextBossAttack = tick + BOSS_ATTACK_RATE
        npc.faceSquare(target.coords)
        npc.anim("seq.${bossStyle.seq}_attack")
        val delay =
            if (bossStyle == Style.Melee) 1 else ctx.projectile(projectile(true), TobMoves.centre(npc), target.coords, target, npc)
        ctx.hitPlayer(npc, target, bossStyle.hitType, ctx.roll(0..BOSS_MAX), delay)
    }

    override fun modifyHit(event: NpcHitEvents.Modify, attacker: Player?) {
        if (attacker == null) return
        val npc = event.npc
        if (npc === boss) {
            if (tick < bossActiveAt) {
                event.hit.damage = 0
                return
            }
            if (event.hit.type != bossStyle.hitType) {
                val reflected = event.hit.damage
                event.hit.damage = 0
                if (reflected > 0) {
                    ctx.hitPlayer(npc, attacker, HitType.Typeless, reflected)
                    npc.heal(reflected, showHitsplat = true)
                }
            }
            return
        }
        val nylo = nylos.firstOrNull { it.npc === npc } ?: return
        if (event.hit.type != nylo.style.hitType) event.hit.damage = 0
    }

    override fun onNpcDeath(npc: Npc): Pair<String, Int>? {
        if (npc === boss) {
            complete()
            return "seq.${bossStyle.seq}_death" to DEATH_TICKS
        }
        if (npc in pillars) {
            collapse(npc)
            return null
        }
        val nylo = nylos.firstOrNull { it.npc === npc } ?: return null
        nylos.remove(nylo)
        if (nylo.big) {
            repeat(SPLIT) { addNylo(nylo.style, big = false, at = staticOf(npc.coords)) }
        }
        return "seq.${nylo.style.seq}_death" to 2
    }

    private fun collapse(pillar: Npc) {
        pillars.remove(pillar)
        ctx.spotanim(TobMoves.centre(pillar), "spotanim.tob_nylocas_death_melee_detonate")
        for (player in targets()) {
            if (pillar.distanceTo(player) <= 1) {
                ctx.hitPlayer(null, player, HitType.Typeless, ctx.roll(COLLAPSE_DAMAGE))
            }
        }
        message("<col=ff0000>A pillar collapses!</col>")
    }

    override fun onComplete() {
        nylos.toList().forEach { despawn(it.npc) }
        nylos.clear()
    }

    private companion object {
        const val PILLAR = "npc.tob_nylocas_support"
        const val BOSS_SPAWNING = "npc.nylocas_boss_spawning"
        val PILLARS =
            listOf(CoordGrid(3289, 4242), CoordGrid(3289, 4252), CoordGrid(3299, 4242), CoordGrid(3299, 4252))
        val LANES = listOf(CoordGrid(3290, 4248), CoordGrid(3295, 4243), CoordGrid(3301, 4248))
        val BOSS_TILE = CoordGrid(3294, 4247)

        const val WAVES = 31
        const val FIRST_WAVE_DELAY = 6
        const val WAVE_INTERVAL = 4
        const val BIG_WAVE_EVERY = 4
        const val STALL_LIMIT = 12
        const val EXPLODE_AFTER = 52
        const val ATTACK_RATE = 4
        const val RANGE = 7
        const val SMALL_MAX = 8
        const val BIG_MAX = 16
        val PILLAR_DAMAGE = 1..4
        val EXPLODE_DAMAGE = 10..25
        val COLLAPSE_DAMAGE = 30..55
        const val SPLIT = 2
        const val BOSS_DROP_TICKS = 4
        const val SWITCH_TICKS = 10
        const val BOSS_ATTACK_RATE = 4
        const val BOSS_MAX = 24
        const val DEATH_TICKS = 4
    }
}
