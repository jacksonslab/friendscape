package org.rsmod.content.other.bankkey

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankKeyModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerPostTickHook>(BankKeyScript::class.java)
    }
}

@Singleton
class BankKeyScript @Inject constructor(private val bankers: KeyBankers) : PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onPlayerLogin { grant(player) }
        onPlayerLogout { bankers.dismiss(player) }
        onOpHeld1(KEY) {
            if (inv[it.slot]?.id == KEY.asRSCM(RSCMType.OBJ)) bankers.summon(player)
        }
        onApNpc1(BANKER) {
            if (isWithinApRange(it.npc, distance = 2)) openBanker(it.npc)
        }
        onOpNpc1(BANKER) { openBanker(it.npc) }
        onIfOpen("interface.bankside") { bankers.dismiss(player) }
    }

    override fun onPostTick(player: Player) {
        grant(player)
        bankers.maintain(player)
    }

    private fun ProtectedAccess.openBanker(npc: org.rsmod.game.entity.Npc) {
        if (!bankers.owns(player, npc)) {
            mes("That banker was summoned by another player.")
            return
        }
        if (tryOpenBank()) bankers.dismiss(player)
    }

    private fun grant(player: Player) {
        if (player.vars[GRANTED] != 0) return
        val keyId = KEY.asRSCM(RSCMType.OBJ)
        val owned = player.invMap.values.any { inventory -> inventory.any { it?.id == keyId && it.count > 0 } }
        val bank = player.invMap.getOrPut("inv.bank")
        if (BankKeyGrant.deliver(bank, player.bankCapacity, keyId, false, owned)) {
            VarPlayerIntMapSetter.set(player, GRANTED, 1)
        }
    }

    companion object {
        const val KEY = "obj.friendscape_bank_key"
        const val BANKER = "npc.friendscape_key_banker"
        const val GRANTED = "varp.friendscape_bank_key_granted"
    }
}
