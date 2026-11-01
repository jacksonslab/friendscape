package org.rsmod.content.bosses.gemstonecrab

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.random.Random
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.damage.DamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

public class GemstoneCrabManager(
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
    private val playerList: PlayerList,
    private val hpBar: BossHpBarScript,
    public val site: CrabSite,
) {
    private val spots = site.spots

    public var liveNpc: Npc? = null
        private set

    public var remainsNpc: Npc? = null
        private set

    public var eligibleMiners: Set<Long> = emptySet()
        private set

    private var currentLocationIndex = -1
    private var nextLocationIndex = -1
    private var burrowCycle = -1
    private var burrowStartCycle = -1
    private var activeLifetime = 0
    private var remainsExpireCycle = -1
    private var remainsDisintegrating = false
    private var remainsSpotCycle = -1
    private var topDealers: List<DamageContributor.ByPlayer> = emptyList()
    private var damagers: List<Player> = emptyList()

    private val barOpenPlayers: MutableSet<Player> = Collections.newSetFromMap(IdentityHashMap())
    private val claimedMiners: MutableSet<Long> = mutableSetOf()
    private val playersInSpotArea: Array<MutableSet<Player>> =
        Array(spots.size) { Collections.newSetFromMap(IdentityHashMap()) }

    public val isCrabActive: Boolean
        get() = liveNpc != null && burrowStartCycle == -1

    public fun isEligibleMiner(uuid: Long): Boolean = uuid in eligibleMiners

    public fun hasClaimedMining(uuid: Long): Boolean = uuid in claimedMiners

    public fun claimMining(uuid: Long): Boolean = claimedMiners.add(uuid)

    public fun start() {
        if (liveNpc != null || remainsNpc != null) {
            return
        }
        spawnNext()
    }

    public fun tick() {
        val live = liveNpc
        if (live != null) {
            if (burrowStartCycle != -1) {
                tickBurrow(live)
                return
            }
            for (player in barOpenPlayers) {
                updateBar(player, live)
            }
            if (mapClock.cycle >= burrowCycle) {
                startBurrow(live)
            }
            return
        }

        val remains = remainsNpc ?: return
        if (mapClock.cycle >= remainsExpireCycle) {
            expireRemains(remains)
            return
        }
        if (remainsDisintegrating) {
            return
        }
        if (mapClock.cycle >= remainsExpireCycle - DISINTEGRATE_CYCLES) {
            disintegrateRemains(remains)
            return
        }
        if (site.crabEffects && mapClock.cycle >= remainsSpotCycle) {
            remains.spotanim("spotanim.crab_boss_remains_idle_spot")
            remainsSpotCycle = mapClock.cycle + REMAINS_SPOT_INTERVAL_CYCLES
        }
    }

    public fun caveExitFor(cave: CoordGrid): CoordGrid? {
        val index = if (isCrabActive) currentLocationIndex else nextLocationIndex
        val destination = spots.getOrNull(index) ?: return null
        return destination.caveExit?.takeIf { destination.cave != cave }
    }

    public fun forceBurrow() {
        val live = liveNpc ?: return
        if (burrowStartCycle == -1) {
            startBurrow(live)
        }
    }

    public fun openBarFor(player: Player, npc: Npc) {
        if (barOpenPlayers.add(player)) {
            hpBar.onOpen(player, npc)
            updateBar(player, npc)
        }
    }

    public fun releaseBar(player: Player) {
        barOpenPlayers.remove(player)
    }

    public fun enterSpotArea(player: Player, spotIndex: Int) {
        playersInSpotArea[spotIndex].add(player)
        val live = liveNpc ?: return
        if (currentLocationIndex == spotIndex) {
            openBarFor(player, live)
        }
    }

    public fun exitSpotArea(player: Player, spotIndex: Int) {
        playersInSpotArea[spotIndex].remove(player)
        val live = liveNpc ?: return
        if (barOpenPlayers.remove(player)) {
            hpBar.onClose(player, live)
        }
    }

    private fun spawnNext() {
        currentLocationIndex = if (nextLocationIndex != -1) nextLocationIndex else pickNextLocation()
        nextLocationIndex = -1
        val npc = Npc(resolveNpc(site.crab), spots[currentLocationIndex].crab)
        val lifetime = ACTIVE_DURATION_CYCLES.random()
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.anim(site.spawnAnimation)
        if (site.crabEffects) {
            npc.spotanim("spotanim.vfx_crab_boss_spawn")
        }
        liveNpc = npc
        activeLifetime = lifetime
        burrowCycle = mapClock.cycle + lifetime

        for (player in playersInSpotArea[currentLocationIndex]) {
            openBarFor(player, npc)
        }
    }

    private fun pickNextLocation(): Int {
        if (spots.size == 1) {
            return 0
        }
        var next: Int
        do {
            next = Random.nextInt(spots.size)
        } while (next == currentLocationIndex)
        return next
    }

    private fun startBurrow(live: Npc) {
        burrowStartCycle = mapClock.cycle
        nextLocationIndex = pickNextLocation()
        val dealers =
            live.damageContributions
                .sortedByDamageDescending()
                .filterIsInstance<DamageContributor.ByPlayer>()
        damagers = dealers.mapNotNull { it.resolve(playerList) }
        topDealers = dealers.take(TOP_DAMAGE_DEALERS)

        burrowCycle = mapClock.cycle
        for (player in barOpenPlayers) {
            updateBar(player, live)
        }
        if (site.preserveDeathFacing) {
            live.facingTarget(playerList)?.let { live.lockFacing(it.coords, it.size, it.size) }
        }
        live.noneMode()
        live.hideAllOps()
        live.anim(site.deathAnimation)
        if (site.crabEffects) {
            live.spotanim("spotanim.vfx_crab_boss_death")
        }
    }

    private fun tickBurrow(live: Npc) {
        when (mapClock.cycle - burrowStartCycle) {
            BURROW_ANNOUNCE_DELAY_CYCLES -> announceBurrow()
            BURROW_REMOVE_DELAY_CYCLES -> finishBurrow(live)
        }
    }

    private fun announceBurrow() {
        broadcast("The ${crabName()} burrows away, leaving a piece of its shell behind.")

        val names = topDealers.take(3).mapNotNull { it.resolve(playerList)?.displayName }
        if (names.isNotEmpty()) {
            broadcast(topDealerMessage(names))
        }

        for (dealer in topDealers) {
            val player = dealer.resolve(playerList) ?: continue
            player.mes(
                "<col=005f00>You gained enough understanding of the crab to mine from its remains.",
            )
        }
    }

    private fun finishBurrow(live: Npc) {
        eligibleMiners = topDealers.map { it.uuid }.toSet()
        claimedMiners.clear()
        topDealers = emptyList()
        damagers = emptyList()
        burrowStartCycle = -1

        val coords = live.coords
        val facingSquare = live.faceLockSquare
        val facingWidth = live.faceLockWidth
        val facingLength = live.faceLockLength
        npcRepo.del(live, Int.MAX_VALUE)
        liveNpc = null

        for (player in barOpenPlayers) {
            hpBar.onClose(player, live)
        }
        barOpenPlayers.clear()

        val remains = Npc(resolveNpc(site.remains), coords)
        npcRepo.add(remains, Int.MAX_VALUE)
        if (site.preserveDeathFacing && facingSquare != CoordGrid.NULL) {
            remains.lockFacing(facingSquare, facingWidth, facingLength)
        }
        if (site.crabEffects) {
            remains.spotanim("spotanim.crab_boss_remains_idle_spot")
        }
        remainsNpc = remains
        remainsSpotCycle = mapClock.cycle + REMAINS_FIRST_SPOT_DELAY_CYCLES
        remainsDisintegrating = false
        remainsExpireCycle = mapClock.cycle + REMAINS_CYCLES
    }

    private fun disintegrateRemains(remains: Npc) {
        remainsDisintegrating = true
        remains.hideAllOps()
        remains.anim(site.disintegrateAnimation)
        if (site.crabEffects) {
            remains.spotanim("spotanim.vfx_crab_boss_disintegrate")
        }
    }

    private fun expireRemains(remains: Npc) {
        npcRepo.del(remains, Int.MAX_VALUE)
        remainsNpc = null
        eligibleMiners = emptySet()
        spawnNext()
    }

    private fun updateBar(player: Player, npc: Npc) {
        val remaining = (burrowCycle - mapClock.cycle).coerceIn(0, activeLifetime)
        hpBar.onUpdate(player, npc, remaining, activeLifetime)
    }

    private fun broadcast(text: String) {
        for (player in damagers) {
            player.mes(text, ChatType.Broadcast)
        }
    }

    private fun topDealerMessage(names: List<String>): String =
        when (names.size) {
            1 -> "The top crab crusher was ${names[0]}!"
            2 -> "The top two crab crushers were ${joinNames(names)}!"
            else -> "The top three crab crushers were ${joinNames(names)}!"
        }

    private fun joinNames(names: List<String>): String =
        when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} & ${names[1]}"
            else -> "${names.dropLast(1).joinToString(", ")}, & ${names.last()}"
        }

    public fun crabName(): String {
        val name = resolveNpc(site.crab).name
        return if (name.equals(DEFAULT_NAME, ignoreCase = true)) name.lowercase() else name
    }

    private fun resolveNpc(name: String) =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Unknown npc: $name")

    private companion object {
        private const val DEFAULT_NAME = "Gemstone crab"
        private val ACTIVE_DURATION_CYCLES = 918..967
        private const val REMAINS_CYCLES = 150
        private const val DISINTEGRATE_CYCLES = 4
        private const val TOP_DAMAGE_DEALERS = 16
        private const val BURROW_ANNOUNCE_DELAY_CYCLES = 2
        private const val BURROW_REMOVE_DELAY_CYCLES = 4
        private const val REMAINS_FIRST_SPOT_DELAY_CYCLES = 13
        private const val REMAINS_SPOT_INTERVAL_CYCLES = 14
    }
}

public data class Spot(
    val crab: CoordGrid,
    val cave: CoordGrid? = null,
    val caveExit: CoordGrid? = null,
)

public data class CrabSite(
    val crab: String,
    val remains: String,
    val spots: List<Spot>,
    val areas: List<String>,
    val spawnAnimation: String = "seq.crab_boss_spawn",
    val attackAnimation: String = "seq.crab_boss_attack",
    val kickAnimation: String? = null,
    val deathAnimation: String = "seq.crab_boss_death",
    val disintegrateAnimation: String = "seq.crab_boss_remains_disintegrate",
    val crabEffects: Boolean = true,
    val preserveDeathFacing: Boolean = false,
)
