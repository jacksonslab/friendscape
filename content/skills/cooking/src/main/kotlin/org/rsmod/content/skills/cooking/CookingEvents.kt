package org.rsmod.content.skills.cooking

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.api.script.onOpContentMixedLocU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.FiremakingColoredLogsRow
import org.rsmod.api.table.cooking.CookingFoodsRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import skillSuccess

class CookingEvents @Inject constructor(
    private val xpMods: XpModifiers,
) : PluginScript() {

    private val foods = CookingFoodsRow.all()
    private val coloredLogRows = FiremakingColoredLogsRow.all()

    private val fires = listOf("loc.fire") + coloredLogRows.map { it.fireObject.internalName }
    private val campfires = listOf("loc.forestry_fire") + coloredLogRows.map { it.campfireObject.internalName }

    /** Bonus added to both ends of a food's range burn curve, as on the OSRS wiki burn charts. */
    enum class RangeType(val curveBonus: Int) {
        STANDARD(0),
        LUMBRIDGE(10),
        HOSIDIUS(12),
    }

    private val rangeTypeByContent = mapOf(
        "content.cooking_range_standard" to RangeType.STANDARD,
        "content.cooking_range_lumbridge" to RangeType.LUMBRIDGE,
        "content.cooking_range_hosidius" to RangeType.HOSIDIUS
    )

    private sealed class CookingSurface {
        data class Fire(val locInternal: String) : CookingSurface()

        data class Range(val rangeType: RangeType, val locInternal: String) : CookingSurface()
    }

    override fun ScriptContext.startup() {
        val fireAndCamp = fires + campfires
        foods.forEach { food ->
            fireAndCamp.forEach { loc ->
                onOpLocU(loc, food.raw.internalName) {
                    cookUsedFood(food, CookingSurface.Fire(loc))
                }
            }
        }
        rangeTypeByContent.forEach { (content, rangeType) ->
            foods.forEach { food ->
                onOpContentMixedLocU(content, food.raw.internalName) {
                    val surface = CookingSurface.Range(rangeType, it.type.internalName)
                    if (canUseRange(surface)) cookUsedFood(food, surface)
                }
            }
        }

        fires.forEach { loc ->
            onOpLoc1(loc) {
                openCookingMenu(CookingSurface.Fire(it.type.internalName))
            }
        }
        rangeTypeByContent.forEach { (content, rangeType) ->
            onOpContentLoc1(content) {
                val surface = CookingSurface.Range(rangeType, it.type.internalName)
                if (canUseRange(surface)) openCookingMenu(surface)
            }
        }

        onPlayerQueueWithArgs<CookTask>("queue.cooking_cook") { processCookTick(it.args) }
    }

    private suspend fun ProtectedAccess.canUseRange(surface: CookingSurface.Range): Boolean {
        if (surface.rangeType != RangeType.LUMBRIDGE) return true
        if (QuestRequirements.hasCompleted(player, "quest_cooksassistant")) return true
        startDialogue { chatNpcSpecific("Cook", "npc.cook", angry, "Hey, who said you could use that?") }
        return false
    }

    private fun ProtectedAccess.burnCurve(food: CookingFoodsRow, surface: CookingSurface): Pair<Int, Int> {
        val gauntletLow = food.lowGauntlet
        val gauntletHigh = food.highGauntlet
        if (gauntletLow != null && gauntletHigh != null && "obj.gauntlets_of_cooking" in player.worn) {
            return gauntletLow + rangeBonus(food, surface) to gauntletHigh + rangeBonus(food, surface)
        }
        if (surface is CookingSurface.Fire) {
            return food.low to food.high
        }
        val low = (food.lowRange ?: food.low) + rangeBonus(food, surface)
        val high = (food.highRange ?: food.high) + rangeBonus(food, surface)
        return low to high
    }

    private fun rangeBonus(food: CookingFoodsRow, surface: CookingSurface): Int {
        if (surface !is CookingSurface.Range) return 0
        // The Lumbridge range only helps the low-level foods (below level 30) on OSRS.
        if (surface.rangeType == RangeType.LUMBRIDGE && food.level >= 30) return 0
        return surface.rangeType.curveBonus
    }

    private fun ProtectedAccess.isBurned(food: CookingFoodsRow, surface: CookingSurface): Boolean {
        if (hasCookingCape()) return false
        val (low, high) = burnCurve(food, surface)
        return !skillSuccess(low, high, player.cookingLvl)
    }

    private fun CookingFoodsRow.canCookOn(surface: CookingSurface): Boolean =
        when (this.surface) {
            SURFACE_FIRE_ONLY -> surface is CookingSurface.Fire
            SURFACE_RANGE_ONLY -> surface is CookingSurface.Range
            else -> true
        }

    private fun ProtectedAccess.mesWrongSurface(food: CookingFoodsRow) {
        if (food.surface == SURFACE_FIRE_ONLY) {
            mes("You need to roast that over a fire.")
        } else {
            mes("You need a range to cook that.")
        }
    }

    private fun ProtectedAccess.hasCookingCape(): Boolean {
        return "obj.skillcape_cooking" in player.worn ||
            "obj.skillcape_cooking_trimmed" in player.worn
    }

    private fun cookAnim(surface: CookingSurface) =
        when (surface) {
            is CookingSurface.Range -> "seq.human_cooking"
            is CookingSurface.Fire -> "seq.human_firecooking"
        }

    private suspend fun ProtectedAccess.openCookingMenu(surface: CookingSurface) {
        val cookable =
            foods.filter {
                inv.contains(it.raw.internalName) && player.cookingLvl >= it.level && it.canCookOn(surface)
            }

        if (cookable.isEmpty()) {
            if (surface is CookingSurface.Range) {
                mesbox("You haven't got anything to cook.")
            } else {
                mes("You have nothing to cook on this fire.")
            }
            return
        }

        if (cookable.size == 1 && inv.count(cookable.first().raw.internalName) == 1) {
            cookInstant(cookable.first(), surface)
            return
        }

        if (cookable.size == 1) {
            val food = cookable.first()
            openSkillMulti(SkillMultiConfig(
                verb = "cook",
                entries = listOf(SkillMultiEntry(food.cooked.internalName, listOf(
                    Material(food.raw.internalName),
                ))),
            )) { selection ->
                cookFood(food, surface, selection.amount)
            }
            return
        }

        openSkillMulti(SkillMultiConfig(
            verb = "cook",
            entries = cookable.map { food ->
                SkillMultiEntry(food.cooked.internalName, listOf(
                    Material(food.raw.internalName),
                ))
            },
        )) { selection ->
            val food = cookable.firstOrNull {
                it.cooked.internalName == selection.entry.internal
            } ?: return@openSkillMulti
            cookFood(food, surface, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.cookUsedFood(food: CookingFoodsRow, surface: CookingSurface) {
        if (!food.canCookOn(surface)) {
            mesWrongSurface(food)
            return
        }
        if (player.cookingLvl < food.level || inv.count(food.raw.internalName) <= 1) {
            cookFood(food, surface)
            return
        }
        openSkillMulti(SkillMultiConfig(
            verb = "cook",
            entries = listOf(SkillMultiEntry(food.cooked.internalName, listOf(
                Material(food.raw.internalName),
            ))),
        )) { selection ->
            cookFood(food, surface, selection.amount)
        }
    }

    private fun ProtectedAccess.cookInstant(food: CookingFoodsRow, surface: CookingSurface) {
        anim(cookAnim(surface))
        applyCook(food, surface)
    }

    private fun ProtectedAccess.cookFood(food: CookingFoodsRow, surface: CookingSurface, amount: Int = 1) {
        if (player.cookingLvl < food.level) {
            mes("You need a Cooking level of ${food.level} to cook ${food.raw.name}.")
            return
        }
        anim(cookAnim(surface))
        weakQueue("queue.cooking_cook", food.cookTicks ?: DEFAULT_COOK_TICKS, CookTask(food, surface, amount, 0))
    }

    private fun ProtectedAccess.processCookTick(task: CookTask) {
        val food = task.food

        if (!inv.contains(food.raw.internalName)) return

        if (player.cookingLvl < food.level) {
            mes("You need a Cooking level of ${food.level} to cook ${food.raw.name}.")
            return
        }

        if (!applyCook(food, task.surface)) return

        val cooked = task.cooked + 1
        if (cooked < task.amount && inv.contains(food.raw.internalName)) {
            anim(cookAnim(task.surface))
            val delay = maxOf(food.cookTicks ?: DEFAULT_COOK_TICKS, MIN_REQUEUE_TICKS)
            weakQueue("queue.cooking_cook", delay, CookTask(food, task.surface, task.amount, cooked))
        }
    }

    private fun ProtectedAccess.applyCook(food: CookingFoodsRow, surface: CookingSurface): Boolean {
        val burned = isBurned(food, surface)
        val result = if (burned) food.burnt else food.cooked
        val swapped =
            player.invTransaction(inv) {
                val inventory = select(inv)
                delete { from = inventory; obj = food.raw.id; strictCount = 1 }
                insert { into = inventory; obj = result.id; strictCount = 1 }
                if (food.returnsSpit) {
                    insert { into = inventory; obj = "obj.spit_iron".asRSCM(); strictCount = 1 }
                }
            }.success
        if (!swapped) {
            mes("You don't have enough inventory space to cook that.")
            return false
        }

        if (burned) {
            spam("You accidentally burn the ${food.cooked.name.lowercase()}.")
        } else {
            val xpModifier = xpMods.get(player, "stat.cooking")
            statAdvance("stat.cooking", food.xp / 10.0 * xpModifier)
            spam("You successfully cook the ${food.cooked.name.lowercase()}.")
        }
        return true
    }

    private val CookingFoodsRow.returnsSpit: Boolean
        get() = raw.internalName.startsWith("obj.spit_skewered_")

    private data class CookTask(
        val food: CookingFoodsRow,
        val surface: CookingSurface,
        val amount: Int,
        val cooked: Int,
    )

    private companion object {
        const val DEFAULT_COOK_TICKS = 4

        // A queue added mid-processing is decremented on the same tick, so delay 1 re-fires instantly.
        const val MIN_REQUEUE_TICKS = 2
    }
}
