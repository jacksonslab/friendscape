package org.rsmod.content.quest.manager

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathDropLocationHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

private var Player.questArenaReturn by intVarp("varp.quest_arena_return")

class QuestArena @Inject constructor(
    private val regions: RegionRepository,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
) : PluginScript(), PlayerDeathDropLocationHook {
    private data class Room(val region: Region, val boss: Npc, val exit: CoordGrid)
    private val rooms = hashMapOf<PlayerUid, Room>()

    override fun ScriptContext.startup() {
        onPlayerLogout { leave(player, returnToExit = true) }
        onPlayerLogin {
            if (player.questArenaReturn != 0) {
                PathingEntityCommon.telejump(player, collision, CoordGrid(player.questArenaReturn))
                player.questArenaReturn = 0
            }
        }
        onPlayerCoordsChanged {
            val room = rooms[player.uid] ?: return@onPlayerCoordsChanged
            if (player.coords.x !in room.region.southWest.x until room.region.southWest.x + 128 ||
                player.coords.z !in room.region.southWest.z until room.region.southWest.z + 128
            ) leave(player, returnToExit = false)
        }
    }

    fun enter(access: ProtectedAccess, type: String, spawn: CoordGrid, entry: CoordGrid, exit: CoordGrid): Boolean {
        leave(access.player, returnToExit = false)
        val template = RegionTemplate.create {
            for (level in 0..3) {
                copy((entry.x shr 6) * 8, (entry.z shr 6) * 8, level) {
                    regionZoneX = 4
                    regionZoneZ = 4
                    zoneWidth = 8
                    zoneLength = 8
                }
            }
        }
        val region = regions.add(template) ?: run {
            access.mes("The area is busy. Please try again shortly.")
            return false
        }
        regions.protect(region)
        val npc = Npc(type, region.normal[spawn])
        npcs.add(npc, 6000)
        rooms[access.player.uid] = Room(region, npc, exit)
        access.player.questArenaReturn = exit.packed
        access.telejump(region.normal[entry])
        npc.opPlayer2(access.player, interactions)
        return true
    }

    fun owner(npc: Npc): PlayerUid? = rooms.entries.firstOrNull { it.value.boss === npc }?.key

    override fun dropLocation(player: Player): CoordGrid? = rooms[player.uid]?.exit

    fun boss(player: Player): Npc? = rooms[player.uid]?.boss?.takeIf { it.isSlotAssigned }

    fun defeated(player: Player) {
        val boss = boss(player) ?: return
        npcs.del(boss, Int.MAX_VALUE)
    }

    fun normal(player: Player, coords: CoordGrid): CoordGrid = rooms[player.uid]?.region?.normal?.get(coords) ?: coords

    fun leave(player: Player, returnToExit: Boolean = true) {
        val room = rooms.remove(player.uid) ?: return
        if (returnToExit) PathingEntityCommon.telejump(player, collision, room.exit)
        player.questArenaReturn = 0
        if (room.boss.isSlotAssigned) npcs.del(room.boss, Int.MAX_VALUE)
        regions.unprotect(room.region)
    }
}
