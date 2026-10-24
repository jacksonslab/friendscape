package org.rsmod.content.raids.tob.rooms

import kotlin.math.max
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.content.raids.tob.TobArea
import org.rsmod.content.raids.tob.TobContext
import org.rsmod.content.raids.tob.TobRaid
import org.rsmod.content.raids.tob.TobRoom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/**
 * Verzik Vitur, started by talking to her on her throne.
 *
 * Phase 1: every 14 ticks she blasts each raider who isn't hidden behind a pillar; a pillar that
 * shields someone takes the hit instead and collapses on anyone beside it when it breaks. Raider
 * damage is capped per hit.
 * Phase 2: a 4-tick rotation of urn bombs with a lightning ball every fourth attack, melee for
 * anyone under her, exploding Nylocas Matomenos and healing blood spells below 35%.
 * Phase 3: a 7-tick rotation of melee/ranged/magic with a special every fourth attack (crabs, webs,
 * yellow pools or the green ball); at 20% she speeds up and sends a tornado after every raider.
 */
internal class VerzikRoom(raid: TobRaid, ctx: TobContext) : TobRoom(raid, ctx, 5) {
    override val displayName = "Verzik Vitur"
    override val entry = CoordGrid(3168, 4305, 0)
    override val arena = TobArea(3154, 4303, 3182, 4322)
    override val startsOnEntry = false
    override val mvpPoints = 4

    private var verzik: Npc? = null
    private var phase = 0
    private var nextAttack = 0
    private var attacks = 0
    private var transitioning = false
    private val pillars = mutableListOf<Pair<Npc, LocInfo>>()
    private val crabs = HashMap<Npc, Int>()
    private val tornadoes = HashMap<Npc, Long>()
    private var special = 0
    private var throneDoor: LocInfo? = null

    override fun bossNpc(): Npc? = verzik

    override fun onPrepare() {
        verzik = spawn(INITIAL, BOSS_TILE)
    }

    fun isThrone(npc: Npc): Boolean = npc === verzik && phase == 0

    override fun onStart() {
        verzik?.let(::despawn)
        verzik = spawn(PHASE1, BOSS_TILE)?.also { it.anim("seq.verzik_throne_transform") }
        phase = 1
        nextAttack = tick + P1_RATE
        for (tile in PILLARS) {
            val npc = spawn(PILLAR_NPC, tile, scaled = false) ?: continue
            pillars += npc to addLoc(tile, PILLAR_LOC)
        }
        message("<col=ff0000>Verzik Vitur rises from her throne!</col>")
    }

    override fun onTick() {
        val boss = verzik ?: return
        tickCrabs()
        tickTornadoes(boss)
        if (transitioning || boss.hitpoints <= 0 || tick < nextAttack) return
        when (phase) {
            1 -> phaseOneAttack(boss)
            2 -> phaseTwoAttack(boss)
            3 -> phaseThreeAttack(boss)
        }
    }

    private fun phaseOneAttack(boss: Npc) {
        nextAttack = tick + P1_RATE
        boss.anim("seq.verzik_phase1_attack_magic")
        val origin = TobMoves.centre(boss)
        for (player in targets()) {
            val shield = pillars.filter { (npc, _) -> blocks(origin, npc, player.coords) }.minByOrNull { (npc, _) -> npc.distanceTo(player) }
            if (shield != null) {
                val landing = ctx.projectile("spotanim.verzik_phase1_projectile", origin, TobMoves.centre(shield.first), sourceNpc = boss)
                ctx.worldQueue(landing) { ctx.spotanim(TobMoves.centre(shield.first), "spotanim.verzik_phase1_impactpillar") }
                ctx.hitNpc(shield.first, ctx.roll(PILLAR_HIT), landing)
                continue
            }
            val landing = ctx.projectile("spotanim.verzik_phase1_projectile", origin, player.coords, player, boss)
            ctx.hitPlayer(boss, player, HitType.Magic, ctx.roll(0..P1_MAX), landing, penetration = 50)
        }
    }

