package org.rsmod.content.raids.tob.rooms

import kotlin.math.max
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
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
 * Sotetseg. Every 5 ticks he either claws an adjacent raider or fires an orb that ricochets to a
 * second raider in the other style; every tenth orb is the big red ball, whose damage is shared by
 * everyone within a tile of its target. At 66% and 33% he drags one raider into the shadow realm:
 * they must walk a hidden path through a 14x15 maze to the portal while the rest of the party
 * follows the trail they leave, and stepping off the path hurts.
 */
internal class SotetsegRoom(raid: TobRaid, ctx: TobContext) : TobRoom(raid, ctx, 3) {
    override val displayName = "Sotetseg"
    override val entry = CoordGrid(3279, 4296, 0)
    override val arena = TobArea(3273, 4308, 3286, 4329)
    override val barriers = listOf(TobBarrier(vertical = false, line = 4307, insideStep = 1))
    override val passageTiles = setOf(CoordGrid(3279, 4293), CoordGrid(3280, 4293))

    private var sotetseg: Npc? = null
    private var nextAttack = 0
    private var orbs = 0
    private var mazesDone = 0
    private var maze: Set<Pair<Int, Int>>? = null
    private var walkerId: Long? = null
    private var nextChip = 0
    private val trail = mutableListOf<LocInfo>()
    private val pathLocs = mutableListOf<LocInfo>()

    override fun bossNpc(): Npc? = sotetseg

    override fun onPrepare() {
        sotetseg = spawn(NONCOMBAT, BOSS_TILE)?.also { it.faceSquare(at(3279, 4310)) }
    }

    override fun onStart() {
        sotetseg?.let { ctx.transmog(it, COMBAT) }
        nextAttack = tick + ATTACK_RATE
    }

    override fun onTick() {
        val boss = sotetseg ?: return
        if (boss.hitpoints <= 0) return
        if (maze != null) {
            tickMaze()
            return
        }
        val percent = boss.hitpoints * 100 / max(1, boss.baseHitpointsLvl)
        if ((mazesDone == 0 && percent <= 66) || (mazesDone == 1 && percent <= 33)) {
            startMaze(boss)
            return
        }
        if (tick < nextAttack) return
        nextAttack = tick + ATTACK_RATE
        attack(boss)
    }

    private fun attack(boss: Npc) {
        val players = targets()
        if (players.isEmpty()) return
        val adjacent = players.filter { boss.distanceTo(it) <= 1 }
        if (adjacent.isNotEmpty()) {
            val target = ctx.random.pick(adjacent)
            boss.anim("seq.tob_sotetseg_attack_melee")
            ctx.hitPlayer(boss, target, HitType.Melee, ctx.roll(0..MELEE_MAX), delay = 2)
            return
        }
        orbs++
        boss.anim("seq.tob_sotetseg_attack_ranged")
        val target = ctx.random.pick(players)
        if (orbs % DEATH_BALL_EVERY == 0) {
            deathBall(boss, target, players.size)
            return
        }
        val magic = ctx.chance(1, 2)
        val landing = orb(boss, target, magic)
        val bounce = players.filter { it !== target }.randomOrNull()
        if (bounce != null) {
            ctx.worldQueue(landing) {
                if (!isActive || !target.isSlotAssigned) return@worldQueue
                val second =
                    ctx.projectile(spot(!magic), target.coords, bounce.coords, bounce, startHeight = 30)
                ctx.hitPlayer(boss, bounce, if (magic) HitType.Ranged else HitType.Magic, ctx.roll(0..ORB_MAX), second)
            }
        }
    }

    private fun List<Player>.randomOrNull(): Player? = if (isEmpty()) null else ctx.random.pick(this)

    private fun orb(boss: Npc, target: Player, magic: Boolean): Int {
        val landing = ctx.projectile(spot(magic), TobMoves.centre(boss), target.coords, target, boss)
        ctx.hitPlayer(boss, target, if (magic) HitType.Magic else HitType.Ranged, ctx.roll(0..ORB_MAX), landing)
        return landing
    }

    private fun spot(magic: Boolean) = if (magic) "spotanim.tob_sotetseg_maging" else "spotanim.tob_sotetseg_ranging"

    private fun deathBall(boss: Npc, target: Player, raiders: Int) {
        message("<col=ff0000>Sotetseg hurls a ball of death at ${target.displayName}!</col>")
        val landing =
            ctx.projectile(
                "spotanim.tob_sotetseg_sharedattack",
                TobMoves.centre(boss),
                target.coords,
                target,
                boss,
                stepCycles = 12,
            )
        val total = DEATH_BALL_MAX * raiders.coerceIn(1, 5) / 5
        ctx.worldQueue(landing) {
            if (!isActive) return@worldQueue
            ctx.spotanim(target.coords, "spotanim.tob_sotetseg_sharedattack_impact")
            val sharing = targets().filter { it.coords.chebyshevDistance(target.coords) <= 1 }
            if (sharing.isEmpty()) return@worldQueue
            val each = total / sharing.size
            for (player in sharing) ctx.hitPlayer(boss, player, HitType.Typeless, ctx.roll(each / 2..each))
        }
    }

