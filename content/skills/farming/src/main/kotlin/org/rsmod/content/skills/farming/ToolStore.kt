package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.bits
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * The tool leprechaun's store. Counts live in Jagex's own `farming_tools_*` varbits, which the
 * store interface's clientscripts read directly; a count is split across a base varbit and its
 * "extra" overflow varbits exactly the way `farming_tools_getstored` adds them back up.
 */
enum class FarmingTool(
    val component: String,
    val obj: String,
    val parts: List<Pair<String, Int>>,
) {
    Rake("rake", "obj.rake", listOf("varbit.farming_tools_rake" to 1, "varbit.farming_tools_extrarakes" to 2)),
    Dibber("dibber", "obj.dibber", listOf("varbit.farming_tools_dibber" to 1, "varbit.farming_tools_extradibbers" to 2)),
    Spade("spade", "obj.spade", listOf("varbit.farming_tools_spade" to 1, "varbit.farming_tools_extraspades" to 2)),
    Secateurs(
        "secateurs",
        "obj.secateurs",
        listOf("varbit.farming_tools_secateurs" to 1, "varbit.farming_tools_extrasecateurs" to 2),
    ),
    WateringCan("wateringcan", "obj.watering_can_dummy", listOf("varbit.farming_tools_wateringcan" to 1)),
    Trowel(
        "trowel",
        "obj.gardening_trowel",
        listOf("varbit.farming_tools_trowel" to 1, "varbit.farming_tools_extratrowels" to 2),
    ),
    PlantCure("plantcure", "obj.plant_cure", listOf("varbit.farming_tools_plantcure" to 1)),
    Bucket(
        "bucket",
        "obj.bucket_empty",
        listOf(
            "varbit.farming_tools_buckets" to 1,
            "varbit.farming_tools_extrabuckets" to 32,
            "varbit.farming_tools_extra2buckets" to 256,
        ),
    ),
    Compost(
        "compost",
        "obj.bucket_compost",
        listOf("varbit.farming_tools_compost" to 1, "varbit.farming_tools_extracompost" to 256),
    ),
    Supercompost(
        "supercompost",
        "obj.bucket_supercompost",
        listOf("varbit.farming_tools_supercompost" to 1, "varbit.farming_tools_extrasupercompost" to 256),
    ),
    Ultracompost("ultracompost", "obj.bucket_ultracompost", listOf("varbit.farming_tools_ultracompost" to 1));

    companion object {
        fun byObj(obj: String): FarmingTool? =
            if (obj in ToolStore.WATERING_CANS) WateringCan else entries.firstOrNull { it.obj == obj }
    }
}

object ToolStore {
    /** `enum_136`: stored watering can value -> can, empty can first. */
    val WATERING_CANS: List<String> = (0..8).map { "obj.watering_can_$it" }

    fun stored(player: Player, tool: FarmingTool): Int {
        if (tool == FarmingTool.WateringCan) {
            return if (player.vars[tool.parts.single().first] > 0) 1 else 0
        }
        return tool.parts.sumOf { (varbit, scale) -> player.vars[varbit] * scale }
    }

    /** The can held in the store, or null. Only one can is ever stored. */
    fun storedCan(player: Player): String? {
        val value = player.vars[FarmingTool.WateringCan.parts.single().first]
        return WATERING_CANS.getOrNull(value - 1)
    }

    fun setStoredCan(player: Player, can: String?) {
        val value = if (can == null) 0 else WATERING_CANS.indexOf(can) + 1
        VarPlayerIntMapSetter.set(player, FarmingTool.WateringCan.parts.single().first, value)
    }

    fun setStored(player: Player, tool: FarmingTool, count: Int) {
        require(tool != FarmingTool.WateringCan) { "Use setStoredCan for the watering can." }
        require(count in 0..capacity(tool)) { "$tool count $count out of range." }
        var left = count
        val parts = tool.parts.sortedByDescending { it.second }
        for ((varbit, scale) in parts) {
            val digit = left / scale
            VarPlayerIntMapSetter.set(player, varbit, digit)
            left -= digit * scale
        }
    }

    /**
     * What the store will hold: the store interface's own capacity enum, clamped to what the
     * varbits can encode.
     */
    fun capacity(tool: FarmingTool): Int {
        val encodable =
            tool.parts.sumOf { (varbit, scale) ->
                val type = ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
                val bits = type?.let { it.bits.last - it.bits.first + 1 } ?: 0
                ((1 shl bits) - 1) * scale
            }
        val listed = capacities()[tool.obj.asRSCM(RSCMType.OBJ)] ?: encodable
        return minOf(listed, encodable)
    }

    private fun capacities(): Map<Int, Int> {
        val type = ServerCacheManager.getEnum(CAPACITY_ENUM.asRSCM(RSCMType.ENUM)) ?: return emptyMap()
        return type.values.entries.associate { (it.key as Number).toInt() to (it.value as Number).toInt() }
    }

    private const val CAPACITY_ENUM = "enum.farming_tools_capacity"
}
