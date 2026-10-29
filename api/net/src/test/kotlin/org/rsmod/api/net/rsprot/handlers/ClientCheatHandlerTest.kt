package org.rsmod.api.net.rsprot.handlers

import net.rsprot.protocol.game.incoming.misc.user.ClientCheat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.net.central.logging.CentralActivityLogWriter
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.cheat.CheatHandler
import org.rsmod.game.entity.Player

class ClientCheatHandlerTest {
    @Test
    fun `mixed case command dispatch preserves player name capitalization`() {
        assertArguments("ChAnGeNaMe JaCkSoN", "changename", listOf("JaCkSoN"))
    }

    @Test
    fun `multiword command arguments preserve capitalization and spaces`() {
        assertArguments("changename Sir Jackson", "changename", listOf("Sir", "Jackson"))
    }

    private fun assertArguments(input: String, command: String, expected: List<String>) {
        var received: List<String>? = null
        val commands = CheatCommandMap()
        commands[command] = CheatHandler(null, { received = args }, null)
        val logger = CentralActivityLogWriter(ServerConfig("test", 43594, 241, "test", 1))
        ClientCheatHandler(commands, logger).handle(Player(), ClientCheat(input, false))
        assertEquals(expected, received)
    }
}
