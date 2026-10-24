package org.rsmod.content.areas.tutorialisland

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.game.entity.Player

class TutorialTeleportHook @Inject constructor(private val progression: TutorialProgression) :
    PlayerTeleportValidateHook {
    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? =
        if (progression.isActive(player)) "Finish Tutorial Island before using teleports." else null
}
