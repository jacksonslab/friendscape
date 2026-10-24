package org.rsmod.content.raids.tob

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.deathResetTimers
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.raids.tob.rewards.TobLoot
import org.rsmod.content.raids.tob.rewards.TobRaiderResult
import org.rsmod.content.raids.tob.rewards.TobRewards
import org.rsmod.content.raids.tob.rooms.BloatRoom
import org.rsmod.content.raids.tob.rooms.MaidenRoom
import org.rsmod.content.raids.tob.rooms.NylocasRoom
import org.rsmod.content.raids.tob.rooms.SotetsegRoom
import org.rsmod.content.raids.tob.rooms.TreasureRoom
import org.rsmod.content.raids.tob.rooms.VerzikRoom
import org.rsmod.content.raids.tob.rooms.XarpusRoom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid

@Singleton
internal class TobRaids
@Inject
constructor(
    val ctx: TobContext,
    private val instances: InstanceManager,
    private val parties: TobParties,
    private val rewards: TobRewards,
) {
    private val raids = HashMap<InstanceId, TobRaid>()
    private val byPlayer = HashMap<Long, TobRaid>()

    fun raidFor(player: Player): TobRaid? = player.uuid?.let(byPlayer::get)?.takeIf { !it.ended }

    fun raidFor(npc: Npc): TobRaid? = instances.instanceForNpc(npc)?.let(raids::get)?.takeIf { !it.ended }

    fun roomFor(npc: Npc): TobRoom? = raidFor(npc)?.rooms?.firstOrNull { it.owns(npc) }

    fun playerFor(uuid: Long): Player? = ctx.playerList.firstOrNull { it.uuid == uuid }

    fun attach(raid: TobRaid, npc: Npc) = instances.attachNpc(raid.session.id, npc)

    suspend fun ProtectedAccess.enterTheatre() {
        if (raidFor(player) != null) {
            mes("You are already inside the Theatre.")
            return
        }
        if (instances.sessionForPlayer(player) != null) {
            mes("You can't enter the Theatre while you're inside another instance.")
            return
        }
        val party = parties.partyOf(player) ?: parties.create(player)
        if (party == null) {
            mes("You can't enter the Theatre right now.")
            return
        }
        val existing = party.raid?.takeIf { !it.ended }
        if (existing != null) {
            joinRaid(existing)
            return
        }
        if (party.leader != player.uuid) {
            mes("Your party leader must enter the Theatre first.")
            return
        }
        createRaid(party)
    }

    private fun ProtectedAccess.createRaid(party: TobParty) {
        val result = instances.create(player, INSTANCE_KEY, SPEC, InstanceAccess.Private, ctx.cycle)
        val created =
            when (result) {
                is InstanceManager.Result.Created -> result
                is InstanceManager.Result.Joined -> return
                is InstanceManager.Result.Failed -> {
                    mes(result.reason)
                    return
                }
            }
        val base = instances.resolveCoord(created.session, TobLayout.STATIC_SOUTH_WEST) ?: return
        val raid = TobRaid(party.leader, created.session, base, this@TobRaids)
        raid.rooms =
            listOf(
                MaidenRoom(raid, ctx),
                BloatRoom(raid, ctx),
                NylocasRoom(raid, ctx),
                SotetsegRoom(raid, ctx),
                XarpusRoom(raid, ctx),
                VerzikRoom(raid, ctx),
                TreasureRoom(raid, ctx),
            )
        raids[created.session.id] = raid
        party.raid = raid
        raid.rooms.first().prepare()
        admit(raid, raid.coord(raid.rooms.first().entry))
        instances.finalizeEntry(player, created.session, ctx.cycle)
        mes("You enter the Theatre of Blood...")
    }

    private fun ProtectedAccess.joinRaid(raid: TobRaid) {
        if (raid.rosterLocked) {
            mes("Your party has already begun the challenge; you can no longer join them.")
            return
        }
        when (val result = instances.join(player, raid.session, ctx.cycle, forceAccess = true)) {
            is InstanceManager.Result.Failed -> {
                mes(result.reason)
                return
            }
            else -> Unit
        }
        admit(raid, raid.coord(raid.rooms.first().entry))
        instances.finalizeEntry(player, raid.session, ctx.cycle)
        mes("You enter the Theatre of Blood...")
    }

    private fun ProtectedAccess.admit(raid: TobRaid, dest: CoordGrid) {
        val id = player.uuid ?: return
        raid.raiders.getOrPut(id) { TobRaider(id, player.displayName) }.left = false
        byPlayer[id] = raid
        telejump(dest, TeleportType.Exempt)
    }

    suspend fun ProtectedAccess.leaveTheatre() {
        val raid = raidFor(player)
        if (raid == null) {
            telejump(EXIT, TeleportType.Exempt)
            return
        }
        instances.leave(player, raid.session, ctx.cycle)
        markLeft(raid, player)
        telejump(EXIT, TeleportType.Exempt)
        if (TobLoot.hasUnclaimed(player)) {
            mes("Your unclaimed loot is waiting in the Rewards Chest in Ver Sinhaza.")
        }
    }

    suspend fun ProtectedAccess.passBarrier(barrierCoords: CoordGrid) {
        val raid = raidFor(player) ?: return
        val raider = raid.raider(player) ?: return
        val static = raid.static(barrierCoords)
        val (room, barrier) = barrierAt(raid, static) ?: return
        val inward = !barrier.isInside(raid.static(player.coords))
        val refusal =
            room.allowsPass(player, inward)
                ?: when {
                    inward && raider.dead -> "You can't rejoin the fight until your party finishes this room."
                    !inward && room.isActive -> "You can't leave while the fight is in progress."
                    inward && room.index > raid.currentRoom -> "You can't go that way yet."
                    else -> null
                }
        if (refusal != null) {
            mes(refusal)
            return
        }
        telejump(raid.coord(barrier.across(static, inward)), TeleportType.Exempt)
        if (inward && room.state == TobRoomState.Waiting && room.startsOnEntry) {
            if (room.index == 0) raid.lockRoster()
            room.start()
        }
    }

    private fun barrierAt(raid: TobRaid, static: CoordGrid): Pair<TobRoom, TobBarrier>? {
        for (room in raid.rooms) {
            for (barrier in room.barriers) {
                val axis = if (barrier.vertical) static.x else static.z
                if (axis != barrier.line) continue
                if (room.arena.contains(barrier.across(static, inward = true))) return room to barrier
            }
        }
        return null
    }

    suspend fun ProtectedAccess.usePassage(passageCoords: CoordGrid) {
        val raid = raidFor(player) ?: return
        val static = raid.static(passageCoords)
        val room = raid.rooms.firstOrNull { it.hasPassage(static) } ?: return
        if (!room.isComplete) {
            mes("You can't go through here until your party has cleared the room.")
            return
        }
        val next = raid.rooms.getOrNull(room.index + 1) ?: return
        next.prepare()
        if (next.index > raid.currentRoom) raid.currentRoom = next.index
        telejump(raid.coord(next.entry), TeleportType.Exempt)
    }

    fun startRoom(player: Player, room: TobRoom): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.raider(player)?.dead == true || room.state != TobRoomState.Waiting) return false
        if (room.index == 0) raid.lockRoster()
        room.start()
        return true
    }

    fun onRoomComplete(raid: TobRaid, room: TobRoom) {
        val elapsed = ctx.cycle - room.startedTick
        raid.broadcast("Wave '${room.displayName}' complete! Duration: <col=ff0000>${formatTicks(elapsed)}</col>")
        raid.mvpOf(room.index)?.let { it.mvpPoints += room.mvpPoints }
        for (raider in raid.raiders.values) {
            if (!raider.dead) continue
            raider.dead = false
            playerFor(raider.uuid)?.mes("You have been revived by your party.")
        }
        if (room is VerzikRoom) finishRaid(raid)
    }

    private fun finishRaid(raid: TobRaid) {
        val results =
            raid.raiders.values
                .filter { !it.left }
                .mapNotNull { raider ->
                    val player = playerFor(raider.uuid) ?: return@mapNotNull null
                    TobRaiderResult(player, raider.deaths, raider.mvpPoints)
                }
        val chests = rewards.roll(results)
        val total = ctx.cycle - raid.startedTick
        for ((slot, chest) in chests.withIndex()) {
            val raider = raid.raider(chest.player) ?: continue
            raider.chestSlot = slot
            TobLoot.store(chest.player, chest.loot)
            val count = chest.player.vars[COMPLETIONS] + 1
            VarPlayerIntMapSetter.set(chest.player, COMPLETIONS, count)
            chest.player.mes("Your completed Theatre of Blood count is: <col=ff0000>$count</col>.")
            chest.player.mes("Theatre of Blood total completion time: <col=ff0000>${formatTicks(total)}</col>")
        }
        (raid.rooms.last() as TreasureRoom).setChests(chests)
    }

    suspend fun ProtectedAccess.raidDeath(): Boolean {
        val raid = raidFor(player) ?: return false
        val raider = raid.raider(player) ?: return false
        val room = raid.roomAt(raid.static(player.coords)) ?: raid.room()
        stopAction()
        delay(2)
        soundSynth("synth.human_death")
        anim("seq.human_death")
        delay(4)
        combatClearQueue()
        clearQueue("queue.death")
        raider.deaths++
        raider.dead = room.isActive
        room.onRaiderDeath(player)
        telejump(raid.coord(room.spectate), TeleportType.Exempt)
        resetAnim()
        player.disablePrayers()
        player.cureAllToxins()
        player.deathResetTimers()
        statRestoreAll(ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) })
        if (raider.dead) {
            mes("You have died. You'll be revived if your party clears the room.")
            raid.broadcast("<col=ff0000>${player.displayName} has died.</col>")
        }
        return true
    }

    fun handleLogout(player: Player) {
        val raid = raidFor(player)
        if (raid != null) {
            val room = raid.roomAt(raid.static(player.coords))
            if (room != null && room.isActive && room.inArena(player.coords)) {
                raid.raider(player)?.let { it.deaths++ }
            }
            markLeft(raid, player)
        }
        parties.leave(player)
    }

    fun deniesAttack(player: Player, npc: Npc): String? {
        val raid = raidFor(npc) ?: return null
        val raider = raid.raider(player) ?: return "You can't attack that."
        if (raider.dead) return "You're dead; you can't fight until your party finishes this room."
        return null
    }

    fun modifyHit(event: NpcHitEvents.Modify) {
        val room = roomFor(event.npc) ?: return
        val attacker =
            if (event.hit.isFromPlayer) event.hit.sourceUid?.let { PlayerUid(it).resolve(ctx.playerList) } else null
        room.modifyHit(event, attacker)
    }

    fun onImpact(event: NpcHitEvents.Impact) {
        val room = roomFor(event.npc) ?: return
        val attacker = if (event.hit.isFromPlayer) event.hit.resolvePlayerSource(ctx.playerList) else null
        if (attacker != null && event.hit.damage > 0 && room.bossNpc() === event.npc) {
            room.raid.recordBossDamage(attacker, room.index, event.hit.damage)
        }
        room.onHit(event, attacker)
    }

    suspend fun StandardNpcAccess.tobNpcDeath() {
        val room = roomFor(npc)
        val death = room?.onNpcDeath(npc)
        noneMode()
        hideAllOps()
        if (death != null) {
            anim(death.first)
            delay(death.second)
        }
        room?.untrack(npc)
        ctx.removeNpc(npc)
    }

    fun tick() {
        for (raid in raids.values.toList()) {
            if (raid.ended) continue
            for (raider in raid.raiders.values.toList()) {
                if (raider.left) continue
                val player = playerFor(raider.uuid)
                if (player == null) {
                    markLeft(raid, raider)
                } else if (!raid.contains(player.coords)) {
                    markLeft(raid, player)
                }
            }
            raid.rooms.forEach(TobRoom::process)
            checkWipe(raid)
            if (raid.players().isEmpty()) endRaid(raid)
        }
    }

    private fun checkWipe(raid: TobRaid) {
        if (!raid.rosterLocked || raid.players().isEmpty()) return
        if (raid.wipeDeadline >= 0) {
            if (ctx.cycle >= raid.wipeDeadline) endRaid(raid)
            return
        }
        if (raid.alivePlayers().isNotEmpty()) return
        raid.wipeDeadline = ctx.cycle + WIPE_DELAY
        raid.broadcast("<col=ff0000>Your party has fallen. The Theatre has claimed another team...</col>")
    }

    private fun endRaid(raid: TobRaid) {
        if (raid.ended) return
        for (player in raid.players()) {
            instances.leave(player, raid.session, ctx.cycle)
            markLeft(raid, player)
            player.clearInteraction()
            player.abortRoute()
            PathingEntityCommon.telejump(player, ctx.collision, EXIT)
        }
        raid.ended = true
        raid.rooms.forEach(TobRoom::dispose)
        raids.remove(raid.session.id)
        byPlayer.entries.removeIf { it.value === raid }
        parties.clearRaid(raid)
    }

    private fun markLeft(raid: TobRaid, player: Player) {
        val raider = raid.raider(player) ?: return
        markLeft(raid, raider)
        if (TobLoot.hasUnclaimed(player) && raider.chestSlot >= 0) {
            player.mes("Your unclaimed loot is waiting in the Rewards Chest in Ver Sinhaza.")
        }
    }

    private fun markLeft(raid: TobRaid, raider: TobRaider) {
        raider.left = true
        byPlayer.remove(raider.uuid, raid)
    }

    private fun formatTicks(ticks: Int): String {
        val seconds = ticks * 3 / 5
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        const val INSTANCE_KEY = "theatre_of_blood"
        private const val COMPLETIONS = "varp.total_completed_theatreofblood"
        private const val WIPE_DELAY = 5

        val EXIT: CoordGrid = CoordGrid(3670, 3218, 0)

        private val MAIDEN_ENTRY = MaidenRoom.ENTRY

        private val SPEC =
            InstanceSpec(
                fee = 0,
                maxPlayers = TobScaling.MAX_PARTY,
                reclaimTicks = 0,
                graceTicks = 0,
                destroyWhenEmpty = true,
                area =
                    InstanceArea.template(
                        template = TobLayout.TEMPLATE,
                        enterCoord =
                            RegionLocal(
                                MAIDEN_ENTRY.level,
                                MAIDEN_ENTRY.mx,
                                MAIDEN_ENTRY.mz,
                                MAIDEN_ENTRY.lx,
                                MAIDEN_ENTRY.lz,
                            ),
                        exitCoord = EXIT,
                    ),
                settingsRowId = 0,
                bossName = "Theatre of Blood",
            )
    }
}
