package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.sql.SQLException
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Friendscape house rules: everyone is a member, and display names can change any time. */
class FriendscapePerksScript
@Inject
constructor(
    private val db: GameDbManager,
    private val players: PlayerList,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.PrepareLogin> { player.members = true }
        onCommand("changename") {
            desc = "Change your display name (no limit)"
            cheat {
                val typed = args.joinToString(" ").trim()
                if (typed.isNotEmpty()) {
                    requestName(player, typed)
                } else if (!protectedAccess.launch(player) { askForName() }) {
                    player.mes("You're busy. Try again in a moment.")
                }
            }
        }
    }

    private suspend fun ProtectedAccess.askForName() {
        val typed = stringDialog("Enter your new display name:").trim()
        if (typed.isNotEmpty()) {
            requestName(player, typed)
        }
    }

    private fun requestName(player: Player, name: String) {
        val problem = validate(name)
        if (problem != null) {
            player.mes(problem)
            return
        }
        if (name == player.displayName) {
            player.mes("That's already your name.")
            return
        }
        val characterId = player.characterId
        val uid = player.uid
        db.request(
            request = { connection ->
                try {
                    connection
                        .prepareStatement(
                            """
                            UPDATE account_characters
                            SET previous_display_name = display_name, display_name = ?,
                                display_name_changed_at = ?
                            WHERE id = ?
                            """
                                .trimIndent()
                        )
                        .use {
                            it.setString(1, name)
                            it.setLong(2, System.currentTimeMillis())
                            it.setInt(3, characterId)
                            it.executeUpdate()
                        }
                    GameDbResult.Ok(true)
                } catch (e: SQLException) {
                    if (e.sqlState == UNIQUE_VIOLATION) GameDbResult.Ok(false) else throw e
                }
            },
            response = { result ->
                val online = uid.resolve(players) ?: return@request
                when {
                    result is GameDbResult.Ok && result.value -> rename(online, name)
                    result is GameDbResult.Ok -> online.mes("Sorry, '$name' is already taken.")
                    else -> {
                        logger.warn { "Display name change failed for character $characterId: $result" }
                        online.mes("Your name couldn't be changed right now. Please try again.")
                    }
                }
            },
        )
    }

    private fun rename(player: Player, name: String) {
        player.previousDisplayName = player.displayName
        player.displayName = name
        player.displayNameChangedAtMillis = System.currentTimeMillis()
        player.rebuildAppearance()
        player.mes("Your display name is now $name.")
    }

    private fun validate(name: String): String? =
        when {
            name.length > MAX_LENGTH -> "Display names can be at most $MAX_LENGTH characters."
            !NAME_CHARS.matches(name) ->
                "Display names can only use letters, numbers, spaces, hyphens and underscores."
            name.first() in SEPARATORS || name.last() in SEPARATORS ->
                "Display names can't start or end with a space, hyphen or underscore."
            DOUBLE_SEPARATOR.containsMatchIn(name) ->
                "Display names can't have two spaces, hyphens or underscores in a row."
            name.none { it.isLetterOrDigit() } -> "Display names need at least one letter or number."
            else -> null
        }

    private companion object {
        const val MAX_LENGTH = 12
        const val UNIQUE_VIOLATION = "23505"
        const val SEPARATORS = " -_"
        val NAME_CHARS = Regex("[A-Za-z0-9 _-]+")
        val DOUBLE_SEPARATOR = Regex("[ _-]{2}")
    }
}
