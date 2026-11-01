package org.rsmod.content.interfaces.trade

import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

@Singleton
class TradeSessions {
    private val requests = mutableMapOf<Player, Request>()
    private val sessions = mutableMapOf<Player, TradeSession>()

    fun request(from: Player, to: Player, cycle: Int) {
        requests[from] = Request(to, cycle)
    }

    fun hasRequested(from: Player, to: Player, cycle: Int): Boolean {
        val request = requests[from] ?: return false
        return request.target == to && cycle - request.cycle <= REQUEST_CYCLES
    }

    fun start(first: Player, second: Player): TradeSession {
        requests.remove(first)
        requests.remove(second)
        val session = TradeSession(first, second)
        sessions[first] = session
        sessions[second] = session
        return session
    }

    operator fun get(player: Player): TradeSession? = sessions[player]

    fun end(session: TradeSession) {
        sessions.remove(session.first)
        sessions.remove(session.second)
    }

    fun forget(player: Player) {
        requests.remove(player)
        requests.entries.removeIf { it.value.target == player }
    }

    private data class Request(val target: Player, val cycle: Int)

    private companion object {
        const val REQUEST_CYCLES = 100
    }
}

class TradeSession(val first: Player, val second: Player) {
    var stage: TradeStage = TradeStage.Offer
    private val accepted = mutableSetOf<Player>()

    fun partner(player: Player): Player = if (player === first) second else first

    fun accept(player: Player): Boolean {
        accepted += player
        return accepted.size == 2
    }

    fun hasAccepted(player: Player): Boolean = player in accepted

    fun resetAccepts() {
        accepted.clear()
    }
}

enum class TradeStage {
    Offer,
    Confirm,
    Done,
}
