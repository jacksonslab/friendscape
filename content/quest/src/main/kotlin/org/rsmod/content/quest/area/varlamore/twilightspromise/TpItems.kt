package org.rsmod.content.quest.area.varlamore.twilightspromise

import org.rsmod.api.player.protect.ProtectedAccess

internal enum class GiveResult {
    Given,
    AlreadyHas,
    NoSpace,
}

internal object TpItems {
    const val FEED_BATCH = 10

    fun ProtectedAccess.giveOnce(obj: String): GiveResult {
        if (playerContainsObj(obj)) {
            return GiveResult.AlreadyHas
        }
        if (invAdd(inv, obj).failure) {
            return GiveResult.NoSpace
        }
        return GiveResult.Given
    }

    fun ProtectedAccess.takeOne(obj: String): Boolean = invDel(inv, obj).success

    fun ProtectedAccess.giveFeed(): Int {
        val amount = minOf(FEED_BATCH, inv.freeSpace())
        if (amount <= 0 || invAdd(inv, TpObj.FEED, amount).failure) {
            return 0
        }
        return amount
    }
}
