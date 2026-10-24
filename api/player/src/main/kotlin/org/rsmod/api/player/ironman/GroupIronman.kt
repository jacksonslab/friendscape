package org.rsmod.api.player.ironman

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

public var Player.ironmanGroupId: Int by intVarp("varp.friendscape_ironman_group")

public val Player.isGroupIronman: Boolean
    get() = gamemode in PlayerGamemode.GROUP_IRONMAN..PlayerGamemode.UNRANKED_GROUP_IRONMAN

public fun Player.sameIronmanGroup(other: Player): Boolean =
    isGroupIronman && other.isGroupIronman && ironmanGroupId > 0 &&
        ironmanGroupId == other.ironmanGroupId && worldType == other.worldType

public fun Player.syncGroupObserver() {
    observerUUID = if (isGroupIronman && ironmanGroupId > 0) Long.MIN_VALUE + ironmanGroupId else uuid
}
