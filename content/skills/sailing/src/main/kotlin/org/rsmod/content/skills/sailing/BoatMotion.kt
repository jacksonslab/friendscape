package org.rsmod.content.skills.sailing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class BoatMotion(val speed: Int, val angle: Int, val dx: Int, val dz: Int) {
    companion object {
        fun step(speed: Int, targetSpeed: Int, speedCap: Int, acceleration: Int, angle: Int, targetAngle: Int): BoatMotion {
            require(speedCap >= 0 && acceleration > 0)
            val current = speed.coerceIn(0, speedCap)
            val target = targetSpeed.coerceIn(0, speedCap)
            val delta = (target.toLong() - current).coerceIn(-acceleration.toLong(), acceleration.toLong())
            val nextSpeed = (current + delta).toInt()
            val difference = ((targetAngle - angle + HALF_ANGLE) and ANGLE_MASK) - HALF_ANGLE
            val nextAngle = (angle + difference.coerceIn(-TURN_RATE, TURN_RATE)) and ANGLE_MASK
            val theta = nextAngle * (2.0 * PI / ANGLE_FULL)
            return BoatMotion(nextSpeed, nextAngle, quantize(-nextSpeed * sin(theta)), quantize(-nextSpeed * cos(theta)))
        }

        private fun quantize(value: Double): Int {
            val magnitude = ((kotlin.math.abs(value) / VELOCITY_QUANTUM) + 0.5).toInt() * VELOCITY_QUANTUM
            return if (value < 0) -magnitude else magnitude
        }

        private const val ANGLE_FULL = 2048
        private const val ANGLE_MASK = ANGLE_FULL - 1
        private const val HALF_ANGLE = ANGLE_FULL / 2
        private const val TURN_RATE = 128
        private const val VELOCITY_QUANTUM = 32
    }
}
