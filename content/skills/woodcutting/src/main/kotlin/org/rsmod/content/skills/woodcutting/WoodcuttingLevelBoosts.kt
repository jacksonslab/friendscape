package org.rsmod.content.skills.woodcutting

import dev.openrune.ServerCacheManager
import dev.openrune.types.enums.enum
import jakarta.inject.Inject
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.content.skills.woodcutting.configs.WoodcuttingParams
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionLoc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

internal var Player.woodcuttingLastSwing by intVarp("varp.woodcutting_last_swing")
internal var Player.woodcuttingTreeCoord by intVarp("varp.woodcutting_tree_coord")
internal var Player.woodcuttingTreeId by intVarp("varp.woodcutting_tree_id")

class WoodcuttingLevelBoosts @Inject constructor(
    private val players: PlayerRepository,
    private val clock: MapClock,
) : InvisibleLevelMod("stat.woodcutting") {
    override fun Player.calculateBoost(): Int {
        if (inGuild(coords)) return 7
        val target = (interaction as? InteractionLoc)?.target ?: return 0
        val type = ServerCacheManager.getObject(target.id) ?: return 0
        if (type.param(WoodcuttingParams.social) == 0) return 0
        val count = players.findAll(ZoneKey.from(target.coords), zoneRadius = 1).count {
            it.isChopping(target, clock.cycle)
        }
        return socialBonus(count)
    }

    companion object {
        internal fun socialBonus(choppers: Int): Int = if (choppers < 2) 0 else choppers.coerceAtMost(10)

        internal fun inGuild(coords: CoordGrid): Boolean {
            val bounds = enum<Int, Int>("woodcutting_guild_bounds")
            val z = if (coords.z >= 6400) coords.z - 6400 else coords.z
            return coords.x in bounds.getValue(0)..bounds.getValue(2) &&
                z in bounds.getValue(1)..bounds.getValue(3)
        }

        private fun Player.isChopping(tree: BoundLocInfo, cycle: Int): Boolean {
            val target = (interaction as? InteractionLoc)?.target ?: return false
            return target == tree && woodcuttingTreeId == tree.id &&
                woodcuttingTreeCoord == tree.coords.packed &&
                woodcuttingLastSwing in (cycle - 1)..cycle && !hasMovedThisCycle && !inv.isFull()
        }
    }
}
