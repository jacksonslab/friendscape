package org.rsmod.content.skills.sailing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChartingCompletionTest {
    @Test
    fun `completion flags preserve the sign bit and separate adjacent words`() {
        assertEquals("varp.sailing_charting_completion_0", SeaCharting.word(31))
        assertEquals("varp.sailing_charting_completion_1", SeaCharting.word(32))
        assertEquals(Int.MIN_VALUE, SeaCharting.mask(31))
        assertEquals(1, SeaCharting.mask(32))
        val word = SeaCharting.mask(0) or SeaCharting.mask(31)
        assertTrue(SeaCharting.isSet(word, 0))
        assertTrue(SeaCharting.isSet(word, 31))
        assertFalse(SeaCharting.isSet(word, 30))
        assertEquals("varp.sailing_charting_completion_11", SeaCharting.word(383))
    }

    @Test
    fun `invalid discovery IDs cannot wrap around into existing completion flags`() {
        for (id in listOf(-1, 384, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { SeaCharting.word(id) }
            assertThrows(IllegalArgumentException::class.java) { SeaCharting.mask(id) }
        }
    }
}
