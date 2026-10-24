package org.rsmod.api.game.process.player

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class BuildAreaZoneStateTest {
    @Test
    fun `forced rebuild invalidates both the deck and root view even if neither moved`() {
        val player = Player()
        player.lastProcessedZone = ZoneKey.from(CoordGrid(6400, 6400))
        player.lastProcessedViewZone = ZoneKey.from(CoordGrid(3053, 3193))
        player.visibleZoneKeys.add(player.lastProcessedViewZone.packed)
        player.regionRebuildPending = true
        player.consumeZoneRebuild()
        assertFalse(player.regionRebuildPending)
        assertTrue(player.visibleZoneKeys.isEmpty())
        assertEquals(ZoneKey.NULL, player.lastProcessedZone)
        assertEquals(ZoneKey.NULL, player.lastProcessedViewZone)
        val freshView = ZoneKey.from(CoordGrid(3053, 3193))
        player.lastProcessedViewZone = freshView
        player.visibleZoneKeys.add(freshView.packed)
        player.consumeZoneRebuild()
        assertEquals(freshView, player.lastProcessedViewZone)
        assertEquals(1, player.visibleZoneKeys.size)
    }
}