    private fun blocks(origin: CoordGrid, pillar: Npc, target: CoordGrid): Boolean {
        val p = TobMoves.centre(pillar)
        val minX = minOf(origin.x, target.x) - 1
        val maxX = maxOf(origin.x, target.x) + 1
        val minZ = minOf(origin.z, target.z) - 1
        val maxZ = maxOf(origin.z, target.z) + 1
        if (p.x !in minX..maxX || p.z !in minZ..maxZ) return false
        val dx = (target.x - origin.x).toDouble()
        val dz = (target.z - origin.z).toDouble()
        val length = max(1.0, kotlin.math.sqrt(dx * dx + dz * dz))
        val cross = kotlin.math.abs(dx * (p.z - origin.z) - dz * (p.x - origin.x)) / length
        return cross <= 1.6
    }

    private fun collapsePillar(npc: Npc) {
        val pillar = pillars.firstOrNull { it.first === npc } ?: return
        pillars.remove(pillar)
        removeLoc(pillar.second)
        ctx.spotanim(TobMoves.centre(npc), "spotanim.verzik_phase1_impactpillar")
        for (player in targets()) {
            if (npc.distanceTo(player) <= 1) ctx.hitPlayer(null, player, HitType.Typeless, ctx.roll(0..PILLAR_COLLAPSE_MAX))
        }
    }

    private fun phaseTwoAttack(boss: Npc) {
        nextAttack = tick + P2_RATE
        attacks++
        val players = targets()
        if (players.isEmpty()) return
        val under = players.filter { boss.distanceTo(it) <= 1 }
        if (under.isNotEmpty()) {
            boss.anim("seq.verzik_phase2_attack_melee")
            for (player in under) ctx.hitPlayer(boss, player, HitType.Melee, ctx.roll(0..P2_MELEE_MAX))
            return
        }
        if (attacks % CRAB_EVERY == 0) spawnCrabs(2, MATOMENOS)
        val origin = TobMoves.centre(boss)
        if (attacks % 4 == 0) {
            boss.anim("seq.verzik_phase2_attack_magic")
            val target = ctx.random.pick(players)
            val landing = ctx.projectile("spotanim.verzik_phase2_lightning", origin, target.coords, target, boss)
            val alone = players.none { it !== target && it.coords.chebyshevDistance(target.coords) <= 3 }
            ctx.hitPlayer(boss, target, HitType.Typeless, if (alone) ctx.roll(LIGHTNING_ALONE) else ctx.roll(LIGHTNING), landing)
            return
        }
        val bloodSpell = boss.hitpoints * 100 <= boss.baseHitpointsLvl * BLOOD_PERCENT
        boss.anim("seq.verzik_phase2_attack_magic")
        for (target in players) {
            if (bloodSpell) {
                val landing = ctx.projectile("spotanim.verzik_phase2_bloodproj", origin, target.coords, target, boss)
                val damage = ctx.roll(0..BLOOD_MAX)
                ctx.hitPlayer(boss, target, HitType.Magic, damage, landing)
                ctx.worldQueue(landing) { if (damage > 0 && !target.isProtectedFromMagic()) boss.heal(damage, showHitsplat = true) }
                continue
            }
            val tile = target.coords
            val landing = ctx.projectile("spotanim.verzik_phase2_ranged", origin, tile, sourceNpc = boss, stepCycles = 7)
            ctx.worldQueue(landing) {
                if (!isActive) return@worldQueue
                ctx.spotanim(tile, "spotanim.verzik_ranged_impact")
                for (player in targets()) {
                    if (player.coords.chebyshevDistance(tile) <= 1) {
                        ctx.hitPlayer(boss, player, HitType.Ranged, ctx.roll(0..URN_MAX), penetration = 50)
                    }
                }
            }
        }
    }

    private fun Player.isProtectedFromMagic(): Boolean = vars["varbit.prayer_protectfrommagic"] == 1

