package org.rsmod.content.travel.quetzal.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class QuetzalPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(QuetzalSitesTable.sites())
}
