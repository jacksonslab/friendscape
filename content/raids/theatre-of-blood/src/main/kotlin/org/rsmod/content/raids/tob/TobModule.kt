package org.rsmod.content.raids.tob

import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathOverrideHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class TobModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathOverrideHook>(TobDeathHook::class.java)
        addSetBinding<NpcAttackValidateHook>(TobAttackHook::class.java)
    }
}

internal class TobDeathHook @Inject constructor(private val raids: TobRaids) : PlayerDeathOverrideHook {
    override suspend fun ProtectedAccess.override(): Boolean = with(raids) { raidDeath() }
}

internal class TobAttackHook @Inject constructor(private val raids: TobRaids) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val reason = raids.deniesAttack(player, npc) ?: return NpcAttackValidateResult.Pass
        return NpcAttackValidateResult.Deny(reason)
    }
}
