package org.rsmod.content.areas.tutorialisland.scripts.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.hit.HitType

class TutorialCombatTest {
    @Test
    fun `only the required attack style advances each rat combat stage`() {
        for (step in TutorialStep.entries) for (hit in HitType.entries) {
            val expected = when {
                step == TutorialStep.CombatMelee && hit == HitType.Melee -> TutorialStep.CombatRange
                step == TutorialStep.CombatRange && hit == HitType.Ranged -> TutorialStep.CombatLadder
                else -> null
            }
            assertEquals(expected, ratKillStep(step, hit))
        }
    }
}
