package org.rsmod.content.skills.thieving.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class ThievingPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(
            ThievingStallTable.stalls(),
            ThievingPickpocketTable.pickpockets(),
            ThievingChestTable.chests(),
        )
}
