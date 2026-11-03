package org.rsmod.content.skills.sailing

import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.plugin.module.PluginModule

class SailingModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(BountyKillHook::class.java)
    }
}
