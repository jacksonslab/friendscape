package org.rsmod.content.skills.sailing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class BoatMotionTest {
    @Test
    fun `acceleration and braking reach their targets without overshoot`() {
        var speed = 0
        repeat(20) {
            val next = BoatMotion.step(speed, 333, 448, 64, 0, 0)
            assertTrue(next.speed in speed..minOf(speed + 64, 333))
            speed = next.speed
        }
        assertEquals(333, speed)
        repeat(20) { speed = BoatMotion.step(speed, 0, 448, 64, 0, 0).speed }
        assertEquals(0, speed)
    }

    @Test
    fun `heading wraps and turns along the shortest arc at the protocol turn rate`() {
        assertEquals(8, BoatMotion.step(0, 0, 448, 64, 2040, 8).angle)
        assertEquals(2040, BoatMotion.step(0, 0, 448, 64, 8, 2040).angle)
        assertEquals(128, BoatMotion.step(0, 0, 448, 64, 0, 512).angle)
        assertEquals(1920, BoatMotion.step(0, 0, 448, 64, 0, 1536).angle)
        for (angle in 0..2047) for (target in 0..2047 step 127) {
            val next = BoatMotion.step(0, 0, 448, 64, angle, target)
            val diff = ((next.angle - angle + 1024) and 2047) - 1024
            assertTrue(abs(diff) <= 128)
            assertTrue(next.angle in 0..2047)
        }
    }

    @Test
    fun `all headings have symmetric quantized displacement and bounded error`() {
        for (angle in 0..2047) {
            val next = BoatMotion.step(448, 448, 448, 64, angle, angle)
            val opposite = BoatMotion.step(448, 448, 448, 64, angle + 1024, angle + 1024)
            assertEquals(0, next.dx % 32)
            assertEquals(0, next.dz % 32)
            assertEquals(-next.dx, opposite.dx)
            assertEquals(-next.dz, opposite.dz)
            assertTrue(abs(kotlin.math.hypot(next.dx.toDouble(), next.dz.toDouble()) - 448) <= 23)
        }
        assertEquals(BoatMotion(192, 0, 0, -192), BoatMotion.step(192, 192, 448, 64, 0, 0))
        assertEquals(BoatMotion(192, 512, -192, 0), BoatMotion.step(192, 192, 448, 64, 512, 512))
    }

    @Test
    fun `invalid speeds clamp and large acceleration cannot overflow`() {
        assertEquals(448, BoatMotion.step(Int.MAX_VALUE, Int.MAX_VALUE, 448, Int.MAX_VALUE, 0, 0).speed)
        assertEquals(0, BoatMotion.step(-100, -100, 448, 64, 0, 0).speed)
        assertThrows(IllegalArgumentException::class.java) { BoatMotion.step(0, 0, -1, 64, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { BoatMotion.step(0, 0, 448, 0, 0, 0) }
    }
}
