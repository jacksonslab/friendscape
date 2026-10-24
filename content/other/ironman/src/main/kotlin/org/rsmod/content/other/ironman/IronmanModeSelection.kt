package org.rsmod.content.other.ironman

import org.rsmod.api.player.ironman.PlayerGamemode

internal object IronmanModeSelection {
    fun allowed(current: Int, desired: Int, onTutorial: Boolean, groupId: Int): Boolean {
        if (groupId != 0 || desired !in PlayerGamemode.NORMAL..PlayerGamemode.HARDCORE_IRONMAN) return false
        if (onTutorial || current == desired) return true
        return when (current) {
            PlayerGamemode.IRONMAN -> desired == PlayerGamemode.NORMAL
            PlayerGamemode.ULTIMATE_IRONMAN, PlayerGamemode.HARDCORE_IRONMAN ->
                desired == PlayerGamemode.NORMAL || desired == PlayerGamemode.IRONMAN
            else -> false
        }
    }
}
