package org.rsmod.content.areas.tutorialisland.scripts.ui

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow.NpcHintArrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.events.EventBus
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class TutorialEquipmentGuidanceTest {
    @Test
    fun GameTestState.`ranged guidance waits for gear then moves the arrow from tutor to rat on equip`() =
        runInjectedGameTest(EquipmentDependencies::class, null, TutorialSidePanelScript::class) { deps ->
            val tile = CoordGrid(1680, 12450, 0)
            allocZoneCollision(tile)
            player.teleport(tile)
            player.clearInv()
            val tutor = spawnNpc(tile.translateX(1), checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.COMBAT_TUTOR.asRSCM())))
            val rat = spawnNpc(tile.translateX(3), checkNotNull(ServerCacheManager.getNpc(tutorial_npcs.GIANT_RAT.asRSCM())))
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)

            fun guide(): String {
                val component = ServerCacheManager.fromComponent(tutorial_components.HINT_TEXT_2)
                return client.outgoingMessages.filterIsInstance<IfSetText>().last {
                    it.interfaceId == component.interfaceId && it.componentId == component.component
                }.text
            }

            deps.progression.setStep(player, TutorialStep.CombatRange, tutor)
            assertTrue(guide().contains("Speak to the combat instructor"), guide())
            assertEquals(HintArrow(NpcHintArrow(tutor.slotId)), client.outgoingMessages.filterIsInstance<HintArrow>().last())

            player.inv[0] = InvObj("obj.shortbow")
            player.inv[1] = InvObj("obj.bronze_arrow", 50)
            deps.progression.setStep(player, TutorialStep.CombatRange, tutor)
            assertTrue(guide().contains("Equip the shortbow"), guide())

            player.inv[0] = null
            player.worn[Wearpos.RightHand.slot] = InvObj("obj.shortbow")
            deps.events.publish(HeldEquipEvents.WearposChange(player, Wearpos.RightHand,
                checkNotNull(ServerCacheManager.getItem("obj.shortbow".asRSCM()))))
            assertEquals(HintArrow(NpcHintArrow(rat.slotId)), client.outgoingMessages.filterIsInstance<HintArrow>().last())
            assertTrue(guide().contains("Equip the bronze arrows"), guide())

            player.inv[1] = null
            player.worn[Wearpos.Quiver.slot] = InvObj("obj.bronze_arrow", 50)
            deps.events.publish(HeldEquipEvents.WearposChange(player, Wearpos.Quiver,
                checkNotNull(ServerCacheManager.getItem("obj.bronze_arrow".asRSCM()))))
            assertTrue(guide().contains("Attack a giant rat"), guide())
            assertEquals(HintArrow(NpcHintArrow(rat.slotId)), client.outgoingMessages.filterIsInstance<HintArrow>().last())
            assertEquals(TutorialStep.CombatRange, deps.progression.current(player))

            client.clearOutgoing()
            deps.progression.resume(player)
            assertEquals(HintArrow(NpcHintArrow(rat.slotId)), client.outgoingMessages.filterIsInstance<HintArrow>().last())
            assertTrue(guide().contains("Attack a giant rat"), guide())
        }

    @Test
    fun GameTestState.`equipping arrows before the bow also refreshes ranged guidance`() =
        runInjectedGameTest(EquipmentDependencies::class, null, TutorialSidePanelScript::class) { deps ->
            player.clearInv()
            player.inv[0] = InvObj("obj.shortbow")
            player.worn[Wearpos.Quiver.slot] = InvObj("obj.bronze_arrow", 50)
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.CombatRange)
            player.inv[0] = null
            player.worn[Wearpos.RightHand.slot] = InvObj("obj.shortbow")
            client.clearOutgoing()
            deps.events.publish(HeldEquipEvents.WearposChange(player, Wearpos.RightHand,
                checkNotNull(ServerCacheManager.getItem("obj.shortbow".asRSCM()))))
            val component = ServerCacheManager.fromComponent(tutorial_components.HINT_TEXT_2)
            val guide = client.outgoingMessages.filterIsInstance<IfSetText>().last {
                it.interfaceId == component.interfaceId && it.componentId == component.component
            }.text
            assertTrue(guide.contains("Attack a giant rat"), guide)
            assertEquals(TutorialStep.CombatRange, deps.progression.current(player))
        }

    @Test
    fun GameTestState.`equipping the dagger immediately directs the player back to Vannaka`() =
        runInjectedGameTest(EquipmentDependencies::class, null, TutorialSidePanelScript::class) { deps ->
            player.clearInv()
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.CombatDagger)
            player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_dagger")
            client.clearOutgoing()

            deps.events.publish(HeldEquipEvents.WearposChange(player, Wearpos.RightHand,
                checkNotNull(ServerCacheManager.getItem("obj.bronze_dagger".asRSCM()))))

            val component = ServerCacheManager.fromComponent(tutorial_components.HINT_TEXT_2)
            val guide = client.outgoingMessages.filterIsInstance<IfSetText>().last {
                it.interfaceId == component.interfaceId && it.componentId == component.component
            }.text
            assertTrue(guide.contains("dagger is equipped"), guide)
            assertTrue(guide.contains("Speak to the combat instructor"), guide)
            assertEquals(TutorialStep.CombatDagger, deps.progression.current(player))
            assertEquals(1, player.worn.count("obj.bronze_dagger"))
            assertEquals(0, player.inv.count("obj.bronze_sword"))
        }

    @Test
    fun GameTestState.`a dagger in the backpack still needs to be equipped`() =
        runInjectedGameTest(EquipmentDependencies::class, null, TutorialSidePanelScript::class) { deps ->
            player.clearInv()
            player.inv[0] = InvObj("obj.bronze_dagger")
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.CombatDagger)
            client.clearOutgoing()

            deps.progression.resume(player)

            val component = ServerCacheManager.fromComponent(tutorial_components.HINT_TEXT_2)
            val guide = client.outgoingMessages.filterIsInstance<IfSetText>().last {
                it.interfaceId == component.interfaceId && it.componentId == component.component
            }.text
            assertEquals(TutorialStep.CombatDagger.guideBody, guide)
            assertEquals(1, player.inv.count("obj.bronze_dagger"))
        }

    @Test
    fun GameTestState.`relogging with an equipped dagger preserves the return instruction`() =
        runInjectedGameTest(EquipmentDependencies::class, null, TutorialSidePanelScript::class) { deps ->
            player.clearInv()
            player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_dagger")
            VarPlayerIntMapSetter.set(player, "varp.friendscape_tutorial_started", 1)
            deps.progression.setStep(player, TutorialStep.CombatDagger)
            client.clearOutgoing()

            deps.progression.resume(player)

            val component = ServerCacheManager.fromComponent(tutorial_components.HINT_TEXT_2)
            val guide = client.outgoingMessages.filterIsInstance<IfSetText>().last {
                it.interfaceId == component.interfaceId && it.componentId == component.component
            }.text
            assertTrue(guide.contains("Speak to the combat instructor"), guide)
        }
}

private class EquipmentDependencies @Inject constructor(val progression: TutorialProgression, val events: EventBus)
