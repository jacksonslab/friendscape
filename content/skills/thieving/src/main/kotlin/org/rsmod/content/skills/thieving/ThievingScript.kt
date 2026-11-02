package org.rsmod.content.skills.thieving

import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.table.thieving.FriendscapeThievingChestRow
import org.rsmod.api.table.thieving.FriendscapeThievingPickpocketRow
import org.rsmod.api.table.thieving.FriendscapeThievingStallRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ThievingScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : PluginScript() {
    private val restockingUntil = HashMap<CoordGrid, Int>()

    override fun ScriptContext.startup() {
        for (stall in FriendscapeThievingStallRow.all()) {
            onOpLoc2(stall.loc.internalName) { stealFromStall(it.loc, stall) }
        }
        for (target in FriendscapeThievingPickpocketRow.all()) {
            for (npc in target.npcs) {
                when (target.op) {
                    1 -> onOpNpc1(npc.internalName) { pickpocket(it.npc, target) }
                    else -> onOpNpc3(npc.internalName) { pickpocket(it.npc, target) }
                }
            }
            val pouch = target.pouch ?: continue
            onOpHeld1(pouch.internalName) { openPouches(target, all = true) }
            onOpHeld2(pouch.internalName) { openPouches(target, all = false) }
        }
        for (chest in FriendscapeThievingChestRow.all()) {
            onOpLoc2(chest.loc.internalName) { lootChest(it.loc, chest) }
            onOpLoc1(chest.loc.internalName) { openChest(chest) }
        }
    }

    private suspend fun ProtectedAccess.stealFromStall(
        loc: BoundLocInfo,
        stall: FriendscapeThievingStallRow,
    ) {
        if (player.isFrozen) return
        arriveDelay()
        faceLoc(loc)
        if (stat(THIEVING) < stall.level) {
            mes("You need to be level ${stall.level} to steal from this stall.")
            return
        }
        if (restockingUntil.getOrDefault(loc.coords, 0) > mapClock) return
        if (inv.isFull()) {
            mes(NO_SPACE)
            return
        }
        mes("You attempt to steal from the ${stall.loc.name.lowercase()}.", ChatType.Spam)
        val spotter = findSpotter(stall.owners + stall.guards)
        if (spotter != null) {
            caughtAtStall(spotter, stall.guards)
            return
        }
        anim(STALL_SEQ)
        delay(2)
        val loot = ThievingLoot.roll(random, stall.loot) ?: return
        if (!giveAll(listOf(loot))) {
            mes(NO_SPACE)
            return
        }
        mes("You steal ${describe(loot.obj, loot.count)}.")
        statAdvance(THIEVING, stall.xp / 10.0)
        restock(loc, stall.empty?.internalName, stall.respawn)
    }

    private fun ProtectedAccess.findSpotter(watchers: List<NpcServerType>): Npc? =
        npcRepo
            .findAll(ZoneKey.from(player.coords), zoneRadius = 1)
            .filter { npc -> watchers.any { npc.type.id == it.id } }
            .filter { it.coords.level == player.coords.level }
            .filter { it.coords.chebyshevDistance(player.coords) <= SPOT_RANGE }
            .filter { lineOfSight(it.coords, player.coords) }
            .minByOrNull { it.coords.chebyshevDistance(player.coords) }

    private fun ProtectedAccess.caughtAtStall(spotter: Npc, guards: List<NpcServerType>) {
        spotter.say(CAUGHT_SHOUT)
        val guard =
            if (guards.any { spotter.type.id == it.id }) {
                spotter
            } else {
                npcRepo
                    .findAll(ZoneKey.from(player.coords), zoneRadius = 1)
                    .filter { npc -> guards.any { npc.type.id == it.id } }
                    .minByOrNull { it.coords.chebyshevDistance(player.coords) }
            }
        guard?.opPlayer2(player, aiPlayerInteractions)
    }

    private fun ProtectedAccess.restock(loc: BoundLocInfo, empty: String?, ticks: Int) {
        if (empty != null) {
            locRepo.change(loc, empty, ticks)
        } else {
            restockingUntil[loc.coords] = mapClock + ticks
        }
    }

    private suspend fun ProtectedAccess.pickpocket(
        npc: Npc,
        target: FriendscapeThievingPickpocketRow,
    ) {
        if (player.isFrozen) return
        val owner = pocketOwner(npc, target)
        if (stat(THIEVING) < target.level) {
            mes("You need to be level ${target.level} to pickpocket $owner.")
            return
        }
        val blocked = pickpocketBlocker(target)
        if (blocked != null) {
            mes(blocked)
            return
        }
        faceEntitySquare(npc)
        mes("You attempt to pick $owner's pocket.", ChatType.Spam)
        delay(1)
        val level = stat(THIEVING)
        val chance = ThievingChance.successChance(target.low, target.high, level, pickpocketBoosts())
        if (chance <= random.randomDouble()) {
            failPickpocket(npc, target, owner)
            return
        }
        val stolen = ThievingLoot.pickpocket(random, target, rogueDoubleLoot(), coinsType())
        if (!giveAll(stolen)) {
            mes(NO_SPACE)
            return
        }
        mes("You pick $owner's pocket.", ChatType.Spam)
        anim(PICKPOCKET_SEQ)
        soundSynth(PICK_SYNTH)
        statAdvance(THIEVING, target.xp / 10.0)
    }

    private suspend fun ProtectedAccess.failPickpocket(
        npc: Npc,
        target: FriendscapeThievingPickpocketRow,
        owner: String,
    ) {
        mes("You fail to pick $owner's pocket.", ChatType.Spam)
        if (player.vars[SHADOW_VEIL_ACTIVE] == 1 && random.of(0, 99) < SHADOW_VEIL_CHANCE) {
            mes("Your attempt to steal goes unnoticed.", ChatType.Spam)
            return
        }
        npc.say(target.caught)
        npc.facePlayer(player)
        stun()
        delay(1)
        spotanim(STUN_SPOTANIM, height = STUN_SPOTANIM_HEIGHT)
        anim(STUN_BLOCK_SEQ)
        soundSynth(STUN_SYNTH)
        val damage = random.of(target.stunMin, target.stunMax)
        queueHit(delay = 1, type = HitType.Typeless, damage = damage)
        delay(1)
        mes("You've been stunned!", ChatType.Spam)
    }

    private fun ProtectedAccess.stun() {
        player.frozen = true
        player.routeDestination.clear()
        player.timer(FREEZE_TIMER, STUN_TICKS)
    }

    private suspend fun ProtectedAccess.lootChest(
        loc: BoundLocInfo,
        chest: FriendscapeThievingChestRow,
    ) {
        if (player.isFrozen) return
        arriveDelay()
        faceLoc(loc)
        if (stat(THIEVING) < chest.level) {
            mes("You need a Thieving level of ${chest.level} to open this chest.")
            return
        }
        if (chest.lockpick && LOCKPICK !in inv) {
            mes("You need a lockpick to open this chest.")
            return
        }
        if (chest.trapped) mes("You search the chest for traps.", ChatType.Spam)
        anim(CHEST_SEQ)
        delay(2)
        val rolled = ThievingLoot.roll(random, chest.loot)
        val loot = ThievingLoot.everything(random, chest.always) + listOfNotNull(rolled)
        if (!giveAll(ThievingLoot.merge(loot))) {
            mes(NO_SPACE)
            return
        }
        if (chest.trapped) mes("You find a trap on the chest and disable it.", ChatType.Spam)
        mes("You open the chest and find some treasure inside!")
        statAdvance(THIEVING, chest.xp / 10.0)
        locRepo.change(loc, chest.empty.internalName, chest.respawn)
    }

    private fun ProtectedAccess.openChest(chest: FriendscapeThievingChestRow) {
        if (!chest.trapped) {
            mes("This chest is locked.")
            return
        }
        mes("You have activated a trap on the chest.")
        val damage = (stat("stat.hitpoints") / TRAP_DIVISOR).coerceAtLeast(1)
        queueHit(delay = 1, type = HitType.Typeless, damage = damage)
    }

    private fun pocketOwner(npc: Npc, target: FriendscapeThievingPickpocketRow): String {
        target.pocketName?.let { return it }
        val name = npc.visType.name
        return if (target.lowercase) "the ${name.lowercase()}" else name
    }

    private fun describe(obj: ItemServerType, count: Int): String {
        val lower = obj.name.replaceFirstChar { it.lowercase() }
        if (count > 1) return "$count ${lower}s"
        val article = if (lower.first() in "aeiou") "an" else "a"
        return "$article $lower"
    }

    private companion object {
        const val THIEVING = "stat.thieving"
        const val SPOT_RANGE = 5
        const val SHADOW_VEIL_ACTIVE = "varbit.arceuus_shadow_veil_active"
        const val SHADOW_VEIL_CHANCE = 15
        const val CAUGHT_SHOUT = "Hey! Get your hands off there!"
        const val STALL_SEQ = "seq.human_pickuptable"
        const val CHEST_SEQ = "seq.human_pickuptable"
        const val PICKPOCKET_SEQ = "seq.human_pickpocket"
        const val PICK_SYNTH = "synth.pick"
        const val STUN_SPOTANIM = "spotanim.stunned_thieving"
        const val STUN_SPOTANIM_HEIGHT = 124
        const val STUN_BLOCK_SEQ = "seq.human_unarmedblock"
        const val STUN_SYNTH = "synth.thieving_stunned"
        const val STUN_TICKS = 9
        const val FREEZE_TIMER = "timer.combat_freeze"
        const val LOCKPICK = "obj.lockpick"
        const val TRAP_DIVISOR = 5
    }
}
