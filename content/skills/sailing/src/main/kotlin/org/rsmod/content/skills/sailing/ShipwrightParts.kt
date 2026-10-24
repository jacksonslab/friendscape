package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.sailing.FriendscapeSailingPartsRow
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ShipwrightParts @Inject constructor(private val xpMods: XpModifiers, private val shipbuilding: Shipbuilding) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.shipwright_workbench") { craft() }
        onOpLoc1("loc.shipwright_workbench_small") { craft() }
        for (id in dev.openrune.ServerCacheManager.getNpcs().keys) {
            val name = runCatching { dev.openrune.rscm.RSCM.getReverseMapping(dev.openrune.rscm.RSCMType.NPC, id) }.getOrNull() ?: continue
            if (name.startsWith("npc.sailing_shipwright_")) {
                onOpNpc3(name) { shipbuilding.commission(this) }
            }
        }
        for (recipe in FriendscapeSailingPartsRow.all().filter { it.anvil == true }) {
            onOpLocCategoryU("category.anvil", recipe.input.internalName) { craft(recipe) }
        }
    }

    private suspend fun ProtectedAccess.craft(selected: FriendscapeSailingPartsRow? = null) {
        val recipes = selected?.let(::listOf) ?: FriendscapeSailingPartsRow.all().filter { !it.anvil }
        val entries = recipes.map { SkillMultiEntry(it.output.internalName, listOf(Material(it.input.internalName, it.count))) }
        val byEntry = entries.zip(recipes).toMap()
        openSkillMulti(SkillMultiConfig(verb = "make", entries = entries)) { selection ->
            val recipe = byEntry[selection.entry] ?: return@openSkillMulti
            repeat(selection.amount) {
                if (!canMake(this, recipe)) {
                    val tools = if (recipe.anvil) "a hammer" else "a hammer and a saw"
                    mes("You need $tools, ${recipe.skill.displayName} ${recipe.level}, and ${recipe.count} ${recipe.input.name}.")
                    resetAnim()
                    return@openSkillMulti
                }
                anim(if (recipe.anvil) "seq.human_smithing" else "seq.human_poh_build")
                delay(if (recipe.anvil) 5 else 4)
                if (!make(this, recipe)) {
                    resetAnim()
                    return@openSkillMulti
                }
                val skill = recipe.skill.internalName
                statAdvance(skill, recipe.xpTenths / 10.0 * xpMods.get(player, skill))
                spam("You make ${recipe.output.name.lowercase()}.")
            }
            resetAnim()
        }
    }

    fun make(access: ProtectedAccess, recipe: FriendscapeSailingPartsRow): Boolean = with(access) {
        if (!canMake(access, recipe)) return false
        player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, recipe.input.id, recipe.count)
            add(inventory, recipe.output.id, 1)
        }.success
    }

    private fun canMake(access: ProtectedAccess, recipe: FriendscapeSailingPartsRow): Boolean = with(access) {
        val hammer = inv.contains("obj.hammer") || inv.contains("obj.imcando_hammer") || worn.contains("obj.imcando_hammer") || worn.contains("obj.imcando_hammer_offhand")
        val saw = inv.contains("obj.poh_saw") || inv.contains("obj.wearable_saw") || worn.contains("obj.wearable_saw")
        stat(recipe.skill.internalName) >= recipe.level && hammer && (recipe.anvil || saw) && inv.count(recipe.input.internalName) >= recipe.count
    }
}
