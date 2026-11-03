package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook

class BountyKillHook @Inject constructor(private val rewards: BountyRewards) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        rewards.rollKill(context.hero, context.npc.type.id)
    }
}
