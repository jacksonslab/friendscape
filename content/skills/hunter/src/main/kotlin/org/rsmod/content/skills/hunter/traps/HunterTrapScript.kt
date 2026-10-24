package org.rsmod.content.skills.hunter.traps

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpObj4
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.hunter.HunterTrapCreatureRow
import org.rsmod.api.table.hunter.HunterTrapLootRow
import org.rsmod.api.table.hunter.HunterTrapRow
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.firstStepDestination
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import kotlin.math.abs

class HunterTrapScript
@Inject
constructor(
    private val traps: HunterTraps,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val objRegistry: ObjRegistry,
    private val playerRepo: PlayerRepository,
    private val areaChecker: AreaChecker,
    private val invisibleLvls: InvisibleLevels,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
    private val worldClock: MapClock,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val creaturesByNpc by lazy { HunterTrapCreatureRow.all().associateBy { it.npc.id } }
    private val lootByCreature by lazy { HunterTrapLootRow.all().groupBy { it.creature.rowId } }

    override fun ScriptContext.startup() {
        for (trap in HunterTrapRow.all()) {
            onOpHeld1(trap.item) { layFromInv(trap, it.slot) }
            onOpObj4(trap.item) { layFromGround(trap, it.obj) }
            onOpLoc1(trap.laid) { dismantle(it.loc) }
            onOpLoc2(trap.laid) { investigate(it.loc) }
            onOpLoc1(trap.failed) { dismantle(it.loc) }
            onOpLoc2(trap.failed) { reset(it.loc) }
        }
        for (creature in HunterTrapCreatureRow.all()) {
            onOpLoc1(creature.full) { check(it.loc) }
            onOpLoc2(creature.full) { reset(it.loc) }
        }
        onPlayerSoftTimer(TIMER) { player.processTraps() }
        onPlayerLogout { collapseAll(player) }
    }

    private suspend fun ProtectedAccess.layFromInv(trap: HunterTrapRow, slot: Int) {
        if (!canLay(trap, coords)) {
            return
        }
        anim(trap.layAnim.internalName)
        delay(LAY_DELAY)

        val layCoords = coords
        if (!canLay(trap, layCoords)) {
            return
        }
        val removed = invDel(inv, trap.item.internalName, count = 1, slot = slot)
        if (removed.failure) {
            return
        }
        placeTrap(trap, layCoords)
    }

    private suspend fun ProtectedAccess.layFromGround(trap: HunterTrapRow, obj: Obj) {
        val layCoords = obj.coords
        if (findGroundTrap(trap, layCoords) == null || !canLay(trap, layCoords)) {
            return
        }
        anim(trap.layAnim.internalName)
        delay(LAY_DELAY)

        val stack = findGroundTrap(trap, layCoords) ?: return
        if (!canLay(trap, layCoords)) {
            return
        }
        if (!takeOneFromGround(stack)) {
            return
        }
        placeTrap(trap, layCoords)
    }

    private fun ProtectedAccess.findGroundTrap(trap: HunterTrapRow, coords: CoordGrid): Obj? =
        objRegistry.findAll(coords).firstOrNull {
            it.type == trap.item.id && it.isVisibleTo(player)
        }

    private fun takeOneFromGround(stack: Obj): Boolean {
        if (!objRepo.del(stack)) {
            return false
        }
        if (stack.count > 1) {
            val entity = ObjEntity(stack.type, stack.count - 1, stack.entity.scope)
            val rest = Obj(stack.coords, entity, worldClock.cycle, stack.receiverId, stack.ownerId)
            objRepo.add(rest, GROUND_DURATION)
        }
        return true
    }

    private fun ProtectedAccess.canLay(trap: HunterTrapRow, layCoords: CoordGrid): Boolean {
        if (player.hunterLvl < trap.level) {
            mes("You need a Hunter level of ${trap.level} to set up this trap.")
            return false
        }
        val limit = trapLimit(player, trap, layCoords)
        if (traps.count(player.uid) >= limit) {
            val plural = if (limit == 1) "trap" else "traps"
            mes("You don't have a high enough Hunter level to set up more than $limit $plural.")
            return false
        }
        if (traps[layCoords] != null || locRepo.findAll(layCoords).any { it.layer == TRAP_LAYER }) {
            mes("You can't lay a trap here.")
            return false
        }
        return true
    }

    private fun trapLimit(player: Player, trap: HunterTrapRow, coords: CoordGrid): Int {
        val base = trapLimit(player.hunterLvl)
        val wildernessBonus = trap.wildernessBonus && coords.isInWilderness(areaChecker)
        return if (wildernessBonus) base + 1 else base
    }

    private fun ProtectedAccess.placeTrap(trap: HunterTrapRow, layCoords: CoordGrid) {
        val loc = spawnLoc(layCoords, trap.laid)
        traps.add(Trap(player.uid, layCoords, trap, loc, TrapState.Laid, worldClock.cycle))
        player.softTimer(TIMER, 1)
        stepAwayFrom(layCoords)
    }

    private fun ProtectedAccess.stepAwayFrom(trapCoords: CoordGrid) {
        if (coords != trapCoords) {
            return
        }
        val dest = collision.firstStepDestination(trapCoords, STEP_DIRECTIONS) ?: return
        walk(dest)
        faceSquare(trapCoords)
    }

    private fun ProtectedAccess.ownedTrapAt(loc: BoundLocInfo): Trap? {
        val trap = traps[loc.coords] ?: return null
        if (trap.loc.id != loc.id) {
            return null
        }
        if (trap.owner != player.uid) {
            mes("This isn't your trap.")
            return null
        }
        return trap
    }

    private fun ProtectedAccess.investigate(loc: BoundLocInfo) {
        val trap = traps[loc.coords]
        if (trap == null || trap.owner != player.uid) {
            mes("This isn't your trap.")
            return
        }
        mes("This trap is set and waiting for something to wander into it.")
    }

    private fun ProtectedAccess.dismantle(loc: BoundLocInfo) {
        val trap = ownedTrapAt(loc) ?: return
        if (trap.state != TrapState.Laid && trap.state != TrapState.Failed) {
            return
        }
        val added = invAdd(inv, trap.row.item.internalName, count = 1)
        if (added.failure) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        removeTrap(trap)
        anim(DISMANTLE_ANIM)
        mes("You dismantle the trap.")
    }

    private fun ProtectedAccess.check(loc: BoundLocInfo) {
        val trap = ownedTrapAt(loc) ?: return
        val creature = trap.creature
        if (trap.state != TrapState.Full || creature == null) {
            return
        }
        if (!collectCatch(trap, creature, includeTrap = true)) {
            return
        }
        removeTrap(trap)
        anim(DISMANTLE_ANIM)
    }

    private fun ProtectedAccess.reset(loc: BoundLocInfo) {
        val trap = ownedTrapAt(loc) ?: return
        val creature = trap.creature
        when (trap.state) {
            TrapState.Full -> {
                if (creature == null || !collectCatch(trap, creature, includeTrap = false)) {
                    return
                }
            }
            TrapState.Failed -> Unit
            else -> return
        }
        trap.creature = null
        changeState(trap, TrapState.Laid, trap.row.laid)
        anim(trap.row.layAnim.internalName)
    }

    private fun ProtectedAccess.collectCatch(
        trap: Trap,
        creature: HunterTrapCreatureRow,
        includeTrap: Boolean,
    ): Boolean {
        val loot = rollLoot(creature)
        val result =
            player.invTransaction(inv) {
                val target = select(inv)
                for ((obj, count) in loot) {
                    add(target, obj, count = count)
                }
                if (includeTrap) {
                    add(target, trap.row.item.id, count = 1)
                }
            }
        if (result.failure) {
            mes("You don't have enough inventory space to do that.")
            return false
        }
        val xp = creature.xp / 10.0 * xpMods.get(player, HUNTER)
        statAdvance(HUNTER, xp)
        mes("You've caught ${creature.npc.name.withArticle()}.")
        return true
    }

    private fun rollLoot(creature: HunterTrapCreatureRow): List<Pair<Int, Int>> =
        lootByCreature[creature.rowId].orEmpty().map { it.obj.id to random.of(it.min, it.max) }

    private fun Player.processTraps() {
        val owned = traps.ownedBy(uid)
        if (owned.isEmpty()) {
            clearSoftTimer(TIMER)
            return
        }
        for (trap in owned) {
            processTrap(this, trap)
        }
    }

    private fun processTrap(owner: Player, trap: Trap) {
        if (!isNearby(owner, trap)) {
            collapse(trap, owner)
            return
        }
        val elapsed = worldClock.cycle - trap.stateCycle
        when (trap.state) {
            TrapState.Laid -> if (elapsed > 0 && elapsed % LURE_INTERVAL == 0) lure(owner, trap)
            TrapState.Catching ->
                if (elapsed >= CATCHING_TICKS) {
                    val full = checkNotNull(trap.creature).full
                    changeState(trap, TrapState.Full, full)
                }
            TrapState.Failing ->
                if (elapsed >= FAILING_TICKS) changeState(trap, TrapState.Failed, trap.row.failed)
            TrapState.Full,
            TrapState.Failed -> if (elapsed >= COLLAPSE_TICKS) collapse(trap, owner)
        }
    }

    private fun isNearby(owner: Player, trap: Trap): Boolean =
        owner.coords.level == trap.coords.level &&
            owner.coords.chebyshevDistance(trap.coords) <= COLLAPSE_DISTANCE

    private fun lure(owner: Player, trap: Trap) {
        if (playerRepo.findAll(trap.coords).any()) {
            return
        }
        val (npc, creature) = findPrey(trap) ?: return
        val caught =
            owner.hunterLvl >= creature.level &&
                owner.statRandom(random, HUNTER, creature.low, creature.high, invisibleLvls)
        if (caught) {
            npcRepo.despawn(npc, creature.respawn)
            trap.creature = creature
            changeState(trap, TrapState.Catching, catchingLoc(trap, npc, creature))
        } else {
            changeState(trap, TrapState.Failing, trap.row.failing)
        }
    }

    private fun findPrey(trap: Trap): Pair<Npc, HunterTrapCreatureRow>? {
        val nearby = npcRepo.findAll(ZoneKey.from(trap.coords), zoneRadius = 1)
        return nearby
            .filter { it.isVisible && it.coords.level == trap.coords.level }
            .filter { it.coords.chebyshevDistance(trap.coords) <= LURE_RADIUS }
            .mapNotNull { npc -> creaturesByNpc[npc.type.id]?.let { npc to it } }
            .filter { (_, creature) -> creature.trap.rowId == trap.row.rowId }
            .minByOrNull { (npc, _) -> npc.coords.chebyshevDistance(trap.coords) }
    }

    private fun catchingLoc(trap: Trap, npc: Npc, creature: HunterTrapCreatureRow): ObjectServerType {
        val dx = npc.coords.x - trap.coords.x
        val dz = npc.coords.z - trap.coords.z
        return when {
            abs(dx) > abs(dz) && dx > 0 -> creature.catchingE
            abs(dx) > abs(dz) -> creature.catchingW
            dz > 0 -> creature.catchingN
            else -> creature.catchingS
        }
    }

    private fun changeState(trap: Trap, state: TrapState, loc: ObjectServerType) {
        trap.loc = spawnLoc(trap.coords, loc)
        trap.state = state
        trap.stateCycle = worldClock.cycle
    }

    private fun spawnLoc(coords: CoordGrid, type: ObjectServerType): LocInfo =
        locRepo.add(coords, type.internalName, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)

    private fun removeTrap(trap: Trap) {
        if (traps.remove(trap)) {
            locRepo.del(trap.loc, Int.MAX_VALUE)
        }
    }

    private fun collapse(trap: Trap, owner: Player) {
        if (!traps.remove(trap)) {
            return
        }
        locRepo.del(trap.loc, Int.MAX_VALUE)
        val item = trap.row.item.internalName
        val drop =
            if (owner.observerUUID != null) {
                Obj.fromOwner(owner, trap.coords, item, 1)
            } else {
                Obj.fromServer(worldClock, trap.coords, item, 1)
            }
        objRepo.add(drop, GROUND_DURATION)
        owner.mes("The ${trap.row.item.name.lowercase()} that you laid has fallen over.")
    }

    private fun collapseAll(owner: Player) {
        for (trap in traps.ownedBy(owner.uid)) {
            collapse(trap, owner)
        }
    }

    private fun String.withArticle(): String {
        val lower = lowercase()
        val article = if (lower.first() in VOWELS) "an" else "a"
        return "$article $lower"
    }

    companion object {
        const val TIMER = "timer.hunter_traps"
        const val HUNTER = "stat.hunter"

        const val LAY_DELAY = 3
        const val LURE_INTERVAL = 3
        const val LURE_RADIUS = 2
        const val CATCHING_TICKS = 2
        const val FAILING_TICKS = 2
        const val COLLAPSE_TICKS = 100
        const val COLLAPSE_DISTANCE = 32
        const val GROUND_DURATION = 200

        private const val DISMANTLE_ANIM = "seq.human_pickupfloor"
        private const val VOWELS = "aeiou"

        private const val TRAP_LAYER = LocLayerConstants.GROUND

        private val STEP_DIRECTIONS =
            listOf(Direction.West, Direction.East, Direction.South, Direction.North)

        fun trapLimit(hunterLevel: Int): Int =
            when {
                hunterLevel >= 80 -> 5
                hunterLevel >= 60 -> 4
                hunterLevel >= 40 -> 3
                else -> 2
            }
    }
}
