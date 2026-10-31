package dev.openrune.tables.skills.prayer

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object PrayerAltarsTable {
    fun altars() = dbTable("dbtable.friendscape_prayer_altars", serverOnly = true) {
        column("altars", 0, VarType.LOC)
        column("xp_multiplier", 1, VarType.INT)
        column("burner_bonus", 2, VarType.INT)

        fun altar(tier: Int, multiplier: Int) {
            row("dbrow.friendscape_prayer_altar_$tier") {
                columnRSCM(0,
                    "loc.poh_altar_saradomin_$tier", "loc.poh_altar_zamorak_$tier",
                    "loc.poh_altar_guthix_$tier", "loc.poh_altar_gnomechild_$tier",
                )
                column(1, multiplier)
                column(2, 500)
            }
        }
        altar(1, 1000)
        altar(2, 1100)
        altar(3, 1250)
        altar(4, 1500)
        altar(5, 1750)
        altar(6, 2000)
        altar(7, 2500)
    }
}
