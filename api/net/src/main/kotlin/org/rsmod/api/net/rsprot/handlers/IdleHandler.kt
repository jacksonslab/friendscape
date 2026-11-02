package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.misc.client.Idle
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player

/**
 * The client reports idle after its own five-minute timer and keeps repeating it while the player
 * stays idle, so the logout waits until the server has seen no input for the configured minutes.
 */
class IdleHandler @Inject constructor(private val config: ServerConfig) : MessageHandler<Idle> {
    override fun handle(player: Player, message: Idle) {
        if (!config.gameplay.idleLogout) {
            return
        }
        if (player.lastInputMapClock == 0) {
            player.lastInputMapClock = player.currentMapClock
        }
        val idleTicks = player.currentMapClock - player.lastInputMapClock
        if (idleTicks < config.gameplay.idleLogoutMinutes * TICKS_PER_MINUTE) {
            return
        }
        if (!player.pendingLogout && !player.loggingOut) {
            player.manualLogout = true
            player.idleLogout = true
        }
    }

    private companion object {
        const val TICKS_PER_MINUTE = 100
    }
}
