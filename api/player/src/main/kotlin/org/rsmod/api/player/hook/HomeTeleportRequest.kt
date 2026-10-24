package org.rsmod.api.player.hook

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.UnboundEvent

public class HomeTeleportRequest(public val access: ProtectedAccess) : UnboundEvent {
    public var handled: Boolean = false
}