    private fun phaseThreeAttack(boss: Npc) {
        val enraged = boss.hitpoints * 100 <= boss.baseHitpointsLvl * ENRAGE_PERCENT
        nextAttack = tick + if (enraged) P3_ENRAGED_RATE else P3_RATE
        if (enraged && tornadoes.isEmpty()) summonTornadoes()
        attacks++
        val players = targets()
        if (players.isEmpty()) return
        if (attacks % 4 == 0) {
            when (special++ % 4) {
                0 -> spawnCrabs(players.size.coerceAtMost(3), P3_NYLOCAS.random())
                1 -> webs(boss)
                2 -> yellows(boss)
                else -> greenBall(boss, players)
            }
            return
        }
        val adjacent = players.filter { boss.distanceTo(it) <= 1 }
        if (adjacent.isNotEmpty()) {
            boss.anim("seq.verzik_phase3_attack_melee")
            for (player in adjacent) ctx.hitPlayer(boss, player, HitType.Melee, ctx.roll(0..P3_MELEE_MAX))
            return
        }
        val magic = attacks % 2 == 0
        boss.anim(if (magic) "seq.verzik_phase3_attack_magic" else "seq.verzik_phase3_attack_ranged")
        val origin = TobMoves.centre(boss)
        for (player in players) {
            val spot = if (magic) "spotanim.verzik_phase3_mageproj" else "spotanim.verzik_phase3_rangeproj"
            val landing = ctx.projectile(spot, origin, player.coords, player, boss)
            ctx.hitPlayer(boss, player, if (magic) HitType.Magic else HitType.Ranged, ctx.roll(0..P3_RANGED_MAX), landing, penetration = 50)
        }
    }

    private fun List<String>.random(): String = ctx.random.pick(this)

    private fun webs(boss: Npc) {
        boss.anim("seq.verzik_phase3_attack_webspin")
        val origin = TobMoves.centre(boss)
        val tiles = ctx.shuffled(arena.tiles()).take(WEBS).map(::at)
        for (tile in tiles) {
            val landing = ctx.projectile("spotanim.verzik_p3_web_proj", origin, tile, sourceNpc = boss)
            ctx.worldQueue(landing) {
                if (!isActive) return@worldQueue
                val web = addLoc(staticOf(tile), "loc.verzik_webspin_loc")
                for (player in targets()) {
                    if (player.coords == tile) {
                        CombatEffects.freeze(player, WEB_FREEZE)
                        ctx.hitPlayer(null, player, HitType.Typeless, ctx.roll(WEB_DAMAGE))
                    }
                }
                ctx.worldQueue(WEB_TICKS) { removeLoc(web) }
            }
        }
    }

    private fun yellows(boss: Npc) {
        boss.anim("seq.verzik_phase3_attack_powerblast")
        val pools = targets().map { player ->
            val tile = ctx.random.of(player.coords, 2).takeIf { inArena(it) } ?: player.coords
            ctx.spotanim(tile, "spotanim.verzik_powerblast_safezone")
            tile
        }.toSet()
        message("<col=ff0000>Verzik gathers power... stand in the light!</col>")
        ctx.worldQueue(YELLOW_DELAY) {
            if (!isActive) return@worldQueue
            for (player in targets()) {
                val safe = player.coords in pools
                ctx.spotanim(player.coords, "spotanim.verzik_powerblast_protection")
                ctx.hitPlayer(boss, player, HitType.Typeless, if (safe) ctx.roll(0..YELLOW_SAFE_MAX) else ctx.roll(YELLOW_DAMAGE))
            }
        }
    }

