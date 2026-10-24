package org.rsmod.content.areas.tutorialisland.progression

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.map.CoordGrid

@Singleton
class TutorialLeave @Inject constructor(private val progression: TutorialProgression) {
    fun giveLeaveKit(access: ProtectedAccess) {
        val player = access.player
        if (!progression.isActive(player)) return

        val kit = listOf(
            tutorial_objs.BRONZE_AXE to 1,
            tutorial_objs.BRONZE_PICKAXE to 1,
            tutorial_objs.TINDERBOX to 1,
            tutorial_objs.NET to 1,
            tutorial_objs.SHRIMP to 1,
            tutorial_objs.BRONZE_DAGGER to 1,
            tutorial_objs.BRONZE_SWORD to 1,
            tutorial_objs.WOODEN_SHIELD to 1,
            tutorial_objs.SHORTBOW to 1,
            tutorial_objs.BRONZE_ARROW to 25,
            tutorial_objs.AIR_RUNE to 25,
            tutorial_objs.MIND_RUNE to 15,
            tutorial_objs.WATER_RUNE to 6,
            tutorial_objs.EARTH_RUNE to 4,
            tutorial_objs.BODY_RUNE to 2,
            tutorial_objs.BUCKET_EMPTY to 1,
            tutorial_objs.POT_EMPTY to 1,
            tutorial_objs.BREAD to 1,
        ) + if (player.isUltimateIronman) listOf(tutorial_objs.COINS to 25) else emptyList()
        if (!access.replaceTutorialKit(kit)) {
            access.mes("There is not enough room for your starter kit. Speak to the instructor again.")
            return
        }

        access.teleport(LUMBRIDGE_SPAWN)
        progression.finishAndLeave(player)
    }

    companion object {
        val LUMBRIDGE_SPAWN: CoordGrid = CoordGrid(0, 50, 50, 21, 18)
    }
}