    private fun startMaze(boss: Npc) {
        mazesDone++
        val walker = ctx.random.pick(targets().ifEmpty { return })
        val path = generatePath()
        maze = path
        walkerId = walker.uuid
        nextChip = tick + CHIP_RATE
        boss.anim("seq.tob_sotetseg_attack_ranged")
        message("<col=ff0000>Sotetseg drags ${walker.displayName} into the shadow realm!</col>")
        for ((col, row) in path) {
            pathLocs += addLoc(CoordGrid(REALM_X + col, MAZE_Z + row, REALM_LEVEL), "loc.tob_sotetseg_lighttile")
        }
        val start = path.first { it.second == 0 }
        ctx.teleport(walker, at(CoordGrid(REALM_X + start.first, MAZE_Z - 1, REALM_LEVEL)))
        walker.mes("Follow the red path to the portal to escape the shadow realm.")
    }

    private fun generatePath(): Set<Pair<Int, Int>> {
        val path = LinkedHashSet<Pair<Int, Int>>()
        var col = ctx.roll(0 until MAZE_WIDTH)
        for (row in 0 until MAZE_LENGTH) {
            path += col to row
            if (row == MAZE_LENGTH - 1) break
            val target = (col + ctx.roll(-3..3)).coerceIn(0, MAZE_WIDTH - 1)
            while (col != target) {
                col += if (target > col) 1 else -1
                path += col to row
            }
        }
        return path
    }

    private fun tickMaze() {
        val path = maze ?: return
        val walker = walkerId?.let { id -> raid.players().firstOrNull { it.uuid == id } }
        if (walker == null || !raid.isAlive(walker)) {
            endMaze(walker = null)
            return
        }
        val realm = staticOf(walker.coords)
        if (realm.level == REALM_LEVEL) {
            val cell = (realm.x - REALM_X) to (realm.z - MAZE_Z)
            if (inMaze(cell)) {
                if (cell !in path) ctx.hitPlayer(null, walker, HitType.Typeless, offPathDamage(walker))
                markTrail(cell)
            }
            if (tick >= nextChip) {
                nextChip = tick + CHIP_RATE
                ctx.hitPlayer(null, walker, HitType.Typeless, ctx.roll(1..3))
            }
        }
        for (player in targets()) {
            val tile = staticOf(player.coords)
            val cell = (tile.x - arena.minX) to (tile.z - MAZE_Z)
            if (inMaze(cell) && cell !in path) ctx.hitPlayer(null, player, HitType.Typeless, offPathDamage(player))
        }
    }

    private fun inMaze(cell: Pair<Int, Int>) =
        cell.first in 0 until MAZE_WIDTH && cell.second in 0 until MAZE_LENGTH

    private fun offPathDamage(player: Player): Int = player.hitpoints * 667 / 10000 + OFF_PATH_FLAT

    private fun markTrail(cell: Pair<Int, Int>) {
        val static = CoordGrid(arena.minX + cell.first, MAZE_Z + cell.second, arena.level)
        if (trail.any { staticOf(it.coords) == static }) return
        trail += addLoc(static, "loc.tob_sotetseg_triggeredtile")
    }

    /** Called when the shadow realm walker uses the portal. */
    fun exitRealm(player: Player) {
        if (player.uuid != walkerId) {
            ctx.teleport(player, at(entry))
            return
        }
        endMaze(player)
    }

    private fun endMaze(walker: Player?) {
        maze = null
        walkerId = null
        trail.toList().forEach(::removeLoc)
        trail.clear()
        pathLocs.toList().forEach(::removeLoc)
        pathLocs.clear()
        if (walker != null) ctx.teleport(walker, at(MAZE_EXIT))
        nextAttack = tick + ATTACK_RATE
    }

    override fun modifyHit(event: NpcHitEvents.Modify, attacker: Player?) {
        if (event.npc === sotetseg && maze != null) event.hit.damage = 0
    }

    override fun onRaiderDeath(player: Player) {
        if (player.uuid == walkerId) endMaze(walker = null)
    }

    override fun onNpcDeath(npc: Npc): Pair<String, Int>? {
        if (npc !== sotetseg) return null
        if (maze != null) endMaze(walker = null)
        complete()
        return "seq.tob_sotetseg_death" to DEATH_TICKS
    }

    private companion object {
        const val NONCOMBAT = "npc.tob_sotetseg_noncombat"
        const val COMBAT = "npc.tob_sotetseg_combat"
        val BOSS_TILE = CoordGrid(3277, 4325)
        val MAZE_EXIT = CoordGrid(3279, 4309)

        const val ATTACK_RATE = 5
        const val MELEE_MAX = 20
        const val ORB_MAX = 22
        const val DEATH_BALL_EVERY = 10
        const val DEATH_BALL_MAX = 188
        const val MAZE_WIDTH = 14
        const val MAZE_LENGTH = 15
        const val MAZE_Z = 4310
        const val REALM_X = 3354
        const val REALM_LEVEL = 3
        const val CHIP_RATE = 7
        const val OFF_PATH_FLAT = 15
        const val DEATH_TICKS = 5
    }
}
