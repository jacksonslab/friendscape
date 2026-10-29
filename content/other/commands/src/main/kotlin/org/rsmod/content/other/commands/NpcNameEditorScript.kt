package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.GlobalNpcNames
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NpcNameEditorScript @Inject constructor(
    private val editor: GlobalNpcNameEditor,
    private val access: ProtectedAccessLauncher,
    private val npcs: NpcList,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        editor.load()
        onCommand("npcnames", "Edit NPC names globally, including future spawns", {
            access.launch(player) { browse() }
        })
    }

    private fun ProtectedAccess.isAdmin(): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) return true
        ifClose()
        return false
    }

    private suspend fun ProtectedAccess.browse() {
        ifClose()
        while (isAdmin()) {
            val choice = menu("Global NPC names", hotkeys = false,
                choices = listOf("Search NPC names or ID", "NPCs nearby", "Saved name overrides", "Close"))
            if (!isAdmin()) return
            val matches = when (choice) {
                0 -> {
                    val query = stringDialog("Search an NPC name or numeric ID:").trim()
                    if (!isAdmin()) return
                    if (query.isEmpty()) continue
                    val id = query.toIntOrNull()
                    ServerCacheManager.getNpcs().values.filter {
                        if (id != null) it.id == id else display(it).contains(query, true) ||
                            it.name.contains(query, true) || it.internalName.contains(query, true)
                    }
                }
                1 -> npcs.filter { it.coords.level == player.coords.level &&
                    it.coords.chebyshevDistance(player.coords) <= 15 }
                    .map { it.type }.distinctBy { it.id }
                2 -> editor.savedIds().mapNotNull { ServerCacheManager.getNpc(it) }
                else -> return
            }.sortedWith(compareBy({ display(it).lowercase() }, { it.id }))
            if (matches.isEmpty()) {
                player.mes("No NPC types found.")
                continue
            }
            choose(matches)
        }
    }

    private suspend fun ProtectedAccess.choose(matches: List<NpcServerType>) {
        var page = 0
        while (isAdmin()) {
            val slice = matches.drop(page * 20).take(20)
            val labels = slice.map { "${display(it)} (#${it.id})" } +
                listOfNotNull(if (page > 0) "Previous page" else null,
                    if ((page + 1) * 20 < matches.size) "Next page" else null, "Back")
            val choice = menu("NPC types - Page ${page + 1}", hotkeys = false, choices = labels)
            if (!isAdmin()) return
            if (choice in slice.indices) {
                edit(slice[choice])
                return
            }
            when (labels.getOrNull(choice)) {
                "Previous page" -> page--
                "Next page" -> page++
                else -> return
            }
        }
    }

    private suspend fun ProtectedAccess.edit(type: NpcServerType) {
        player.mes("NPC #${type.id} | Current: ${display(type)} | Default: ${type.name}")
        val choice = menu("Change every NPC of type #${type.id}", hotkeys = false,
            choices = listOf("Rename globally", "Restore default name", "Back"))
        if (!isAdmin() || choice !in 0..1) return
        val name = if (choice == 0) {
            val input = stringDialog("New global NPC name (1-40 characters):")
            if (!isAdmin()) return
            try { NpcNameStore.validateName(input) } catch (e: IllegalArgumentException) {
                player.mes(e.message ?: "Invalid NPC name.")
                return
            }
        } else null
        val confirmed = menu("Set every NPC #${type.id} to '${name ?: type.name}'?", hotkeys = false,
            choices = listOf("Cancel", "Save globally"))
        if (!isAdmin() || confirmed != 1) return
        try {
            if (name == null) editor.restore(type.id) else editor.rename(type.id, name)
            player.mes("Saved. Existing and future NPCs of this type now use '${name ?: type.name}'.")
            logger.info { "Global NPC name: actor=${player.username} npc=${type.id} name=${name ?: "[default]"}" }
        } catch (e: Exception) {
            player.mes("Could not save the NPC name. Check the server log.")
            logger.error(e) { "Could not save NPC name for type ${type.id}" }
        }
    }

    private fun display(type: NpcServerType): String =
        (GlobalNpcNames.get(type.id) ?: type.name).replace('|', '/')
}
