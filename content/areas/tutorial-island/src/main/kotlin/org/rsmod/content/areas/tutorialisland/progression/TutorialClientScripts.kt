package org.rsmod.content.areas.tutorialisland.progression

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.runClientScript
import org.rsmod.game.entity.Player

object TutorialClientScripts {
    private fun run(player: Player, name: String, vararg args: Any) {
        player.runClientScript("clientscript.[clientscript,$name]".asRSCM(), *args)
    }

    fun tutorialDefaultSettings(player: Player) = run(player, "tutorial_default_settings")
    fun tutorialEnd(player: Player) = run(player, "tutorial_end")
    fun mesOverlay(player: Player, text: String) = run(player, "mesoverlay", text)
    fun tutorialProgressbarSet(player: Player, progress: Int, max: Int) =
        run(player, "tutorial_progressbar_set", progress * 100 / max)
    fun magicFlashSpell(player: Player, component: String) =
        run(player, "magic_flash_spell", component.asRSCM())
    fun entityHighlightClear(player: Player) = player.runClientScript("clientscript.[proc,entity_highlight_clear]".asRSCM())
    fun equipmentIconFlash(player: Player) = run(player, "equipment_icon_flash")
    fun highlightingOff(player: Player) = ClientScripts.highlightingOff(player)
    fun highlightingOn(player: Player) = ClientScripts.highlightingOn(player)
    fun highlightLocTypeOn(player: Player, id: Int) = run(player, "highlight_loctype_on", id)
    fun highlightNpcTypeOn(player: Player, id: Int) = run(player, "highlight_npctype_on", id)
    fun toplevelSidebuttonSwitch(player: Player, side: Int) =
        ClientScripts.toplevelSidebuttonSwitch(player, side)
}
