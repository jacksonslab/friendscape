package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public fun interface PlayerDeathDropLocationHook {
    public fun dropLocation(player: Player): CoordGrid?
}
