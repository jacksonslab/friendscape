package org.rsmod.content.skills.sailing.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class SailingPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(SailingTables.templates(), SailingTables.fixtures(), SailingTables.docks())
}
