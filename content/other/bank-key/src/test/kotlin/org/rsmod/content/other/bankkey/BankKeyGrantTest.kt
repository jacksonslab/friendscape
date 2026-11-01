@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.bankkey

import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

class BankKeyGrantTest {
    private fun bank(vararg objs: InvObj?): Inventory =
        Inventory(InventoryServerType(size = objs.size), arrayOf(*objs))

    @Test
    fun `grant inserts exactly one key without changing other stacks`() {
        val coins = InvObj(995, Int.MAX_VALUE)
        val bank = bank(coins, null)
        assertTrue(BankKeyGrant.deliver(bank, 2, 63490, false, false))
        assertSame(coins, bank[0])
        assertEquals(InvObj(63490, 1), bank[1])
        repeat(5) { assertTrue(BankKeyGrant.deliver(bank, 2, 63490, false, false)) }
        assertEquals(1, bank.sumOf { if (it?.id == 63490) it.count else 0 })
    }

    @Test
    fun `full bank defers grant and never overwrites or falls back to inventory`() {
        val original = InvObj(995, Int.MAX_VALUE)
        val bank = bank(original)
        repeat(3) { assertFalse(BankKeyGrant.deliver(bank, 1, 63490, false, false)) }
        assertSame(original, bank[0])
        bank[0] = null
        assertTrue(BankKeyGrant.deliver(bank, 1, 63490, false, false))
        assertEquals(InvObj(63490, 1), bank[0])
    }

    @Test
    fun `locked capacity is not used and an existing key elsewhere is not duplicated`() {
        val bank = bank(InvObj(995, 1), null)
        assertFalse(BankKeyGrant.deliver(bank, 1, 63490, false, false))
        assertTrue(BankKeyGrant.deliver(bank, 2, 63490, false, true))
        assertNull(bank[1])
        assertTrue(BankKeyGrant.deliver(bank, 2, 63490, true, false))
        assertNull(bank[1])
    }
}