    private fun greenBall(boss: Npc, players: List<Player>) {
        val target = ctx.random.pick(players)
        val landing =
            ctx.projectile("spotanim.verzik_acidbomb_projanim", TobMoves.centre(boss), target.coords, target, boss, stepCycles = 10)
        ctx.worldQueue(landing) {
            if (!isActive || !target.isSlotAssigned) return@worldQueue
            ctx.spotanim(target.coords, "spotanim.verzik_acidbomb_impact")
            val partner = targets().filter { it !== target }.minByOrNull { it.coords.chebyshevDistance(target.coords) }
            val full = target.baseHitpointsLvl * GREEN_PERCENT / 100
            if (partner != null && partner.coords.chebyshevDistance(target.coords) <= GREEN_BOUNCE_RANGE) {
                ctx.hitPlayer(boss, target, HitType.Typeless, full / 3)
                ctx.hitPlayer(boss, partner, HitType.Typeless, full / 3)
            } else {
                ctx.hitPlayer(boss, target, HitType.Typeless, full)
            }
        }
    }

    private fun spawnCrabs(count: Int, type: String) {
        repeat(count) {
            val tile = ctx.random.pick(arena.tiles())
            val npc = spawn(type, tile) ?: return@repeat
            crabs[npc] = tick + CRAB_FUSE
        }
    }

