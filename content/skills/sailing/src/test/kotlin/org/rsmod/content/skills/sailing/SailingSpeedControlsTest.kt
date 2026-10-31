package org.rsmod.content.skills.sailing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SailingSpeedControlsTest {
    @Test
    fun `main button follows native captions for stopped forward and reverse modes`() {
        assertEquals(SailingMoveModes.FULL, SailingSpeedControls.main(SailingMoveModes.STOPPED))
        assertEquals(SailingMoveModes.FULL, SailingSpeedControls.main(SailingMoveModes.HELM_IDLE))
        assertEquals(SailingMoveModes.STOPPED, SailingSpeedControls.main(SailingMoveModes.HALF))
        assertEquals(SailingMoveModes.STOPPED, SailingSpeedControls.main(SailingMoveModes.FULL))
        assertEquals(SailingMoveModes.STOPPED, SailingSpeedControls.main(SailingMoveModes.REVERSE))
    }

    @Test
    fun `lower speed steps full to half to stopped to reverse`() {
        assertEquals(SailingMoveModes.HALF, SailingSpeedControls.lower(SailingMoveModes.FULL))
        assertEquals(SailingMoveModes.STOPPED, SailingSpeedControls.lower(SailingMoveModes.HALF))
        assertEquals(SailingMoveModes.REVERSE, SailingSpeedControls.lower(SailingMoveModes.STOPPED))
        assertEquals(SailingMoveModes.REVERSE, SailingSpeedControls.lower(SailingMoveModes.HELM_IDLE))
    }

    @Test
    fun `raise speed stops a reversing boat before moving forward`() {
        assertEquals(SailingMoveModes.STOPPED, SailingSpeedControls.raise(SailingMoveModes.REVERSE))
        assertEquals(SailingMoveModes.FULL, SailingSpeedControls.raise(SailingMoveModes.STOPPED))
        assertEquals(SailingMoveModes.FULL, SailingSpeedControls.raise(SailingMoveModes.HALF))
    }
}
