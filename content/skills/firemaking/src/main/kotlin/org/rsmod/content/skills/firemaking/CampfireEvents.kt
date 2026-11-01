package org.rsmod.content.skills.firemaking

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.loc.locNearby
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.FiremakingColoredLogsRow
import org.rsmod.api.table.FiremakingLogsRow
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CampfireEvents @Inject constructor(
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val fires: FiremakingFires,
    private val xpMods: XpModifiers,
) : PluginScript() {
    private val coloredLogRows = FiremakingColoredLogsRow.all()
    private val campFireObjects = coloredLogRows.map { it.campfireObject.internalName }.toSet() + "loc.forestry_fire"
    private val basicFires = coloredLogRows.map { it.fireObject.internalName }.toSet() + "loc.fire"
    private val campfireLocForColoredLog =
        coloredLogRows.associate { it.logItem.internalName to it.campfireObject.internalName }

    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs("queue.firemaking_campfire_tend") { processCampfireTendTick(it.args) }
        campFireObjects.forEach { camp ->
            onOpLoc1(camp) { tendCampfireMenu(it.vis) }
            onOpLoc2(camp) { showCampfireStatus(it.vis) }
            onOpLoc3(camp) { anim("seq.forestry_sitting_tea_loop") }
            FiremakingLogsRow.all().forEach { log ->
                onOpLocU(camp, log.input.internalName) {
                    tendCampfireWithLog(it.vis, log, inv.count(log.input.internalName))
                }
            }
        }
        basicFires.forEach { fire ->
            FiremakingLogsRow.all().forEach { log ->
                onOpLocU(fire, log.input.internalName) { lightCampfire(it, log) }
            }
        }
    }

    private fun ProtectedAccess.lightCampfire(event: LocUEvents.Op, log: FiremakingLogsRow) {
        if (!inv.contains("obj.tinderbox")) {
            mes("You need a tinderbox to start a campfire.")
            return
        }
        if (!hasFiremakingLevelOrMes(log)) return
        val fire = event.vis
        if (locRepo.locNearby(fire.coords, 5, fire.coords, campFireObjects)) {
            mes("There's a Forester's Campfire nearby, help tend to that one or move further away.")
            return
        }
        val fireObject = campfireLocForColoredLog[log.input.internalName] ?: "loc.forestry_fire"
        if (!consumeFiremakingLog(log.input.internalName) {
                fires.convert(fire, fireObject, log.foresterInitialTicks.coerceAtLeast(1))
            }) return
        val smoke = when (fire.angle.id) {
            0 -> "spotanim.forestry_campfire_smoke_01"
            1 -> "spotanim.forestry_campfire_smoke_02"
            2 -> "spotanim.forestry_campfire_smoke_03"
            else -> "spotanim.forestry_campfire_smoke_04"
        }
        spotanimMap(worldRepo, SpotanimType(smoke.asRSCM(RSCMType.SPOTANIM)), fire.coords)
    }

    private suspend fun ProtectedAccess.tendCampfireMenu(camp: BoundLocInfo) {
        val logs = FiremakingLogsRow.all().filter {
            player.firemakingLvl >= it.statReq.first().t1 && inv.count(it.input.internalName) > 0
        }
        if (logs.isEmpty()) {
            mes("You don't have any logs you can burn.")
            return
        }
        openSkillMulti(SkillMultiConfig(verb = "burn", entries = logs.map { SkillMultiEntry(it.input.internalName) })) { selection ->
            val log = logs.firstOrNull { it.input.internalName == selection.entry.internal } ?: return@openSkillMulti
            tendCampfireWithLog(camp, log, selection.amount)
        }
    }

    private fun ProtectedAccess.tendCampfireWithLog(camp: BoundLocInfo, log: FiremakingLogsRow, amount: Int) {
        val identity = fires.identity(camp) ?: return
        stopAction()
        if (amount <= 0 || !canTend(log, camp)) {
            resetAnim()
            return
        }
        weakQueue("queue.firemaking_campfire_tend", 1, CampfireTendTask(camp, log, amount, identity))
    }

    private fun ProtectedAccess.processCampfireTendTick(task: CampfireTendTask) {
        val camp = task.campfire
        if (fires.identity(camp) !== task.identity || !canTend(task.log, camp)) {
            resetAnim()
            return
        }
        if (!consumeFiremakingLog(task.log.input.internalName) { fires.extend(camp, task.log.foresterLogTicks) }) {
            resetAnim()
            return
        }
        anim(task.log.foresterAnimation?.internalName ?: "seq.forestry_campfire_burning_logs")
        statAdvance("stat.firemaking", task.log.xpTenths / 10.0 * xpMods.get(player, "stat.firemaking"))
        spotanimMap(worldRepo, "spotanim.forestry_campfire_burning_spotanim", camp.coords)
        if (task.remaining > 1) {
            weakQueue("queue.firemaking_campfire_tend", 9, task.copy(remaining = task.remaining - 1))
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.showCampfireStatus(camp: BoundLocInfo) {
        resetAnim()
        val text = when (fires.remaining(camp)) {
            in 0..58 -> "The embers glow softly."
            in 59..118 -> "The flames flicker gently."
            in 119..178 -> "The fire burns steadily."
            in 179..238 -> "The fire burns brightly."
            else -> "The roaring fire crackles invitingly."
        }
        spam(text)
    }

    private fun ProtectedAccess.canTend(log: FiremakingLogsRow, camp: BoundLocInfo): Boolean =
        coords.level == camp.coords.level &&
            kotlin.math.abs(coords.x - camp.coords.x) <= 1 &&
            kotlin.math.abs(coords.z - camp.coords.z) <= 1 &&
            inv.count(log.input.internalName) > 0 && fires.remaining(camp) > 0 && hasFiremakingLevelOrMes(log)

    private fun ProtectedAccess.hasFiremakingLevelOrMes(log: FiremakingLogsRow): Boolean {
        if (player.firemakingLvl >= log.statReq.first().t1) return true
        mes("You need a Firemaking level of ${log.statReq.first().t1} to burn ${log.input.name}.")
        return false
    }

    private data class CampfireTendTask(
        val campfire: BoundLocInfo,
        val log: FiremakingLogsRow,
        val remaining: Int,
        val identity: Any,
    )
}