    private fun tickCrabs() {
        for ((npc, fuse) in crabs.toList()) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                crabs.remove(npc)
                continue
            }
            val target = TobMoves.closest(npc, targets())
            if (tick >= fuse || (target != null && npc.distanceTo(target) <= 1 && tick >= fuse - CRAB_FUSE / 2)) {
                crabs.remove(npc)
                ctx.spotanim(npc.coords, "spotanim.tob_nylocas_death_melee_detonate")
                for (player in targets()) {
                    if (npc.distanceTo(player) <= 1) ctx.hitPlayer(npc, player, HitType.Typeless, ctx.roll(0..CRAB_MAX))
                }
                despawn(npc)
                continue
            }
            if (target != null) npc.walk(target.coords)
        }
    }

    private fun summonTornadoes() {
        message("<col=ff0000>Verzik Vitur summons a storm!</col>")
        for (player in targets()) {
            val npc = spawn(TORNADO, staticOf(ctx.random.of(player.coords, 4)), scaled = false) ?: continue
            tornadoes[npc] = player.uuid ?: continue
        }
    }

    private fun tickTornadoes(boss: Npc) {
        for ((npc, owner) in tornadoes.toList()) {
            val player = targets().firstOrNull { it.uuid == owner }
            if (player == null) {
                tornadoes.remove(npc)
                despawn(npc)
                continue
            }
            if (npc.coords == player.coords) {
                val damage = max(TORNADO_MIN, player.hitpoints / 2)
                ctx.hitPlayer(boss, player, HitType.Typeless, damage)
                boss.heal(damage * 3, showHitsplat = true)
                npc.telejump(ctx.collision, at(staticOf(ctx.random.of(player.coords, 5)).let { if (arena.contains(it)) it else entry }))
                continue
            }
            TobMoves.stepToward(ctx, npc, player.coords)
        }
    }

    override fun modifyHit(event: NpcHitEvents.Modify, attacker: Player?) {
        if (event.npc !== verzik) return
        if (transitioning || phase == 0) {
            event.hit.damage = 0
            return
        }
        if (phase == 1 && attacker != null) {
            val cap = if (event.hit.type == HitType.Melee) P1_MELEE_CAP else P1_RANGED_CAP
            if (event.hit.damage > cap) event.hit.damage = cap
        }
    }

    override fun onNpcDeath(npc: Npc): Pair<String, Int>? {
        if (pillars.any { it.first === npc }) {
            collapsePillar(npc)
            return "seq.verzik_pillar_collapse" to 1
        }
        if (crabs.remove(npc) != null) return null
        if (npc !== verzik) return null
        return when (phase) {
            1 -> {
                transitioning = true
                pillars.toList().forEach { (pillar, loc) ->
                    removeLoc(loc)
                    despawn(pillar)
                }
                pillars.clear()
                ctx.worldQueue(P1_DEATH_TICKS + 1) { nextPhase(2, PHASE2, P2_TILE) }
                "seq.verzik_phase1_death" to P1_DEATH_TICKS
            }
            2 -> {
                transitioning = true
                crabs.keys.toList().forEach(::despawn)
                crabs.clear()
                ctx.worldQueue(P2_DEATH_TICKS + 1) { nextPhase(3, PHASE3, P3_TILE) }
                "seq.verzik_phase2_death" to P2_DEATH_TICKS
            }
            else -> {
                tornadoes.keys.toList().forEach(::despawn)
                tornadoes.clear()
                crabs.keys.toList().forEach(::despawn)
                crabs.clear()
                complete()
                "seq.verzik_phase3_death_a" to P3_DEATH_TICKS
            }
        }
    }

    private fun nextPhase(next: Int, type: String, tile: CoordGrid) {
        if (!isActive) return
        val npc = spawn(type, tile) ?: return
        npc.anim(if (next == 2) "seq.verzik_phase2_spawn" else "seq.verzik_phase3_spawn")
        verzik = npc
        phase = next
        attacks = 0
        transitioning = false
        nextAttack = tick + SPAWN_DELAY
    }

    override fun onComplete() {
        throneDoor = addLoc(THRONE_DOOR_TILE, "loc.tob_dungeon_verzik_throne_door_opened")
        message("<col=ff0000>The way to Verzik's treasure vault lies behind her throne.</col>")
    }

    companion object {
        const val INITIAL = "npc.verzik_initial"
        private const val PHASE1 = "npc.verzik_phase1"
        private const val PHASE2 = "npc.verzik_phase2"
        private const val PHASE3 = "npc.verzik_phase3"
        private const val PILLAR_NPC = "npc.verzik_pillar_npc"
        private const val PILLAR_LOC = "loc.tob_dungeon_verzik_pillar"
        private const val MATOMENOS = "npc.tob_verzik_phase2_bloodnylocas"
        private const val TORNADO = "npc.tob_verzik_creeper"
        private val P3_NYLOCAS =
            listOf("npc.verzik_nylocas_melee", "npc.verzik_nylocas_ranged", "npc.verzik_nylocas_magic")

        private val BOSS_TILE = CoordGrid(3166, 4317)
        private val P2_TILE = CoordGrid(3167, 4313)
        private val P3_TILE = CoordGrid(3165, 4311)
        val THRONE_DOOR_TILE = CoordGrid(3167, 4323)
        private val PILLARS =
            listOf(3158, 3176).flatMap { x -> listOf(4305, 4311, 4317).map { z -> CoordGrid(x, z) } }

        private const val P1_RATE = 14
        private const val P1_MAX = 137
        private val PILLAR_HIT = 20..35
        private const val PILLAR_COLLAPSE_MAX = 70
        private const val P1_MELEE_CAP = 20
        private const val P1_RANGED_CAP = 10
        private const val P1_DEATH_TICKS = 5

        private const val P2_RATE = 4
        private const val P2_MELEE_MAX = 30
        private const val URN_MAX = 44
        private val LIGHTNING = 15..20
        private val LIGHTNING_ALONE = 48..55
        private const val BLOOD_PERCENT = 35
        private const val BLOOD_MAX = 30
        private const val CRAB_EVERY = 8
        private const val CRAB_FUSE = 20
        private const val CRAB_MAX = 63
        private const val P2_DEATH_TICKS = 5

        private const val P3_RATE = 7
        private const val P3_ENRAGED_RATE = 5
        private const val ENRAGE_PERCENT = 20
        private const val P3_MELEE_MAX = 63
        private const val P3_RANGED_MAX = 34
        private const val WEBS = 10
        private const val WEB_FREEZE = 5
        private val WEB_DAMAGE = 10..20
        private const val WEB_TICKS = 15
        private const val YELLOW_DELAY = 5
        private const val YELLOW_SAFE_MAX = 10
        private val YELLOW_DAMAGE = 40..60
        private const val GREEN_PERCENT = 74
        private const val GREEN_BOUNCE_RANGE = 2
        private const val TORNADO_MIN = 5
        private const val P3_DEATH_TICKS = 6
        private const val SPAWN_DELAY = 6
    }
}
