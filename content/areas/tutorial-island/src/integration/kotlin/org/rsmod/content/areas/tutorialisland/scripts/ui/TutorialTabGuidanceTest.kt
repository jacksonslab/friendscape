package org.rsmod.content.areas.tutorialisland.scripts.ui

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow.NpcHintArrow
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.events.SideTabClicked
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varbits
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class TutorialTabGuidanceTest {
    @Test
    fun GameTestState.`opening Prayer restores Brother Brace's arrow and return prompt`() =
        checkTabGuide("PrayerTab", 5, tutorial_npcs.PRAYER_TUTOR, "Brother Brace")

    @Test
    fun GameTestState.`opening Magic restores the Magic Instructor's arrow and return prompt`() =
        checkTabGuide("MagicTab", 6, tutorial_npcs.MAGIC_TUTOR, "Magic Instructor")

    @Test
    fun GameTestState.`opening Account Management restores the Account Guide's arrow and return prompt`() =
        checkTabGuide("AccountMgmt", 8, tutorial_npcs.BANK_TUTOR, "Account Guide")

    private fun GameTestState.checkTabGuide(
        stepName: String,
        tab: Int,
        npcName: String,
        instructor: String,
    ) = runInjectedGameTest(TabDependencies::class, null, TutorialSidePanelScript::class) { deps ->
        val tile = CoordGrid(1680, 12450, 0)
        allocZoneCollision(tile)
        player.teleport(tile)
        val tutor = spawnNpc(tile.translateX(1), checkNotNull(ServerCacheManager.getNpc(npcName.asRSCM())))
        val step = TutorialStep.valueOf(stepName)
        VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
        deps.progression.setStep(player, step, tutor)
        assertEquals(tab + 1, player.testFlashingSide)
        assertTrue(client.outgoingMessages.contains(RunClientScript(915, listOf<Any>(3))))
        val expectedArrow = HintArrow(NpcHintArrow(tutor.slotId))
        assertEquals(expectedArrow, client.outgoingMessages.filterIsInstance<HintArrow>().last())
        client.clearOutgoing()

        deps.events.publish(SideTabClicked(player, tab))
        assertEquals(0, player.testFlashingSide)

        assertEquals(expectedArrow, client.outgoingMessages.filterIsInstance<HintArrow>().last())
        val component = ServerCacheManager.fromComponent(tutorial_components.HINT_TEXT_2)
        val guide = client.outgoingMessages.filterIsInstance<IfSetText>().last {
            it.interfaceId == component.interfaceId && it.componentId == component.component
        }.text
        assertTrue(guide.contains("Speak to"), guide)
        assertTrue(guide.contains(instructor), guide)
        assertEquals(step, deps.progression.current(player))

        client.clearOutgoing()
        deps.progression.resume(player)
        assertEquals(expectedArrow, client.outgoingMessages.filterIsInstance<HintArrow>().last())
    }

    @Test
    fun GameTestState.`finishing the Prayer lesson moves the arrow to the exit`() =
        runInjectedGameTest(TabDependencies::class, null, TutorialSidePanelScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile)
            val tutor = spawnNpc(tile.translateX(1), checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.PRAYER_TUTOR.asRSCM())))
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.PrayerTab, tutor)
            deps.events.publish(SideTabClicked(player, 5))
            client.clearOutgoing()

            deps.progression.setStep(player, TutorialStep.PrayerExit, tutor)

            assertTrue(client.outgoingMessages.filterIsInstance<HintArrow>().last().type is HintArrow.TileHintArrow)
        }

    @Test
    fun GameTestState.`opening Prayer at another tutorial stage does not redirect its arrow`() =
        runInjectedGameTest(TabDependencies::class, null, TutorialSidePanelScript::class) { deps ->
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.CombatRange)
            client.clearOutgoing()

            deps.events.publish(SideTabClicked(player, 5))

            assertTrue(client.outgoingMessages.filterIsInstance<HintArrow>().isEmpty())
            assertEquals(TutorialStep.CombatRange, deps.progression.current(player))
        }
}

private class TabDependencies @Inject constructor(val progression: TutorialProgression, val events: EventBus)

private var Player.testFlashingSide by intVarBit(tutorial_varbits.FLASHSIDE)
