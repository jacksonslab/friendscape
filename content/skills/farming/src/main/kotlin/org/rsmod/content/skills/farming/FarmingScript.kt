package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FarmingScript
@Inject
constructor(
    private val patches: FarmingPatches,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (patch in FarmingData.patches) {
            onOpLoc1(patch.loc) { interact(patch) }
            onOpLoc2(patch.loc) { inspect(patch) }
            onOpLoc4(patch.loc) { mes("A guide to every crop is in the Farming skill guide.") }
            onOpLocU(patch.loc) { useOn(patch, it.objType.internalName) }
        }

        for (crop in FarmingData.crops) {
            val seed = crop.seed ?: continue
            val seedling = crop.seedling ?: continue
            onOpHeldU(seed, PLANTPOT_FILLED) { sow(crop) }
            for (can in WATERING_CANS.drop(1)) {
                onOpHeldU(seedling, can) { waterSeedling(crop, can) }
            }
        }

        onPlayerLogin {
            patches.update(player)
            patches.transmit(player, force = true)
        }

        onPlayerCoordsChanged {
            patches.update(player)
            patches.transmit(player)
        }
    }

    private fun ProtectedAccess.state(patch: Patch): PatchState = patches.state(player, patch)

    private fun ProtectedAccess.store(patch: Patch, state: PatchState) {
        patches.store(player, patch, state)
    }

    private fun ProtectedAccess.xp(amount: Double) {
        if (amount > 0) {
            statAdvance(STAT, amount * xpMods.get(player, STAT))
        }
    }

    private fun ProtectedAccess.has(obj: String): Boolean = inv.count(obj) > 0

    private fun ProtectedAccess.hasSecateurs(): Boolean = has(SECATEURS) || has(MAGIC_SECATEURS)

    suspend fun ProtectedAccess.interact(patch: Patch) {
        patches.update(player)
        val state = state(patch)
        val crop = patches.crop(state)
        when {
            crop == null -> if (state.clear) mes(EMPTY_PATCH) else rake(patch)
            state.stump || state.health == Health.Dead -> clear(patch)
            state.health == Health.Diseased -> if (crop.kind.tree) prune(patch) else cure(patch)
            state.stage < crop.grownStage -> mes("The ${crop.name} is still growing.")
            crop.kind == PatchKind.Tree -> if (state.checked) chop(patch, crop) else checkHealth(patch, crop)
            crop.kind == PatchKind.FruitTree ->
                when {
                    !state.checked -> checkHealth(patch, crop)
                    state.lives > 0 -> pickFruit(patch, crop)
                    else -> chopFruitTree(patch, crop)
                }
            else -> harvest(patch, crop)
        }
    }

    suspend fun ProtectedAccess.useOn(patch: Patch, obj: String) {
        patches.update(player)
        val state = state(patch)
        val crop = patches.crop(state)
        val planted = FarmingData.planted(obj)
        val compost = Compost.byObj(obj)
        when {
            planted != null -> plant(patch, planted)
            compost != null -> applyCompost(patch, compost)
            obj == RAKE -> if (crop == null && !state.clear) rake(patch) else mes(NOTHING_TO_RAKE)
            obj == SPADE -> dig(patch)
            obj == PLANT_CURE -> cure(patch)
            obj == SECATEURS || obj == MAGIC_SECATEURS -> prune(patch)
            obj in WATERING_CANS -> water(patch, obj)
            obj == PLANTPOT_EMPTY -> fillPlantPot(patch)
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.inspect(patch: Patch) {
        patches.update(player)
        val state = state(patch)
        val crop = patches.crop(state)
        val kind =
            when (patch.kind) {
                PatchKind.Allotment -> "an allotment"
                PatchKind.Flower -> "a flower patch"
                PatchKind.Herb -> "a herb patch"
                PatchKind.Tree -> "a tree patch"
                PatchKind.FruitTree -> "a fruit tree patch"
            }
        val soil =
            when (state.compost) {
                Compost.None -> "The soil has not been treated."
                Compost.Normal -> "The soil has been treated with compost."
                Compost.Super -> "The soil has been treated with supercompost."
                Compost.Ultra -> "The soil has been treated with ultracompost."
            }
        mes("This is $kind. $soil")
        mes(
            when {
                crop == null && state.clear -> "The patch is empty and weeded."
                crop == null -> "The patch needs weeding."
                state.stump -> "The ${crop.name} tree has been chopped down; only the stump remains."
                state.health == Health.Dead -> "The ${crop.name} has died."
                state.health == Health.Diseased -> "The ${crop.name} is diseased."
                state.stage >= crop.grownStage -> "The ${crop.name} is fully grown."
                else -> "The ${crop.name} is growing (stage ${state.stage + 1} of ${crop.stages})."
            }
        )
        if (state.protected) {
            mes("A gardener is looking after this patch.")
        }
    }

    private suspend fun ProtectedAccess.rake(patch: Patch) {
        if (!has(RAKE)) {
            mes("You need a rake to clear this patch.")
            return
        }
        while (true) {
            val before = state(patch)
            if (!before.empty || before.clear) {
                break
            }
            anim(ANIM_RAKE)
            delay(RAKE_TICKS)
            val current = state(patch)
            if (!current.empty || current.clear || !has(RAKE)) {
                break
            }
            if (invAdd(inv, WEEDS, 1).failure) {
                mes("You don't have enough inventory space to hold any more weeds.")
                break
            }
            store(patch, current.copy(raked = current.raked + 1))
            xp(RAKE_XP)
        }
        resetAnim()
        if (state(patch).let { it.empty && it.clear }) {
            spam("You successfully clear the patch.")
        }
    }

    private suspend fun ProtectedAccess.plant(patch: Patch, crop: Crop) {
        val state = state(patch)
        val sapling = crop.kind.tree
        when {
            crop.kind != patch.kind -> {
                mes("You can't plant ${if (sapling) "that sapling" else "${crop.name} seeds"} here.")
                return
            }
            !state.empty -> {
                mes("There is already something growing in this patch.")
                return
            }
            !state.clear -> {
                mes("This patch needs weeding first.")
                return
            }
            player.farmingLvl < crop.level -> {
                mes("You need a Farming level of ${crop.level} to plant that.")
                return
            }
            sapling && !has(SPADE) -> {
                mes("You need a spade to plant the sapling.")
                return
            }
            !sapling && !has(DIBBER) -> {
                mes("You need a seed dibber to plant seeds here.")
                return
            }
            inv.count(crop.planted) < crop.plantedCount -> {
                mes("You need ${crop.plantedCount} seeds to plant ${patch.kind.article()}.")
                return
            }
        }
        anim(if (sapling) ANIM_DIG else ANIM_DIBBER)
        delay(PLANT_TICKS)
        val current = state(patch)
        if (!current.empty || !current.clear) {
            resetAnim()
            return
        }
        val taken =
            if (sapling) {
                invReplace(inv, crop.planted, 1, PLANTPOT_EMPTY)
            } else {
                invDel(inv, crop.planted, crop.plantedCount)
            }
        if (taken.failure) {
            resetAnim()
            return
        }
        store(patch, PatchState(cropId = crop.id, raked = PatchState.RAKED, compost = current.compost))
        xp(crop.plantXp)
        resetAnim()
        spam(
            if (sapling) "You plant the ${crop.name} sapling."
            else "You plant ${crop.plantedCount.words()} ${crop.name} seed${if (crop.plantedCount > 1) "s" else ""}."
        )
    }

    private suspend fun ProtectedAccess.applyCompost(patch: Patch, compost: Compost) {
        val state = state(patch)
        val crop = patches.crop(state)
        when {
            !state.clear -> {
                mes("This patch needs weeding first.")
                return
            }
            state.compost != Compost.None -> {
                mes("This patch has already been treated with compost.")
                return
            }
            crop != null && (state.stage >= crop.grownStage || state.health != Health.Healthy) -> {
                mes("There's no point treating this patch now.")
                return
            }
        }
        anim(ANIM_POUR)
        delay(COMPOST_TICKS)
        val current = state(patch)
        if (current.compost != Compost.None || !current.clear) {
            resetAnim()
            return
        }
        if (invReplace(inv, compost.obj, 1, BUCKET).failure) {
            resetAnim()
            return
        }
        store(patch, current.copy(compost = compost))
        xp(compost.xp)
        resetAnim()
        spam("You treat the patch with ${compost.name.lowercase().let { if (it == "normal") "" else it }}compost.")
    }

    private suspend fun ProtectedAccess.water(patch: Patch, can: String) {
        val state = state(patch)
        val crop = patches.crop(state)
        val charges = WATERING_CANS.indexOf(can)
        when {
            charges <= 0 -> mes("This watering can is empty.")
            crop == null -> mes("There's nothing growing here to water.")
            !crop.waterable -> mes("This patch doesn't need watering.")
            state.health != Health.Healthy -> mes("Water won't cure the crops.")
            state.stage >= crop.grownStage -> mes("The ${crop.name} is already fully grown.")
            state.watered -> mes("This patch has already been watered.")
            else -> {
                anim(ANIM_WATER)
                delay(WATER_TICKS)
                val current = state(patch)
                if (current.watered || current.cropId != crop.id || current.health != Health.Healthy) {
                    resetAnim()
                    return
                }
                if (invReplace(inv, can, 1, WATERING_CANS[charges - 1]).failure) {
                    resetAnim()
                    return
                }
                store(patch, current.copy(watered = true))
                resetAnim()
                spam("You water the ${crop.name}.")
            }
        }
    }

    private suspend fun ProtectedAccess.cure(patch: Patch) {
        val state = state(patch)
        val crop = patches.crop(state)
        when {
            crop == null || state.health != Health.Diseased -> {
                mes("This patch doesn't need curing.")
                return
            }
            crop.kind.tree -> {
                mes("You need secateurs to prune the diseased leaves.")
                return
            }
            !has(PLANT_CURE) -> {
                mes("You need plant cure to cure the ${crop.name}.")
                return
            }
        }
        anim(ANIM_CURE)
        delay(CURE_TICKS)
        val current = state(patch)
        if (current.health != Health.Diseased || invDel(inv, PLANT_CURE, 1).failure) {
            resetAnim()
            return
        }
        store(patch, current.copy(health = Health.Healthy))
        resetAnim()
        mes("You treat the ${crop!!.name} with the plant cure.")
        mes("It is restored to health.")
    }

    private suspend fun ProtectedAccess.prune(patch: Patch) {
        val crop = patches.crop(state(patch))
        if (crop == null || !crop.kind.tree || state(patch).health != Health.Diseased) {
            mes("This patch doesn't need pruning.")
            return
        }
        if (!hasSecateurs()) {
            mes("You need secateurs to prune the diseased leaves.")
            return
        }
        while (state(patch).health == Health.Diseased) {
            anim(ANIM_PRUNE)
            delay(PRUNE_TICKS)
            val current = state(patch)
            if (current.health != Health.Diseased || !hasSecateurs()) {
                break
            }
            if (random.of(4) == 0) {
                mes("There are still some diseased leaves left.")
                continue
            }
            store(patch, current.copy(health = Health.Healthy))
            mes("You have successfully removed all the diseased leaves.")
        }
        resetAnim()
    }

    private suspend fun ProtectedAccess.clear(patch: Patch) {
        val state = state(patch)
        if (state.empty) {
            mes(EMPTY_PATCH)
            return
        }
        if (!has(SPADE)) {
            mes("You need a spade to clear this patch.")
            return
        }
        anim(ANIM_DIG)
        delay(CLEAR_TICKS)
        val current = state(patch)
        if (current.empty || !(current.stump || current.health == Health.Dead)) {
            resetAnim()
            return
        }
        store(patch, PatchState.EMPTY_CLEAR)
        resetAnim()
        mes("You have successfully cleared this patch for new crops.")
    }

    private suspend fun ProtectedAccess.dig(patch: Patch) {
        val state = state(patch)
        val crop = patches.crop(state)
        when {
            crop == null -> mes(EMPTY_PATCH)
            state.stump || state.health == Health.Dead -> clear(patch)
            crop.kind == PatchKind.Allotment && state.stage >= crop.grownStage && state.health == Health.Healthy ->
                harvest(patch, crop)
            else -> {
                val confirm =
                    choice2(
                        "Yes, I want to clear it for new crops.",
                        true,
                        "No, I'll leave it.",
                        false,
                        title = "Dig up these ${crop.name}?",
                    )
                if (!confirm) {
                    return
                }
                anim(ANIM_DIG)
                delay(CLEAR_TICKS)
                if (state(patch).cropId != crop.id) {
                    resetAnim()
                    return
                }
                store(patch, PatchState.EMPTY_CLEAR)
                resetAnim()
                mes("You have successfully cleared this patch for new crops.")
            }
        }
    }

    /** Allotments, herbs and flowers: pick until the patch's harvest lives run out. */
    private suspend fun ProtectedAccess.harvest(patch: Patch, crop: Crop) {
        if (crop.kind == PatchKind.Allotment && !has(SPADE)) {
            mes("You need a spade to harvest your crops.")
            return
        }
        if (crop.kind == PatchKind.Flower) {
            pickFlowers(patch, crop)
            return
        }
        var picked = 0
        while (true) {
            if (inv.isFull()) {
                mes("You don't have enough inventory space to hold any more ${crop.produceName()}.")
                break
            }
            anim(if (crop.kind == PatchKind.Herb) ANIM_PICK_LOW else ANIM_DIG)
            delay(HARVEST_TICKS)
            val current = state(patch)
            if (!current.harvestable(crop)) {
                break
            }
            if (invAdd(inv, crop.produce, 1).failure) {
                break
            }
            picked++
            xp(crop.harvestXp)
            val save = HarvestLives.saveChance(player.farmingLvl, crop.saveLow, crop.saveHigh, has(MAGIC_SECATEURS))
            val lives = if (random.of(256) < save) current.lives else current.lives - 1
            if (lives <= 0) {
                store(patch, PatchState.EMPTY_CLEAR)
                resetAnim()
                mes("The ${patch.kind.noun()} is now empty.")
                return
            }
            store(patch, current.copy(lives = lives))
        }
        resetAnim()
        if (picked > 0) {
            spam("You harvest some ${crop.produceName()}.")
        }
    }

    private suspend fun ProtectedAccess.pickFlowers(patch: Patch, crop: Crop) {
        if (inv.isFull()) {
            mes("You don't have enough inventory space to pick the ${crop.name}.")
            return
        }
        anim(ANIM_PICK_LOW)
        delay(HARVEST_TICKS)
        val current = state(patch)
        if (!current.harvestable(crop)) {
            resetAnim()
            return
        }
        val rolled = if (crop.name == LIMPWURT) LIMPWURT_BASE + random.of(0, limpwurtBonus()) else 1
        val count = rolled.coerceAtMost(inv.freeSpace())
        if (invAdd(inv, crop.produce, count).failure) {
            resetAnim()
            return
        }
        store(patch, PatchState.EMPTY_CLEAR)
        xp(crop.harvestXp * count)
        resetAnim()
        spam("You pick the ${crop.name}.")
    }

    private fun ProtectedAccess.limpwurtBonus(): Int = (player.farmingLvl / 10 - 1).coerceIn(0, 8)

    private suspend fun ProtectedAccess.checkHealth(patch: Patch, crop: Crop) {
        anim(ANIM_PICK_MID)
        delay(CHECK_TICKS)
        val current = state(patch)
        if (current.cropId != crop.id || current.checked || current.stage < crop.grownStage) {
            resetAnim()
            return
        }
        store(patch, current.copy(checked = true))
        xp(crop.checkXp)
        resetAnim()
        mes("You examine the tree for signs of disease and find that it is in perfect health.")
    }

    /** Farmed trees chop like the wild tree of the same kind; [Crop.tree] names that tree. */
    private suspend fun ProtectedAccess.chop(patch: Patch, crop: Crop) {
        val tree = crop.tree?.let { ServerCacheManager.getObject(it.asRSCM(RSCMType.LOC)) } ?: return
        val level = with(Woodcutting) { tree.treeLevelReq }
        if (player.woodcuttingLvl < level) {
            mes("You need a Woodcutting level of $level to chop down this tree.")
            return
        }
        val axe = Woodcutting.findAxe(player, tree)
        if (axe == null) {
            mes("You need an axe to chop down this tree.")
            return
        }
        val anim = RSCM.getReverseMapping(RSCMType.SEQ, with(Woodcutting) { getInvObj(axe).axeWoodcuttingAnim.id })
        spam("You swing your axe at the tree.")
        while (true) {
            if (inv.isFull()) {
                mes("Your inventory is too full to hold any more logs.")
                break
            }
            anim(anim)
            delay(CHOP_TICKS)
            val current = state(patch)
            if (!current.choppable(crop)) {
                break
            }
            val (low, high) = Woodcutting.cutSuccessRates(tree, axe)
            if (!statRandom("stat.woodcutting", low, high, invisibleLvls)) {
                continue
            }
            if (invAdd(inv, crop.produce, 1).failure) {
                break
            }
            statAdvance("stat.woodcutting", with(Woodcutting) { tree.treeXp } * xpMods.get(player, "stat.woodcutting"))
            spam("You get some ${crop.produceName()}.")
            if (random.of(CHOP_DEPLETE) == 0) {
                store(patch, current.copy(stump = true))
                soundSynth("synth.tree_fall_sound")
                break
            }
        }
        resetAnim()
    }

    private suspend fun ProtectedAccess.chopFruitTree(patch: Patch, crop: Crop) {
        val axe = Woodcutting.findAxe(player, ServerCacheManager.getObject("loc.oaktree".asRSCM(RSCMType.LOC))!!)
        if (axe == null) {
            mes("You need an axe to chop down this tree.")
            return
        }
        anim(RSCM.getReverseMapping(RSCMType.SEQ, with(Woodcutting) { getInvObj(axe).axeWoodcuttingAnim.id }))
        delay(CHOP_TICKS)
        val current = state(patch)
        if (!current.choppable(crop) || current.lives > 0) {
            resetAnim()
            return
        }
        store(patch, current.copy(stump = true))
        resetAnim()
        soundSynth("synth.tree_fall_sound")
        mes("You chop down the ${crop.name} tree.")
    }

    private suspend fun ProtectedAccess.pickFruit(patch: Patch, crop: Crop) {
        while (true) {
            if (inv.isFull()) {
                mes("You don't have enough inventory space to hold any more ${crop.produceName()}.")
                break
            }
            anim(ANIM_PICK_HIGH)
            delay(PICK_FRUIT_TICKS)
            val current = state(patch)
            if (!current.choppable(crop) || current.lives <= 0) {
                break
            }
            if (invAdd(inv, crop.produce, 1).failure) {
                break
            }
            store(patch, current.copy(lives = current.lives - 1))
            xp(crop.harvestXp)
            spam("You pick some ${crop.produceName()}.")
            if (current.lives - 1 <= 0) {
                break
            }
        }
        resetAnim()
    }

    private suspend fun ProtectedAccess.fillPlantPot(patch: Patch) {
        val state = state(patch)
        when {
            !state.empty || !state.clear -> {
                mes("This patch needs to be weeded and empty to fill a plant pot.")
                return
            }
            !has(TROWEL) -> {
                mes("You need a gardening trowel to fill the pot with soil.")
                return
            }
        }
        anim(ANIM_FILL_POT)
        delay(FILL_POT_TICKS)
        if (invReplace(inv, PLANTPOT_EMPTY, 1, PLANTPOT_FILLED).failure) {
            resetAnim()
            return
        }
        resetAnim()
        spam("You fill the plant pot with soil.")
    }

    private suspend fun ProtectedAccess.sow(crop: Crop) {
        val seed = crop.seed ?: return
        val seedling = crop.seedling ?: return
        when {
            !has(TROWEL) -> {
                mes("You need a gardening trowel to sow seeds in a plant pot.")
                return
            }
            player.farmingLvl < crop.level -> {
                mes("You need a Farming level of ${crop.level} to plant that seed.")
                return
            }
        }
        anim(ANIM_FILL_POT)
        delay(FILL_POT_TICKS)
        val result =
            player.invTransaction(inv) {
                val target = select(inv)
                delete(target, seed.asRSCM(RSCMType.OBJ), 1)
                delete(target, PLANTPOT_FILLED.asRSCM(RSCMType.OBJ), 1)
                add(target, seedling.asRSCM(RSCMType.OBJ), 1)
            }
        resetAnim()
        if (result.success) {
            spam("You sow the seed in the plant pot.")
        }
    }

    private suspend fun ProtectedAccess.waterSeedling(crop: Crop, can: String) {
        val seedling = crop.seedling ?: return
        val watered = crop.wateredSeedling ?: return
        val emptier = WATERING_CANS[WATERING_CANS.indexOf(can) - 1]
        anim(ANIM_WATER)
        delay(WATER_TICKS)
        val result =
            player.invTransaction(inv) {
                val target = select(inv)
                delete(target, seedling.asRSCM(RSCMType.OBJ), 1)
                delete(target, can.asRSCM(RSCMType.OBJ), 1)
                add(target, watered.asRSCM(RSCMType.OBJ), 1)
                add(target, emptier.asRSCM(RSCMType.OBJ), 1)
            }
        resetAnim()
        if (result.success) {
            spam("You water the seedling.")
        }
    }

    private fun PatchState.harvestable(crop: Crop): Boolean =
        cropId == crop.id && stage >= crop.grownStage && health == Health.Healthy && lives > 0

    private fun PatchState.choppable(crop: Crop): Boolean =
        cropId == crop.id && stage >= crop.grownStage && health == Health.Healthy && checked && !stump

    private fun Crop.produceName(): String =
        ServerCacheManager.getItem(produce.asRSCM(RSCMType.OBJ))?.name?.lowercase() ?: name

    private fun PatchKind.article(): String =
        when (this) {
            PatchKind.Allotment -> "an allotment"
            else -> "this patch"
        }

    private fun PatchKind.noun(): String =
        when (this) {
            PatchKind.Allotment -> "allotment"
            PatchKind.Herb -> "herb patch"
            PatchKind.Flower -> "flower patch"
            else -> "patch"
        }

    private fun Int.words(): String = if (this == 3) "three" else toString()

    companion object {
        const val STAT = "stat.farming"

        const val RAKE = "obj.rake"
        const val DIBBER = "obj.dibber"
        const val SPADE = "obj.spade"
        const val TROWEL = "obj.gardening_trowel"
        const val SECATEURS = "obj.secateurs"
        const val MAGIC_SECATEURS = "obj.fairy_enchanted_secateurs"
        const val PLANT_CURE = "obj.plant_cure"
        const val WEEDS = "obj.weeds"
        const val BUCKET = "obj.bucket_empty"
        const val PLANTPOT_EMPTY = "obj.plantpot_empty"
        const val PLANTPOT_FILLED = "obj.plantpot_compost"

        val WATERING_CANS: List<String> = (0..8).map { "obj.watering_can_$it" }

        private const val ANIM_RAKE = "seq.farming_raking"
        private const val ANIM_DIBBER = "seq.farming_seed_dibbing"
        private const val ANIM_DIG = "seq.human_dig"
        private const val ANIM_POUR = "seq.farming_pour_water"
        private const val ANIM_WATER = "seq.farming_watering"
        private const val ANIM_CURE = "seq.farming_plant_cure"
        private const val ANIM_PRUNE = "seq.picking_mid"
        private const val ANIM_PICK_LOW = "seq.picking_low"
        private const val ANIM_PICK_MID = "seq.picking_mid"
        private const val ANIM_PICK_HIGH = "seq.picking_high"
        private const val ANIM_FILL_POT = "seq.farming_filling_plantpot"

        private const val RAKE_TICKS = 4
        private const val PLANT_TICKS = 3
        private const val COMPOST_TICKS = 2
        private const val WATER_TICKS = 2
        private const val CURE_TICKS = 3
        private const val PRUNE_TICKS = 3
        private const val CLEAR_TICKS = 3
        private const val HARVEST_TICKS = 2
        private const val CHECK_TICKS = 2
        private const val CHOP_TICKS = 4
        private const val PICK_FRUIT_TICKS = 2
        private const val FILL_POT_TICKS = 2

        private const val RAKE_XP = 4.0
        private const val CHOP_DEPLETE = 8
        private const val LIMPWURT = "limpwurt"
        private const val LIMPWURT_BASE = 3

        private const val EMPTY_PATCH = "This patch is empty and ready for planting."
        private const val NOTHING_TO_RAKE = "This patch doesn't need raking."
    }
}
