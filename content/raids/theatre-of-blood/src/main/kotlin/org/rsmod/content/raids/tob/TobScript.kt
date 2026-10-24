package org.rsmod.content.raids.tob

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.content.raids.tob.rewards.TobLoot
import org.rsmod.content.raids.tob.rewards.TobLoot.claimLoot
import org.rsmod.content.raids.tob.rooms.SotetsegRoom
import org.rsmod.content.raids.tob.rooms.TreasureRoom
import org.rsmod.content.raids.tob.rooms.VerzikRoom
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TobScript
@Inject
internal constructor(private val raids: TobRaids, private val parties: TobParties) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.StartCycle> { raids.tick() }
        onPlayerLogout { raids.handleLogout(player) }

        onOpLoc1("loc.tob_surface_raid_entrance") { with(raids) { enterTheatre() } }
        onOpLoc1("loc.tob_surface_notice_board") { partyBoard() }
        for (chest in LOBBY_CHESTS) onOpLoc1(chest) { claimFromLobby() }

        onOpLoc1("loc.tob_arena_barrier") { with(raids) { passBarrier(it.loc.coords) } }
        for (passage in PASSAGES) {
            onOpLoc1(passage) { with(raids) { usePassage(it.loc.coords) } }
            onOpLoc2(passage) { with(raids) { usePassage(it.loc.coords) } }
        }
        onOpLoc1("loc.tob_sotetseg_darkrealm_exit") { leaveShadowRealm() }
        onOpLoc1("loc.tob_dungeon_verzik_throne_door_opened") { enterVault() }
        for (chest in VAULT_CHESTS) onOpLoc1(chest) { openVaultChest(it.loc) }
        onOpLoc1("loc.tob_treasureroom_stairsup") { with(raids) { leaveTheatre() } }
        onOpLoc1("loc.tob_treasureroom_teleportout") { with(raids) { leaveTheatre() } }

        onOpNpc1(VerzikRoom.INITIAL) { talkToVerzik(it.npc) }
        onOpNpc3(VerzikRoom.INITIAL) { talkToVerzik(it.npc) }

        for (name in TobNpcs.ALL) {
            val type = ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: continue
            onNpcQueue(type, "queue.death") { with(raids) { tobNpcDeath() } }
            onModifyNpcHit(type) { raids.modifyHit(this) }
            onNpcHit(type) { raids.onImpact(this) }
        }
    }

    private suspend fun ProtectedAccess.partyBoard() {
        val party = parties.partyOf(player)
        if (party == null) {
            val pick =
                choice3("Create a party.", 1, "Join a party.", 2, "Never mind.", 3, title = "Theatre of Blood")
            when (pick) {
                1 -> {
                    parties.create(player)
                    mes("You have formed a party. Others can join by naming you at the notice board.")
                }
                2 -> {
                    val leader = stringDialog("Enter the party leader's name:")
                    if (leader.isBlank()) return
                    when (val result = parties.join(player, leader)) {
                        is TobParties.JoinResult.Joined -> mes("You have joined the party.")
                        is TobParties.JoinResult.Failed -> mes(result.reason)
                    }
                }
            }
            return
        }
        val pick = choice3("View my party.", 1, "Leave my party.", 2, "Never mind.", 3, title = "Theatre of Blood")
        when (pick) {
            1 -> mesbox("Party (${party.members.size}/${TobScaling.MAX_PARTY}): ${parties.memberNames(party).joinToString(", ")}")
            2 -> {
                if (raids.raidFor(player) != null) {
                    mes("You can't leave your party while inside the Theatre.")
                    return
                }
                parties.leave(player)
                mes("You have left the party.")
            }
        }
    }

    private suspend fun ProtectedAccess.talkToVerzik(npc: org.rsmod.game.entity.Npc) {
        val raid = raids.raidFor(player) ?: return
        val room = raid.rooms.filterIsInstance<VerzikRoom>().firstOrNull() ?: return
        if (!room.isThrone(npc)) return
        val start =
            choice2("Yes, let's begin.", true, "No, not yet.", false, title = "Start the fight with Verzik?")
        if (!start) return
        if (raids.startRoom(player, room)) {
            npc.say("Oh. Hello again.")
        }
    }

    private suspend fun ProtectedAccess.leaveShadowRealm() {
        val raid = raids.raidFor(player) ?: return
        val room = raid.rooms.filterIsInstance<SotetsegRoom>().firstOrNull() ?: return
        room.exitRealm(player)
    }

    private suspend fun ProtectedAccess.enterVault() {
        val raid = raids.raidFor(player) ?: return
        val vault = raid.rooms.last() as? TreasureRoom ?: return
        vault.prepare()
        raid.currentRoom = vault.index
        telejump(raid.coord(vault.entry), TeleportType.Exempt)
        vault.syncChests(player)
        mes("You enter Verzik's treasure vault.")
    }

    private suspend fun ProtectedAccess.openVaultChest(loc: BoundLocInfo) {
        val raid = raids.raidFor(player) ?: return
        val vault = raid.rooms.last() as? TreasureRoom ?: return
        val slot = vault.chestSlot(raid.static(loc.coords))
        if (slot < 0) return
        if (vault.ownerOf(slot) != player.uuid) {
            mes("This chest doesn't belong to you.")
            return
        }
        claim()
    }

    private suspend fun ProtectedAccess.claimFromLobby() {
        if (!TobLoot.hasUnclaimed(player)) {
            mes("The chest is empty.")
            return
        }
        claim()
    }

    private suspend fun ProtectedAccess.claim() {
        when (val result = claimLoot()) {
            TobLoot.Claim.Empty -> mes("The chest is empty.")
            is TobLoot.Claim.NoRoom ->
                mes("You need at least ${result.slotsNeeded} free inventory spaces to take your loot.")
            is TobLoot.Claim.Claimed -> {
                anim("seq.human_openchest")
                mes("You take your rewards from the chest.")
            }
        }
    }

    private companion object {
        val PASSAGES = listOf("loc.tob_dungeon_walkway_exit_clickbox", "loc.tob_dungeon_xarpus_arena_door_exit")
        val LOBBY_CHESTS =
            listOf(
                "loc.tob_rewards_chest_lobby_multi",
                "loc.tob_rewards_chest_lobby_open",
                "loc.tob_rewards_chest_lobby_closed",
            )
        val VAULT_CHESTS =
            TreasureRoom.CHEST_LOCS + listOf("loc.tob_treasureroom_chest_mine_standard", "loc.tob_treasureroom_chest_mine_rare")
    }
}
