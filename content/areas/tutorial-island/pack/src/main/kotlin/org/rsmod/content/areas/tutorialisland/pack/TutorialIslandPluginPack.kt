package org.rsmod.content.areas.tutorialisland.pack

import dev.openrune.pack.PluginPack

class TutorialIslandPluginPack : PluginPack() {
    override fun extraTasks() = listOf(AccountGuideSpawnTask(checkNotNull(resourceRoot()).resolve("maps/account-guide.properties")))
}
