package org.rsmod.content.skills.farming

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

open class FarmingClock @Inject constructor() {
    open fun millis(): Long = System.currentTimeMillis()
}

/**
 * Reads, grows and transmits a player's patches. Patches persist in server varps; the client only
 * ever sees the `farming_transmit_*` varbits of the one farm the player stands in.
 */
class FarmingPatches @Inject constructor(private val clock: FarmingClock, private val random: GameRandom) {
    fun state(player: Player, patch: Patch): PatchState = PatchState.unpack(player.vars[patch.varp])

    fun crop(state: PatchState): Crop? = FarmingData.crop(state.cropId)

    fun store(player: Player, patch: Patch, state: PatchState) {
        VarPlayerIntMapSetter.set(player, patch.varp, state.pack())
        if (player.vars[AREA_SENT] == patch.area) {
            sendPatch(player, patch, state)
        }
    }

    fun nowTick(): Long = PatchGrowth.tickOf(clock.millis())

    /**
     * Catches every patch up to the current farming tick. The first call for a character only
     * starts its clock: there is nothing planted yet to grow.
     */
    fun update(player: Player) {
        val now = nowTick()
        val last = player.vars[GROWTH_TICK].toLong()
        if (last == 0L) {
            VarPlayerIntMapSetter.set(player, GROWTH_TICK, now.toInt())
            return
        }
        if (now <= last) {
            return
        }
        val patches = FarmingData.patches
        val before = patches.associateWith { state(player, it) }
        for (patch in patches) {
            val current = before.getValue(patch)
            val crop = crop(current)
            val protectedByFlower = crop != null && flowerProtects(patch, crop, before)
            val grown =
                PatchGrowth.grow(current, crop, last, now, protectedByFlower) { chance ->
                    random.of(128) < chance
                }
            if (grown != current) {
                store(player, patch, grown)
            }
        }
        VarPlayerIntMapSetter.set(player, GROWTH_TICK, now.toInt())
        growSeedlings(player)
    }

    fun transmit(player: Player, force: Boolean = false) {
        val area = FarmingData.areaAt(player.coords)?.index ?: 0
        if (!force && player.vars[AREA_SENT] == area) {
            return
        }
        VarPlayerIntMapSetter.set(player, AREA_SENT, area)
        for (patch in FarmingData.patchesIn(area)) {
            sendPatch(player, patch, state(player, patch))
        }
    }

    private fun sendPatch(player: Player, patch: Patch, state: PatchState) {
        val varbit = patch.transmit ?: return
        VarPlayerIntMapSetter.set(player, varbit, PatchTransmit.value(state, crop(state)))
    }

    /** A fully grown flower guards the allotments beside it; white lilies guard every crop. */
    private fun flowerProtects(patch: Patch, crop: Crop, states: Map<Patch, PatchState>): Boolean {
        if (crop.kind != PatchKind.Allotment) {
            return false
        }
        val flowerPatch = FarmingData.patchesIn(patch.area).firstOrNull { it.kind == PatchKind.Flower }
        val flower = flowerPatch?.let(states::get) ?: return false
        val flowerCrop = crop(flower) ?: return false
        val grown = flower.stage >= flowerCrop.grownStage && flower.health == Health.Healthy
        return grown && (flowerCrop.id == crop.protection || flowerCrop.name == WHITE_LILY)
    }

    /** Watered seedlings sprout into saplings on the next farming tick, carried or banked. */
    private fun growSeedlings(player: Player) {
        for (crop in FarmingData.crops) {
            val watered = crop.wateredSeedling ?: continue
            for (inv in listOf(player.inv, player.invMap.getOrPut(BANK))) {
                val count = inv.count(watered)
                if (count == 0) {
                    continue
                }
                player.invTransaction(inv) {
                    val target = select(inv)
                    delete {
                        from = target
                        obj = watered.asRSCM(RSCMType.OBJ)
                        strictCount = count
                    }
                    insert {
                        into = target
                        obj = crop.planted.asRSCM(RSCMType.OBJ)
                        strictCount = count
                    }
                }
            }
        }
    }

    companion object {
        const val GROWTH_TICK: String = "varp.farming_growth_tick"
        const val AREA_SENT: String = "varp.farming_area_sent"
        const val BANK: String = "inv.bank"
        private const val WHITE_LILY = "white lily"
    }
}
