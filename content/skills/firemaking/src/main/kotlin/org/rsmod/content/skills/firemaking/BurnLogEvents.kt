package org.rsmod.content.skills.firemaking

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpObj4
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.FiremakingColoredLogsRow
import org.rsmod.api.table.FiremakingLogsRow
import org.rsmod.game.MapClock
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.map.collision.firstStepDestination
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import skillSuccess


public class BurnLogEvents @Inject constructor(
    private val objRegistry: ObjRegistry,
    private val locRepo: LocRepository,
    private val worldClock: MapClock,
    private val xpMods: XpModifiers,
    private val fires: FiremakingFires,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val WALK_DIRECTIONS = listOf(
        Direction.West,
        Direction.East,
        Direction.South,
        Direction.North,
    )

    private val coloredLogs = FiremakingColoredLogsRow.all().map { it.logItem.internalName }.toSet()

    private val coloredFire: Map<String, String> = FiremakingColoredLogsRow.all()
        .associate { it.logItem.internalName to it.fireObject.internalName }

    override fun ScriptContext.startup() {
        FiremakingLogsRow.all().forEach { log ->
            onOpHeldU("obj.tinderbox", log.input) { startBurn(log, method = BurnMethod.Tinderbox) }
            onOpHeldU(log.input) {
                val barbarianAnim = it.second.paramOrNull(params.barbarian_firemaking_anim)
                    ?: return@onOpHeldU
                val animName = RSCM.getReverseMapping(RSCMType.SEQ, barbarianAnim.id)
                if (animName.isBlank()) {
                    return@onOpHeldU
                }
                startBurn(log, method = BurnMethod.Bow, burnAnim = animName, bow = it.second.internalName)
            }
            onOpObj4(log.input) { startBurn(log, it.obj, BurnMethod.Tinderbox) }
        }

        onPlayerQueueWithArgs("queue.firemaking_light") { processBurnTick(it.args) }
    }

    private fun ProtectedAccess.startBurn(
        log: FiremakingLogsRow,
        groundObj: Obj? = null,
        method: BurnMethod,
        burnAnim: String = "seq.human_createfire",
        bow: String? = null,
    ) {
        if (!canBurn(log, groundObj, method, bow)) {
            resetAnim()
            return
        }

        val obj = groundObj ?: dropFiremakingLog(log.input.internalName, objRegistry, worldClock) ?: return

        stopAction()
        anim(burnAnim)
        player.mes(
            when (method) {
                BurnMethod.Tinderbox -> "You attempt to light the logs."
                BurnMethod.Bow -> "You attempt to light the logs with your bow."
            },
        )

        weakQueue("queue.firemaking_light", 4, BurnTask(log, obj, method, bow))
    }

    private fun ProtectedAccess.canBurn(
        log: FiremakingLogsRow,
        obj: Obj?,
        method: BurnMethod,
        bow: String?,
    ): Boolean {
        if (obj != null && (obj.coords != coords || !fires.contains(player, obj))) {
            return false
        }

        if (method == BurnMethod.Tinderbox && !inv.contains("obj.tinderbox")) {
            player.mes("You do not have any fire source to light this.")
            return false
        }

        if (method == BurnMethod.Bow && (bow == null || !inv.contains(bow))) {
            player.mes("You need your bow to light these logs.")
            return false
        }

        val reqLevel = when (method) {
            BurnMethod.Tinderbox -> log.statReq.first().t1
            BurnMethod.Bow -> (log.statReq.first().t1 + 20).coerceAtMost(99)
        }
        if (player.firemakingLvl < reqLevel) {
            player.mes("You need a Firemaking level of $reqLevel to burn ${log.input.name} this way.")
            return false
        }

        val fireTile = obj?.coords ?: coords
        if (!collision.isZoneValid(fireTile) || collision.isWalkBlocked(fireTile) || tileHasCentrepieceLoc(fireTile)) {
            player.mes("You can't light a fire here.")
            return false
        }

        return true
    }

    private fun tileHasCentrepieceLoc(tile: CoordGrid): Boolean =
        locRepo.findExact(tile, LocShape.CentrepieceStraight) != null ||
            locRepo.findExact(tile, LocShape.CentrepieceDiagonal) != null

    private fun ProtectedAccess.processBurnTick(task: BurnTask) {
        if (!canBurn(task.log, task.obj, task.method, task.bow)) {
            resetAnim()
            return
        }

        val success = coloredLogs.contains(task.log.input.internalName) ||
            skillSuccess(64, 512, player.firemakingLvl)

        if (!success) {
            weakQueue("queue.firemaking_light", 4, task)
            return
        }

        completeBurn(task)
    }

    private fun ProtectedAccess.completeBurn(task: BurnTask) {
        val fireCoords = task.obj.coords
        val fireId = coloredFire[task.log.input.internalName] ?: "loc.fire"
        if (!fires.light(player, task.obj, fireId, (100..200).random())) {
            resetAnim()
            return
        }

        resetAnim()
        val xpModifier = xpMods.get(player, "stat.firemaking")
        val xp = (task.log.xpTenths / 10.0) * xpModifier
        statAdvance("stat.firemaking", xp)
        mes("The fire catches and the logs begin to burn.")

        moveAwayFromFire(fireCoords)
        faceSquare(fireCoords)
    }

    private fun ProtectedAccess.moveAwayFromFire(coords: CoordGrid) {
        val dest = collision.firstStepDestination(coords, WALK_DIRECTIONS) ?: return
        walk(dest)
    }

    data class BurnTask(
        val log: FiremakingLogsRow,
        val obj: Obj,
        val method: BurnMethod,
        val bow: String?,
    )

    enum class BurnMethod {
        Tinderbox,
        Bow,
    }
}
