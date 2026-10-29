package org.rsmod.content.other.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AdminPanelInputTest {
    @ParameterizedTest
    @ValueSource(strings = ["3222 3218 0", "3222,3218,0", " 3222, 3218, 0 "])
    fun `accepts coordinate input from chatbox`(input: String) {
        val destination = requireNotNull(AdminPanelInput.coordinates(input))
        assertEquals(3222, destination.x)
        assertEquals(3218, destination.z)
        assertEquals(0, destination.level)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "3222 3218", "3222 3218 0 extra", "-1 3218 0",
        "3222 16384 0", "16384 3218 0", "3222 3218 -1", "3222 3218 4",
        "99999999999999999999 3218 0", "abc 3218 0", "1.5 3218 0"])
    fun `rejects malformed and out of range input without wrapping`(input: String) {
        assertNull(AdminPanelInput.coordinates(input))
    }

    @Test
    fun `preserves coordinate boundaries`() {
        val destination = requireNotNull(AdminPanelInput.coordinates("16383 0 3"))
        assertEquals(16383, destination.x)
        assertEquals(0, destination.z)
        assertEquals(3, destination.level)
    }

    @Test
    fun `user text cannot introduce client tags or log lines`() {
        assertEquals("col=ff0000Hello/colWorld", AdminPanelInput.plain("<col=ff0000>Hello</col>\r\nWorld\t"))
    }
}
