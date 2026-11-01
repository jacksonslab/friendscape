package org.rsmod.content.other.bankkey

import dev.openrune.types.util.UncheckedType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

internal object BankKeyGrant {
    @OptIn(UncheckedType::class)
    fun deliver(bank: Inventory, capacity: Int, keyId: Int, alreadyGranted: Boolean, owned: Boolean): Boolean {
        if (alreadyGranted || owned || bank.any { it?.id == keyId && it.count > 0 }) return true
        val slot = (0 until minOf(capacity, bank.size)).firstOrNull { bank[it] == null }
            ?: return false
        bank[slot] = InvObj(keyId, 1)
        return true
    }
}
