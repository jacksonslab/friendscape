package org.rsmod.content.travel.quetzal.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Links each of Jagex's `dbtable.quetzal` landing sites to the multinpc that shows Renu there. The
 * npc's multivarbit is the site's state: 0 hides Renu (and keeps a buildable pad unbuilt), higher
 * values pick Renu's colour.
 */
object QuetzalSitesTable {
    const val SITE = 0
    const val NPC = 1

    fun sites() =
        dbTable("dbtable.friendscape_quetzal_sites", serverOnly = true) {
            column("site", SITE, VarType.DBROW)
            column("npc", NPC, VarType.NPC)

            fun site(name: String) {
                row("dbrow.friendscape_quetzal_$name") {
                    columnRSCM(SITE, "dbrow.quetzal_$name")
                    columnRSCM(NPC, "npc.quetzal_$name")
                }
            }

            site("fortis")
            site("teomat")
            site("sunsetcoast")
            site("hunterguild")
            site("camtorum")
            site("colossalwyrm")
            site("outerfortis")
            site("colosseum")
            site("aldarin")
            site("quetzacalligorge")
            site("salvageroverlook")
            site("talteklan")
            site("auburnvalley")
            site("kastori")
        }
}
