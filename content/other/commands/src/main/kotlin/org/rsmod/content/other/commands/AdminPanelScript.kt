package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.mechanics.toxins.impl.PlayerDisease
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminInfiniteStamina
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.script.onIfModalButton
import org.rsmod.content.other.commands.AdminPanelInput.plain
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val ADMIN_INTERFACE = "interface.friendscape_admin"

class AdminPanelScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val players: PlayerList,
    private val commands: CheatCommandMap,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onCommand("admin", "Open the Friendscape admin panel", {
            protectedAccess.launch(player) { openPanel() }
        }, aliases = listOf("adminpanel"))

        onIfModalButton("component.friendscape_admin:refresh") { openPanel() }
        onIfModalButton("component.friendscape_admin:players") {
            if (requireAdmin()) listPlayers()
        }
        onIfModalButton("component.friendscape_admin:find") {
            if (requireAdmin()) findPlayer()
        }
        onIfModalButton("component.friendscape_admin:goto") {
            if (requireAdmin()) teleportToPlayer()
        }
        onIfModalButton("component.friendscape_admin:bring") {
            if (requireAdmin()) bringPlayer()
        }
        onIfModalButton("component.friendscape_admin:teleport") {
            if (requireAdmin()) teleportCoordinates()
        }
        onIfModalButton("component.friendscape_admin:spawn") { runCommand("spawn") }
        onIfModalButton("component.friendscape_admin:bank") { runCommand("openbank") }
        onIfModalButton("component.friendscape_admin:npcnames") { runCommand("npcnames") }
        onIfModalButton("component.friendscape_admin:stamina") {
            if (requireAdmin()) {
                player.adminInfiniteStamina = !player.adminInfiniteStamina
                if (player.adminInfiniteStamina) {
                    player.runEnergy = constants.run_max_energy
                    UpdateRun.energy(player, player.runEnergy)
                }
                audit(player, "infinite stamina ${player.adminInfiniteStamina}")
                player.mes("Infinite stamina ${if (player.adminInfiniteStamina) "enabled" else "disabled"}.")
                openPanel()
            }
        }
        onIfModalButton("component.friendscape_admin:god") {
            runCommand("god")
            openPanel()
        }
        onIfModalButton("component.friendscape_admin:maxhit") {
            runCommand("maxhit")
            openPanel()
        }
        onIfModalButton("component.friendscape_admin:restore") {
            if (requireAdmin()) {
                statRestore("stat.hitpoints")
                statRestore("stat.prayer")
                PlayerPoison.clear(player)
                PlayerVenom.clear(player)
                PlayerDisease.clear(player)
                audit(player, "restore")
                player.mes("Hitpoints and prayer restored; poison, venom and disease cleared.")
                openPanel()
            }
        }
        onIfModalButton("component.friendscape_admin:broadcast") {
            if (requireAdmin()) broadcast()
        }
    }

    private fun ProtectedAccess.requireAdmin(): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) return true
        ifClose()
        player.mes("This panel requires Administrator rights.")
        return false
    }

    private fun ProtectedAccess.openPanel() {
        if (!requireAdmin()) return
        ifOpenMainModal(ADMIN_INTERFACE)
        ifSetText("component.friendscape_admin:status", "Online: ${players.count()} | Position: ${player.coords.x}, ${player.coords.z}, ${player.coords.level}")
        ifSetText("component.friendscape_admin:toggles", "God: ${if (player.adminGodMode) "ON" else "OFF"} | Max hit: ${if (player.adminMaxHit) "ON" else "OFF"} | Stamina: ${if (player.adminInfiniteStamina) "ON" else "OFF"}")
    }

    private fun ProtectedAccess.runCommand(command: String) {
        if (!requireAdmin()) return
        ifClose()
        if (commands.execute(player, command, emptyList())) {
            audit(player, command)
        } else {
            player.mes("That tool is unavailable in this world.")
        }
    }

    private suspend fun ProtectedAccess.listPlayers() {
        ifClose()
        val online = players.sortedBy { it.displayName.lowercase() }
        if (online.isEmpty()) return
        var page = 0
        while (requireAdmin()) {
            val slice = online.drop(page * 20).take(20)
            val labels = slice.map { plain(it.displayName) } +
                listOfNotNull(if (page > 0) "Previous page" else null,
                    if ((page + 1) * 20 < online.size) "Next page" else null, "Back to admin panel")
            val choice = menu("Online players (${online.size}) - Page ${page + 1}", hotkeys = false, choices = labels)
            if (!requireAdmin()) return
            if (choice in slice.indices) {
                showPlayer(slice[choice])
                return
            }
            when (labels.getOrNull(choice)) {
                "Previous page" -> page--
                "Next page" -> page++
                else -> { openPanel(); return }
            }
        }
    }

    private suspend fun ProtectedAccess.selectPlayer(): Player? {
        ifClose()
        val name = stringDialog("Enter the online player's exact name:").trim()
        if (!requireAdmin()) return null
        val target = players.firstOrNull {
            it.displayName.equals(name, ignoreCase = true) || it.username.equals(name, ignoreCase = true)
        }
        if (target == null) {
            player.mes("That player is not online.")
            openPanel()
        }
        return target
    }

    private suspend fun ProtectedAccess.findPlayer() {
        val target = selectPlayer() ?: return
        showPlayer(target)
    }

    private fun ProtectedAccess.showPlayer(target: Player) {
        if (players.none { it === target }) {
            player.mes("That player has logged out.")
        } else {
            player.mes("${plain(target.displayName)} | Rank: ${target.modLevel} | Position: ${target.coords.x}, ${target.coords.z}, ${target.coords.level}")
        }
        openPanel()
    }

    private suspend fun ProtectedAccess.teleportToPlayer() {
        val target = selectPlayer() ?: return
        ifClose()
        telejump(target.coords, TeleportType.Exempt)
        audit(player, "goto ${target.username}")
        openPanel()
    }

    private suspend fun ProtectedAccess.bringPlayer() {
        val target = selectPlayer() ?: return
        if (target === player) { openPanel(); return }
        val answer = menu("Bring ${plain(target.displayName)} to your position?", hotkeys = false, choices = listOf("Cancel", "Bring player"))
        if (!requireAdmin()) return
        if (answer == 1 && players.any { it === target }) {
            val destination = player.coords
            val actor = player
            val moved = protectedAccess.launch(target, "An administrator tried to bring you, but you are busy.") {
                if (actor.modLevel.isAtLeast(Rights.ADMINISTRATOR) && players.any { it === actor }) {
                    ifClose()
                    telejump(destination, TeleportType.Exempt)
                    player.mes("An administrator teleported you to their position.")
                    audit(actor, "bring ${target.username}")
                }
            }
            if (!moved) player.mes("That player is busy. Try again when they are free.")
        }
        openPanel()
    }

    private suspend fun ProtectedAccess.teleportCoordinates() {
        ifClose()
        val input = stringDialog("Enter coordinates: x z plane (for example: 3222 3218 0)")
        if (!requireAdmin()) return
        val destination = AdminPanelInput.coordinates(input)
        if (destination == null) {
            player.mes("Enter x and z from 0 to 16383 and a plane from 0 to 3.")
        } else {
            ifClose()
            telejump(destination, TeleportType.Exempt)
            audit(player, "teleport ${destination.x} ${destination.z} ${destination.level}")
        }
        openPanel()
    }

    private suspend fun ProtectedAccess.broadcast() {
        ifClose()
        val message = plain(stringDialog("Broadcast message (1-160 characters):").trim())
        if (!requireAdmin()) return
        if (message.isEmpty() || message.length > 160) {
            player.mes("Broadcasts must contain 1 to 160 characters.")
            openPanel()
            return
        }
        val answer = menu("Send to everyone: $message", hotkeys = false, choices = listOf("Cancel", "Send broadcast"))
        if (!requireAdmin()) return
        if (answer == 1) {
            for (target in players) target.mes("<col=ff981f>[Friendscape]</col> $message")
            audit(player, "broadcast $message")
        }
        openPanel()
    }

    private fun audit(actor: Player, action: String) {
        logger.info { "Admin panel: actor=${plain(actor.username)} action=${plain(action)}" }
    }
}
