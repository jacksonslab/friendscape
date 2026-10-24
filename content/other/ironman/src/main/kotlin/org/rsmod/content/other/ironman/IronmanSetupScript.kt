package org.rsmod.content.other.ironman

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.ironmanGroupId
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class IronmanSetupScript @Inject constructor(
    private val groups: GroupIronmanService,
    private val players: PlayerList,
    private val clock: MapClock,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {
    private var Player.invitedGroup by intVarp("varp.friendscape_ironman_invite")
    private var Player.inviteExpiry by intVarp("varp.friendscape_ironman_invite_expiry")

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.PrepareLogin> { groups.prepareLogin(player) }
        onIfOpen("interface.ironman_setup") {
            for (button in listOf("none", "im", "uim", "hcim", "gim")) {
                player.ifSetEvents("component.ironman_setup:${button}_button", -1..-1, IfEvent.Op1)
            }
            player.ifSetHide("component.ironman_setup:hcgim_button", true)
            player.ifSetHide("component.ironman_setup:ugim_button", true)
        }
        onIfModalButton("component.ironman_setup:none_button") { choose(PlayerGamemode.NORMAL) }
        onIfModalButton("component.ironman_setup:im_button") { choose(PlayerGamemode.IRONMAN) }
        onIfModalButton("component.ironman_setup:uim_button") { choose(PlayerGamemode.ULTIMATE_IRONMAN) }
        onIfModalButton("component.ironman_setup:hcim_button") { choose(PlayerGamemode.HARDCORE_IRONMAN) }
        onIfModalButton("component.ironman_setup:gim_button") { ifClose(); groupMenu() }
        onCommand("group") {
            desc = "Create, join or manage your Ironman group"
            cheat { launcher.launch(player) { groupMenu() } }
        }
    }

    private suspend fun ProtectedAccess.choose(mode: Int) {
        if (player.ironmanGroupId != 0) {
            mes("Leave your group through ::group before choosing a solo mode.")
            return
        }
        if (!IronmanModeSelection.allowed(player.gamemode, mode, player.canChooseIronman(), player.ironmanGroupId)) {
            mes("After Tutorial Island you can downgrade your Ironman mode, but cannot upgrade it.")
            return
        }
        ifClose()
        startDialogue {
            val name = when (mode) {
                PlayerGamemode.IRONMAN -> "Ironman"
                PlayerGamemode.ULTIMATE_IRONMAN -> "Ultimate Ironman"
                PlayerGamemode.HARDCORE_IRONMAN -> "Hardcore Ironman"
                else -> "Normal"
            }
            if (choice2("Choose $name mode.", true, "Cancel.", false)) {
                if (!IronmanModeSelection.allowed(player.gamemode, mode, player.canChooseIronman(), player.ironmanGroupId)) return@startDialogue
                player.setGamemode(mode)
                access.mes("Your game mode is now $name.")
            }
        }
    }

    private suspend fun ProtectedAccess.groupMenu() {
        startDialogue {
            val group = groups.group(player)
            if (group == null) {
                when (choice3("Create a Group Ironman group.", 1, "Accept my group invitation.", 2, "Cancel.", 3)) {
                    1 -> {
                        if (!player.canChooseIronman()) {
                            access.mes("Create or join your group before leaving Tutorial Island.")
                            return@startDialogue
                        }
                        val name = access.stringDialog("Group name (3 to 20 characters):")
                        access.mes(groups.create(player, name) ?: "Group created. Use ::group to invite your friends.")
                    }
                    2 -> {
                        val id = player.invitedGroup
                        if (id <= 0 || clock.cycle.toInt() > player.inviteExpiry) {
                            player.invitedGroup = 0
                            access.mes("You have no active group invitation.")
                            return@startDialogue
                        }
                        val error = groups.join(player, id)
                        if (error == null) player.invitedGroup = 0
                        access.mes(error ?: "You have joined the group. Shared storage is available from your bank.")
                    }
                }
                return@startDialogue
            }
            when (choice4("Show group members.", 1, "Invite a player.", 2, "Leave this group.", 3, "Cancel.", 4, title = group.name)) {
                1 -> access.mesbox(group.members.joinToString("<br>") { (id, name) -> if (id == group.leader) "$name (leader)" else name })
                2 -> {
                    if (group.leader != player.characterId) {
                        access.mes("Only the group leader can invite players.")
                        return@startDialogue
                    }
                    if (group.members.size >= GroupIronmanService.MAX_MEMBERS) {
                        access.mes("Your group already has five members.")
                        return@startDialogue
                    }
                    val name = access.stringDialog("Invite which player?").trim()
                    val target = players.firstOrNull { it.displayName.equals(name, true) || it.username.equals(name, true) }
                    if (target == null || target === player || target.worldType != player.worldType || !target.canChooseIronman() || target.ironmanGroupId != 0) {
                        access.mes("Invite an online, ungrouped player who is still on Tutorial Island.")
                        return@startDialogue
                    }
                    val current = groups.group(player)
                    if (current?.id != group.id || current.leader != player.characterId) return@startDialogue
                    target.invitedGroup = group.id
                    target.inviteExpiry = clock.cycle.toInt() + 200
                    target.mes("${player.displayName} invited you to ${group.name}. Use ::group to accept within two minutes.")
                    access.mes("Invitation sent to ${target.displayName}.")
                }
                3 -> {
                    if (choice2("Leave and become a normal account.", true, "Keep my Group Ironman status.", false)) {
                        access.mes(groups.leave(player) ?: "You have left the group and become a normal account.")
                    }
                }
            }
        }
    }
}
