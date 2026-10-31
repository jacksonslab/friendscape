package org.rsmod.content.skills.prayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PrayerIncenseDurationTest {
    @Test
    fun `burn duration follows the Firemaking formula at its inclusive and exclusive bounds`() {
        assertEquals(250, PrayerIncenseScript.burnTicks(50, 0))
        assertEquals(299, PrayerIncenseScript.burnTicks(50, 49))
        assertEquals(299, PrayerIncenseScript.burnTicks(99, 0))
        assertEquals(397, PrayerIncenseScript.burnTicks(99, 98))
    }

    @Test
    fun `invalid random rolls cannot extend a burner beyond its allowed lifetime`() {
        assertThrows(IllegalArgumentException::class.java) { PrayerIncenseScript.burnTicks(50, 50) }
        assertThrows(IllegalArgumentException::class.java) { PrayerIncenseScript.burnTicks(0, 0) }
    }
}
