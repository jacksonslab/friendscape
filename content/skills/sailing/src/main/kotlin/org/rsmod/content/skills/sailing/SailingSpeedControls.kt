package org.rsmod.content.skills.sailing

object SailingSpeedControls {
    fun main(mode: Int): Int = when (mode) {
        SailingMoveModes.HALF, SailingMoveModes.FULL, SailingMoveModes.REVERSE -> SailingMoveModes.STOPPED
        else -> SailingMoveModes.FULL
    }

    fun lower(mode: Int): Int = when (mode) {
        SailingMoveModes.FULL -> SailingMoveModes.HALF
        SailingMoveModes.HALF -> SailingMoveModes.STOPPED
        else -> SailingMoveModes.REVERSE
    }

    fun raise(mode: Int): Int = when (mode) {
        SailingMoveModes.REVERSE -> SailingMoveModes.STOPPED
        else -> SailingMoveModes.FULL
    }

    fun set(boat: Boat, mode: Int) {
        boat.moveMode = mode
        boat.targetSpeed = when (mode) {
            SailingMoveModes.FULL -> boat.type.baseSpeed
            SailingMoveModes.HALF -> boat.type.baseSpeed / 2
            SailingMoveModes.REVERSE -> -boat.type.baseSpeed / 3
            else -> 0
        }
        if (boat.targetSpeed != 0) boat.port = null
    }
}
