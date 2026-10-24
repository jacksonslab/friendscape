package org.rsmod.content.raids.tob.rooms

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.raids.tob.TobArea
import org.rsmod.content.raids.tob.TobContext
import org.rsmod.content.raids.tob.TobRaid
import org.rsmod.content.raids.tob.TobRoom
import org.rsmod.content.raids.tob.rewards.TobChestContents
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Verzik's vault. One Monumental chest per surviving raider; the chest varbits make each raider
 * see an arrow over their own chest and a purple glow on the one holding a unique.
 */
internal class TreasureRoom(raid: TobRaid, ctx: TobContext) : TobRoom(raid, ctx, 6) {
    override val displayName = "Treasure vault"
    override val entry = CoordGrid(3237, 4309, 0)
    override val arena = TobArea(3225, 4306, 3249, 4333)
    override val startsOnEntry = false
    override val mvpPoints = 0

    private val owners = arrayOfNulls<Long>(CHEST_TILES.size)
    private val rare = BooleanArray(CHEST_TILES.size)

    fun setChests(chests: List<TobChestContents>) {
        for ((slot, chest) in chests.withIndex()) {
            if (slot >= CHEST_TILES.size) break
            owners[slot] = chest.player.uuid
            rare[slot] = chest.rare
            addLoc(CHEST_TILES[slot], CHEST_LOCS[slot])
        }
        raid.players().forEach(::syncChests)
    }

    fun syncChests(player: Player) {
        for (slot in CHEST_TILES.indices) {
            val mine = owners[slot] != null && owners[slot] == player.uuid
            val value = (if (mine) 2 else 0) + (if (rare[slot]) 1 else 0)
            VarPlayerIntMapSetter.set(player, CHEST_VARBITS[slot], value)
        }
    }

    fun chestSlot(static: CoordGrid): Int = CHEST_TILES.indexOfFirst { it.x == static.x && it.z == static.z }

    fun ownerOf(slot: Int): Long? = owners.getOrNull(slot)

    override fun onStart() = Unit

    override fun onTick() = Unit

    companion object {
        val CHEST_TILES = listOf(3227, 3230, 3233, 3236, 3239).map { CoordGrid(it, 4315) }
        val CHEST_LOCS = (0 until 5).map { "loc.tob_treasureroom_chest_loc$it" }
        private val CHEST_VARBITS = (0 until 5).map { "varbit.tob_treasureroom_chest_$it" }
    }
}
