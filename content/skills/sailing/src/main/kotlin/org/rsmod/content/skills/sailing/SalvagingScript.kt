package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SalvagingScript
@Inject
constructor(
    private val boats: BoatManager,
    private val wrecks: ShipwreckManager,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.Startup> { wrecks.start() }
        onEvent<GameLifecycle.LateCycle> { wrecks.tick() }

        for (hook in BoatTypes.ALL.map { it.hook }.distinct()) {
            onOpLoc1(locName(hook)) { deployHook() }
            onOpLoc5(locName(hook)) { modifyFacility() }
        }
        for (hold in BoatTypes.ALL.mapNotNull { it.cargoHold }.distinct()) {
            val variants = dev.openrune.ServerCacheManager.getObject(RSCM.getRSCM(hold.loc))?.transforms.orEmpty()
            for (variant in variants.filter { it > 0 }.distinct()) {
                onOpLoc5(locName(variant)) { modifyFacility() }
            }
        }
        onOpLoc1("loc.sailing_port_salvaging_station") { sortSalvage() }
        onOpLoc1("loc.sailing_port_salvaging_station_port_sarim") { sortSalvage() }
    }

    private suspend fun ProtectedAccess.deployHook() {
        val boat = boats.boatOf(player) ?: return
        if (!boat.isStopped) {
            mes("You need to bring the boat to a stop before deploying the hook.")
            return
        }
        val wreck = wrecks.nearestActive(boat.entity.coords, SALVAGE_RANGE)
        if (wreck == null) {
            mes("There's no shipwreck close enough to salvage.")
            return
        }
        val type = wreck.type
        if (statBase(STAT_SAILING) < type.level) {
            mes("You need a Sailing level of ${type.level} to salvage this shipwreck.")
            return
        }
        mes("You deploy the salvaging hook.")
        while (true) {
            delay(SALVAGE_INTERVAL)
            if (boats.boatOf(player) != boat || !boat.isStopped) {
                mes("You stop salvaging as the boat moves away.")
                return
            }
            if (!wreck.active) {
                mes("You've salvaged all you can before the wreck is reclaimed by the sea.")
                return
            }
            if (inv.isFull()) {
                mes("Your inventory is too full to hold any more salvage.")
                return
            }
            wrecks.startDepleting(wreck)
            val chance = SkillingSuccessRate.successRate(type.lowChance, type.highChance, stat(STAT_SAILING), MAX_LEVEL)
            if (random.randomDouble() >= chance) {
                continue
            }
            if (!invAdd(inv, objName(type.salvage.id)).success) {
                return
            }
            statAdvance(STAT_SAILING, type.salvageXp * xpMods.get(player, STAT_SAILING))
        }
    }

    private suspend fun ProtectedAccess.sortSalvage() {
        if (inv.none { it != null && WreckTypes.bySalvage(it.id) != null }) {
            mes("You don't have any salvage to sort.")
            return
        }
        while (true) {
            val salvage = inv.firstOrNull { it != null && WreckTypes.bySalvage(it.id) != null } ?: return
            val type = WreckTypes.bySalvage(salvage.id) ?: return
            val (loot, count) = type.roll(random)
            val sorted =
                player.invTransaction(inv) {
                    val target = select(inv)
                    delete(target, salvage.id, 1)
                    add(target, loot.id, count)
                }
            if (!sorted.success) {
                mes("You don't have enough inventory space to sort any more salvage.")
                return
            }
            statAdvance(STAT_SAILING, type.sortXp * xpMods.get(player, STAT_SAILING))
            delay(SORT_INTERVAL)
        }
    }

    private fun ProtectedAccess.modifyFacility() {
        val boat = boats.boatOf(player) ?: return
        if (!boat.isOwnedBy(player)) {
            mes("Only the owner can modify this boat.")
            return
        }
        if (!boat.type.hookReplacesCargoHold) {
            mes("Your ${boat.type.name.lowercase()} already carries both a cargo hold and a salvaging hook.")
            return
        }
        if (boats.swapRaftFacility(boat, player)) {
            val now = if (player.sailingRaftFacility == RAFT_FACILITY_HOOK) "salvaging hook" else "cargo hold"
            mes("You refit your raft with a $now.")
        }
    }

    private companion object {
        private const val SALVAGE_RANGE = 5
        private const val SALVAGE_INTERVAL = 4
        private const val SORT_INTERVAL = 2
        private const val MAX_LEVEL = 99

        private fun locName(id: Int): String = RSCM.getReverseMapping(RSCMType.LOC, id)

        private fun objName(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)
    }
}
