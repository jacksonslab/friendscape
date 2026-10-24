package org.rsmod.content.skills.sailing

import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.region.Region
import org.rsmod.game.world.WorldType
import org.rsmod.map.CoordGrid

class Boat(val type: BoatType, val entity: WorldEntity, val region: Region) {
    var port: Port? = null
    var owner: Long? = null
    var ownerWorld: WorldType? = null
    var ownerName: String = ""

    var targetAngle: Int = entity.angle
    var speed: Int = 0
    var targetSpeed: Int = 0
    var helmsman: Player? = null
    var moveMode: Int = SailingMoveModes.STOPPED
    var hookDeployed: Boolean = false

    val isStopped: Boolean
        get() = speed == 0 && targetSpeed == 0

    fun isOwnedBy(player: Player): Boolean =
        owner != null && owner == player.uuid && ownerWorld == player.worldType

    fun deckCoord(dx: Int, dz: Int, level: Int = type.deckLevel): CoordGrid =
        CoordGrid(region.southWest.x + dx, region.southWest.z + dz, level)

    val boardDest: CoordGrid
        get() = deckCoord(type.boardDestDx, type.boardDestDz)

    override fun toString(): String = "Boat(type=${type.key}, owner=$ownerName, entity=$entity)"
}
