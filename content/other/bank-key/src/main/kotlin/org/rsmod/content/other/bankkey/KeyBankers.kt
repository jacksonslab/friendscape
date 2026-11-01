package org.rsmod.content.other.bankkey

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Singleton
class KeyBankers @Inject constructor(
    private val npcRepo: NpcRepository,
    private val clock: MapClock,
    private val collision: CollisionFlagMap,
) {
    private data class Summon(val npc: Npc, val expires: Int)
    private val active = mutableMapOf<Player, Summon>()
    private val steps = StepValidator(collision)

    fun summon(player: Player) {
        val tile = beside(player) ?: run {
            player.mes("There is no room beside you for your banker.")
            return
        }
        dismiss(player)
        val type = checkNotNull(ServerCacheManager.getNpc(BankKeyScript.BANKER.asRSCM(RSCMType.NPC)))
        val npc = Npc(type, tile)
        npcRepo.add(npc, LIFETIME)
        npc.assignSpawnOwner(player, clock.cycle)
        npc.facePlayer(player)
        active[player] = Summon(npc, clock.cycle + LIFETIME)
    }

    fun owns(player: Player, npc: Npc): Boolean = active[player]?.npc === npc && npc.isSlotAssigned

    fun dismiss(player: Player) {
        val summon = active.remove(player) ?: return
        if (summon.npc.isSlotAssigned) npcRepo.del(summon.npc, Int.MAX_VALUE)
    }

    fun maintain(player: Player) {
        val summon = active[player] ?: return
        val coords = summon.npc.coords
        if (player.loggingOut || player.ui.containsModal("interface.bankmain") ||
            clock.cycle >= summon.expires || !summon.npc.isSlotAssigned ||
            coords.level != player.coords.level || coords.chebyshevDistance(player.coords) > MAX_DISTANCE) {
            dismiss(player)
        }
    }

    private fun beside(player: Player): CoordGrid? = OFFSETS.firstNotNullOfOrNull { (x, z) ->
        val tile = player.coords.translate(x, z)
        tile.takeIf {
            collision[it.x, it.z, it.level] and BLOCKED == 0 &&
                steps.canTravel(player.coords.level, player.coords.x, player.coords.z, x, z)
        }
    }

    private companion object {
        const val LIFETIME = 100
        const val MAX_DISTANCE = 15
        const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.BLOCK_PLAYERS
        val OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
