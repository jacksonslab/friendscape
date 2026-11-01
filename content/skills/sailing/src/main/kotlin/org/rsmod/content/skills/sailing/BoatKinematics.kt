package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes

@Singleton
class BoatKinematics @Inject constructor(private val boats: BoatManager) {
    fun tick() {
        boats.releaseStranded()
        for (boat in boats.all) {
            step(boat)
        }
    }

    private fun step(boat: Boat) {
        val entity = boat.entity
        val type = boat.type
        val motion =
            BoatMotion.step(boat.speed, boat.targetSpeed, type.speedCap, type.acceleration, entity.angle, boat.targetAngle)
        val sailable = boats.navigableAt(entity.projectedLevel)

        if (motion.angle != entity.angle) {
            if (Navigation.fits(sailable, entity.fineX, entity.fineZ, motion.angle, type.hullWidth, type.hullLength)) {
                entity.updateAngle(motion.angle)
            } else {
                boat.targetAngle = entity.angle
            }
        }

        boat.speed = motion.speed
        if (boat.speed == 0 || (motion.dx == 0 && motion.dz == 0)) {
            return
        }
        val nextX = entity.fineX + motion.dx
        val nextZ = entity.fineZ + motion.dz
        if (!Navigation.passageClear(sailable, entity.fineX, entity.fineZ, motion.dx, motion.dz, entity.angle, type.hullWidth, type.hullLength)) {
            runAground(boat)
            return
        }
        boat.port = null
        entity.updateCoord(level = entity.projectedLevel, fineX = nextX, fineZ = nextZ, teleport = false)
    }

    private fun runAground(boat: Boat) {
        boat.speed = 0
        boat.targetSpeed = 0
        if (boat.moveMode != SailingMoveModes.STOPPED) {
            boat.moveMode = SailingMoveModes.HELM_IDLE
        }
        boat.helmsman?.mes("Your boat can't sail any further that way. Un-set the sails once it stops to reverse.")
    }
}
