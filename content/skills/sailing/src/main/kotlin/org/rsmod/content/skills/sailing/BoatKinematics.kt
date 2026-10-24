package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class BoatKinematics @Inject constructor(private val boats: BoatManager) {
    fun tick() {
        for (boat in boats.all) {
            step(boat)
        }
    }

    private fun step(boat: Boat) {
        val entity = boat.entity
        val motion = BoatMotion.step(boat.speed, boat.targetSpeed, boat.type.speedCap,
            boat.type.acceleration, entity.angle, boat.targetAngle)
        boat.speed = motion.speed
        if (motion.angle != entity.angle) entity.updateAngle(motion.angle)

        if (boat.speed != 0) {
            val dx = motion.dx
            val dz = motion.dz
            if (dx != 0 || dz != 0) {
                entity.updateCoord(
                    level = entity.projectedLevel,
                    fineX = entity.fineX + dx,
                    fineZ = entity.fineZ + dz,
                    teleport = false,
                )
            }
        }
    }

}
