package org.rsmod.api.game.process.player

import org.rsmod.game.entity.Player
import org.rsmod.map.zone.ZoneKey

internal fun Player.consumeZoneRebuild() {
    if (!regionRebuildPending) return
    regionRebuildPending = false
    visibleZoneKeys.clear()
    lastProcessedZone = ZoneKey.NULL
    lastProcessedViewZone = ZoneKey.NULL
}
