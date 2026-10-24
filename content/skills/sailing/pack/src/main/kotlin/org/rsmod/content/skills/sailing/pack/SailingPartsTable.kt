package org.rsmod.content.skills.sailing.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object SailingPartsTable {
    fun parts() = dbTable("dbtable.friendscape_sailing_parts", serverOnly = true) {
        column("output", 0, VarType.OBJ)
        column("input", 1, VarType.OBJ)
        column("count", 2, VarType.INT)
        column("level", 3, VarType.INT)
        column("xp_tenths", 4, VarType.INT)
        column("skill", 5, VarType.STAT)
        column("anvil", 6, VarType.BOOLEAN)

        fun hull(tier: String, plank: String, level: Int, xpTenths: Int) {
            row("dbrow.friendscape_sailing_hull_$tier") {
                columnRSCM(0, "obj.sailing_boat_hull_part_$tier")
                columnRSCM(1, "obj.$plank")
                column(2, 5)
                column(3, level)
                column(4, xpTenths)
                columnRSCM(5, "stat.construction")
                column(6, false)
            }
            row("dbrow.friendscape_sailing_large_hull_$tier") {
                columnRSCM(0, "obj.sailing_boat_large_hull_part_$tier")
                columnRSCM(1, "obj.sailing_boat_hull_part_$tier")
                column(2, 5)
                column(3, level)
                column(4, 25)
                columnRSCM(5, "stat.construction")
                column(6, false)
            }
        }

        // OSRS Wiki: Construction training / Hull parts; individual item Creation tables.
        hull("wooden", "woodplank", 1, 725)
        hull("oak", "plank_oak", 8, 1500)
        hull("teak", "plank_teak", 23, 2250)
        hull("mahogany", "plank_mahogany", 41, 3500)
        hull("camphor", "plank_camphor", 53, 4000)
        hull("ironwood", "plank_ironwood", 77, 4375)
        hull("rosewood", "plank_rosewood", 84, 4750)

        fun keel(tier: String, level: Int) {
            row("dbrow.friendscape_sailing_large_keel_$tier") {
                columnRSCM(0, "obj.sailing_boat_large_keel_part_$tier")
                columnRSCM(1, "obj.sailing_boat_keel_part_$tier")
                column(2, 5)
                column(3, level)
                column(4, 50)
                columnRSCM(5, "stat.smithing")
                column(6, true)
            }
        }
        keel("bronze", 10)
        keel("iron", 22)
        keel("steel", 38)
        keel("mithril", 56)
        keel("adamant", 74)
        keel("rune", 86)
    }
}
