package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.thieving.FriendscapeThievingPickpocketRow

internal const val COINS = "obj.coins"
internal const val BASE_POUCH_CAP = 28

private const val ARDOUGNE_AREA = "area.thieving_ardougne"
private const val ROGUE_CHANCE_PER_PIECE = 20
private val POUCH_CAPS = intArrayOf(BASE_POUCH_CAP, 56, 84, 140)
private val DIARY_VARBITS =
    listOf(
        "varbit.ardougne_diary_medium_complete",
        "varbit.ardougne_diary_hard_complete",
        "varbit.ardougne_diary_elite_complete",
    )
private val ARDOUGNE_CLOAKS =
    listOf("obj.ardy_cape_medium", "obj.ardy_cape_hard", "obj.ardy_cape_elite")
private val THIEVING_CAPES =
    listOf(
        "obj.skillcape_thieving",
        "obj.skillcape_thieving_trimmed",
        "obj.skillcape_max",
        "obj.skillcape_max_worn",
    )
private val ROGUE_OUTFIT =
    listOf(
        "obj.roguesden_helm",
        "obj.roguesden_body",
        "obj.roguesden_legs",
        "obj.roguesden_gloves",
        "obj.roguesden_boots",
    )

internal fun coinsType(): ItemServerType =
    checkNotNull(ServerCacheManager.getItem(COINS.asRSCM(RSCMType.OBJ)))

/** Medium = 1, hard = 2, elite = 3. A finished diary or its Ardougne cloak, worn or held, counts. */
internal fun ProtectedAccess.ardougneDiaryTier(): Int {
    var tier = 0
    for (index in DIARY_VARBITS.indices) {
        val cloak = ARDOUGNE_CLOAKS[index]
        val done = player.vars[DIARY_VARBITS[index]] == 1
        if (done || cloak in player.worn || cloak in inv) tier = index + 1
    }
    return tier
}

internal fun ProtectedAccess.pouchCap(): Int = POUCH_CAPS[ardougneDiaryTier()]

internal fun ProtectedAccess.pickpocketBoosts(): PickpocketBoosts {
    val tier = ardougneDiaryTier()
    val diary = tier >= 2 || tier == 1 && inArea(ARDOUGNE_AREA, player.coords)
    return PickpocketBoosts(
        glovesOfSilence = "obj.hunting_silent_gloves" in player.worn,
        ardougneDiary = diary,
        thievingCape = THIEVING_CAPES.any { it in player.worn },
    )
}

internal fun ProtectedAccess.rogueDoubleLoot(): Boolean {
    val pieces = ROGUE_OUTFIT.count { it in player.worn }
    return pieces > 0 && random.of(0, 99) < pieces * ROGUE_CHANCE_PER_PIECE
}

internal const val POUCHES_FULL =
    "You need to empty your coin pouches before you can continue pickpocketing."
internal const val NO_SPACE = "You don't have enough inventory space."

internal fun ProtectedAccess.pickpocketBlocker(target: FriendscapeThievingPickpocketRow): String? {
    val pouch = target.pouch
    val held = if (pouch == null) 0 else inv.count(pouch.internalName)
    if (pouch != null && held >= pouchCap()) return POUCHES_FULL
    if (inv.isFull() && held == 0) return NO_SPACE
    return null
}

internal fun ProtectedAccess.giveAll(stolen: List<Stolen>): Boolean {
    if (stolen.isEmpty()) return true
    return player
        .invTransaction(inv) {
            val target = select(inv)
            for (item in stolen) add(target, item.obj.id, item.count)
        }
        .success
}

/**
 * Opens [all] or one of the held pouches and returns how many were opened. Pouches whose coins
 * would push the stack past Int.MAX_VALUE stay shut, and the swap of pouches for coins is a single
 * transaction, so a failed add leaves every pouch in place.
 */
internal fun ProtectedAccess.openPouches(row: FriendscapeThievingPickpocketRow, all: Boolean): Int {
    val pouch = row.pouch ?: return 0
    val held = inv.count(pouch.internalName)
    if (held == 0) return 0
    val wanted = if (all) held else 1
    val room = Int.MAX_VALUE.toLong() - inv.count(COINS)
    var opened = 0
    var coins = 0L
    while (opened < wanted) {
        val next = ThievingLoot.pouchCoins(random, row)
        if (coins + next > room) break
        coins += next
        opened++
    }
    if (opened == 0) {
        mes("You don't have enough space for any more coins.")
        return 0
    }
    val swapped =
        player
            .invTransaction(inv) {
                val target = select(inv)
                delete(target, pouch.id, opened)
                if (coins > 0) add(target, coinsType().id, coins.toInt())
            }
            .success
    if (!swapped) {
        mes(NO_SPACE)
        return 0
    }
    val message = if (opened > 1) "You open all of the pouches." else "You open the coin pouch."
    mes(message, ChatType.Spam)
    return opened
}
