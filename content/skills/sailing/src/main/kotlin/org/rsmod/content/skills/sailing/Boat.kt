package org.rsmod.content.skills.sailing

import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

class Boat(val type: BoatType, val entity: WorldEntity, val region: Region) {
    var dock: Dock? = null
    var owner: Long? = null
    var ownerWorld: org.rsmod.game.world.WorldType? = null

    fun isOwnedBy(player: Player): Boolean = owner != null && owner == player.uuid && ownerWorld == player.worldType

    var targetAngle: Int = entity.angle
    var speed: Int = 0
    var targetSpeed: Int = 0
    var helmsman: Player? = null
    var moveMode: Int = SailingMoveModes.STOPPED

    val boardDest: CoordGrid
        get() =
            CoordGrid(
                region.southWest.x + type.boardDestDx,
                region.southWest.z + type.boardDestDz,
                type.deckLevel,
            )

    override fun toString(): String = "Boat(type=${type.key}, entity=$entity)"
}
