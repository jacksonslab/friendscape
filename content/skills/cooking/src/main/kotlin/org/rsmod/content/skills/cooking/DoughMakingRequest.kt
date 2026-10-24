package org.rsmod.content.skills.cooking

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.UnboundEvent

class DoughMakingRequest(val access: ProtectedAccess, val water: String) : UnboundEvent {
    var handled: Boolean = false
}
