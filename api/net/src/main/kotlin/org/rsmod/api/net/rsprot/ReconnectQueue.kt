package org.rsmod.api.net.rsprot

import java.util.concurrent.ConcurrentLinkedQueue
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import org.rsmod.game.entity.Player

/** Reconnect requests arrive on netty threads; [NetworkScript] answers them on the game thread. */
internal object ReconnectQueue {
    class Request(
        val responseHandler: GameLoginResponseHandler<Player>,
        val block: LoginBlock<XteaKey>,
    )

    private val pending = ConcurrentLinkedQueue<Request>()

    fun add(request: Request) {
        pending.add(request)
    }

    fun poll(): Request? = pending.poll()
}
