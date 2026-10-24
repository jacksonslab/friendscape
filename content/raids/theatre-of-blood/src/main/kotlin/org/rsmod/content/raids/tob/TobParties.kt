package org.rsmod.content.raids.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

internal class TobParty(var leader: Long) {
    val members: MutableList<Long> = mutableListOf(leader)
    var raid: TobRaid? = null

    val isFull: Boolean
        get() = members.size >= TobScaling.MAX_PARTY
}

@Singleton
internal class TobParties @Inject constructor(private val playerList: PlayerList) {
    private val byMember = HashMap<Long, TobParty>()

    fun partyOf(player: Player): TobParty? = player.uuid?.let(byMember::get)

    fun create(player: Player): TobParty? {
        val id = player.uuid ?: return null
        if (byMember.containsKey(id)) return null
        val party = TobParty(id)
        byMember[id] = party
        return party
    }

    sealed class JoinResult {
        data class Joined(val party: TobParty) : JoinResult()

        data class Failed(val reason: String) : JoinResult()
    }

    fun join(player: Player, leaderName: String): JoinResult {
        val id = player.uuid ?: return JoinResult.Failed("You can't join a party right now.")
        if (byMember.containsKey(id)) return JoinResult.Failed("You are already in a party.")
        val leader =
            playerList.firstOrNull { it.matchesName(leaderName) }
                ?: return JoinResult.Failed("Nobody called '$leaderName' is online.")
        val party =
            partyOf(leader)?.takeIf { it.leader == leader.uuid }
                ?: return JoinResult.Failed("${leader.displayName} is not leading a party.")
        if (party.isFull) return JoinResult.Failed("That party is full.")
        val raid = party.raid
        if (raid != null && raid.rosterLocked) {
            return JoinResult.Failed("That party has already begun the challenge.")
        }
        party.members += id
        byMember[id] = party
        messageParty(party, "${player.displayName} has joined the party.")
        return JoinResult.Joined(party)
    }

    fun leave(player: Player) {
        val id = player.uuid ?: return
        val party = byMember.remove(id) ?: return
        party.members.remove(id)
        if (party.members.isEmpty()) return
        if (party.leader == id) {
            party.leader = party.members.first()
            val name = playerList.firstOrNull { it.uuid == party.leader }?.displayName ?: "Someone"
            messageParty(party, "$name is now the party leader.")
        }
        messageParty(party, "${player.displayName} has left the party.")
    }

    fun clearRaid(raid: TobRaid) {
        byMember.values.filter { it.raid === raid }.forEach { it.raid = null }
    }

    fun memberNames(party: TobParty): List<String> =
        party.members.map { member ->
            val name = playerList.firstOrNull { it.uuid == member }?.displayName ?: "(offline)"
            if (member == party.leader) "$name (leader)" else name
        }

    private fun messageParty(party: TobParty, text: String) {
        for (member in party.members) {
            playerList.firstOrNull { it.uuid == member }?.mes(text)
        }
    }

    private fun Player.matchesName(name: String): Boolean =
        displayName.equals(name.trim(), ignoreCase = true) || username.equals(name.trim(), ignoreCase = true)
}
