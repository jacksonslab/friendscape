package org.rsmod.content.raids.tob

import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal data class TobArea(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int, val level: Int = 0) {
    fun contains(static: CoordGrid): Boolean =
        static.level == level && static.x in minX..maxX && static.z in minZ..maxZ

    fun tiles(): List<CoordGrid> = buildList {
        for (x in minX..maxX) for (z in minZ..maxZ) add(CoordGrid(x, z, level))
    }
}

/**
 * A barrier line: every tile shares [line] on one axis, and [insideStep] (+1 or -1) is the step
 * across it into the arena.
 */
internal data class TobBarrier(val vertical: Boolean, val line: Int, val insideStep: Int) {
    fun isInside(static: CoordGrid): Boolean {
        val axis = if (vertical) static.x else static.z
        return (axis - line) * insideStep > 0
    }

    fun across(barrierTile: CoordGrid, inward: Boolean): CoordGrid {
        val step = if (inward) insideStep else -insideStep
        return if (vertical) {
            CoordGrid(line + step, barrierTile.z, barrierTile.level)
        } else {
            CoordGrid(barrierTile.x, line + step, barrierTile.level)
        }
    }
}

internal enum class TobRoomState {
    Waiting,
    Active,
    Complete,
}

internal abstract class TobRoom(val raid: TobRaid, val ctx: TobContext, val index: Int) {
    abstract val displayName: String
    abstract val entry: CoordGrid
    abstract val arena: TobArea
    open val barriers: List<TobBarrier> = emptyList()
    open val spectate: CoordGrid
        get() = entry
    open val mvpPoints: Int = 2
    open val passageTiles: Set<CoordGrid> = emptySet()
    open val startsOnEntry: Boolean = true

    fun hasPassage(static: CoordGrid): Boolean = passageTiles.any { it.x == static.x && it.z == static.z }

    var state: TobRoomState = TobRoomState.Waiting
        private set

    var startedTick: Int = -1
        private set

    private val npcs = mutableListOf<Npc>()
    private val locs = mutableListOf<LocInfo>()
    private var prepared = false

    val isActive: Boolean
        get() = state == TobRoomState.Active

    val isComplete: Boolean
        get() = state == TobRoomState.Complete

    protected val tick: Int
        get() = ctx.cycle - startedTick

    fun at(static: CoordGrid): CoordGrid = raid.coord(static)

    fun at(x: Int, z: Int, level: Int = arena.level): CoordGrid = raid.coord(CoordGrid(x, z, level))

    fun staticOf(coords: CoordGrid): CoordGrid = raid.static(coords)

    fun inArena(coords: CoordGrid): Boolean = arena.contains(staticOf(coords))

    fun prepare() {
        if (prepared) return
        prepared = true
        onPrepare()
    }

    fun start() {
        if (state != TobRoomState.Waiting) return
        prepare()
        state = TobRoomState.Active
        startedTick = ctx.cycle
        onStart()
    }

    fun complete() {
        if (state != TobRoomState.Active) return
        state = TobRoomState.Complete
        onComplete()
        raid.onRoomComplete(this)
    }

    fun process() {
        if (state == TobRoomState.Active) onTick()
    }

    fun dispose() {
        npcs.forEach(ctx::removeNpc)
        npcs.clear()
        locs.forEach(ctx::removeLoc)
        locs.clear()
        onDispose()
    }

    /** Raiders who can currently be targeted: alive, logged in and standing inside the arena. */
    fun targets(): List<Player> = raid.alivePlayers().filter { inArena(it.coords) }

    fun spawn(type: String, static: CoordGrid, scaled: Boolean = true): Npc? {
        val npc = ctx.spawnNpc(type, at(static), if (scaled) raid.hpFactor else 1.0) ?: return null
        track(npc)
        return npc
    }

    fun track(npc: Npc) {
        npcs += npc
        raid.attach(npc)
    }

    fun untrack(npc: Npc) {
        npcs.remove(npc)
    }

    fun despawn(npc: Npc) {
        npcs.remove(npc)
        ctx.removeNpc(npc)
    }

    fun owns(npc: Npc): Boolean = npc in npcs

    fun addLoc(static: CoordGrid, loc: String, angle: Int = 0): LocInfo {
        val info = ctx.addLoc(at(static), loc, angle)
        locs += info
        return info
    }

    fun removeLoc(loc: LocInfo) {
        if (locs.remove(loc)) ctx.removeLoc(loc)
    }

    fun message(text: String) {
        raid.players().forEach { it.mes(text) }
    }

    protected open fun onPrepare() = Unit

    protected abstract fun onStart()

    protected abstract fun onTick()

    protected open fun onComplete() = Unit

    protected open fun onDispose() = Unit

    open fun bossNpc(): Npc? = null

    /** Returns the seq to play and how many ticks it runs, or null to delete straight away. */
    open fun onNpcDeath(npc: Npc): Pair<String, Int>? = null

    open fun modifyHit(event: NpcHitEvents.Modify, attacker: Player?) = Unit

    open fun onHit(event: NpcHitEvents.Impact, attacker: Player?) = Unit

    open fun onRaiderDeath(player: Player) = Unit

    open fun allowsPass(player: Player, inward: Boolean): String? = null
}
