package org.rsmod.content.skills.sailing.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

object SailingPortsTable {
    const val DOCK = 0
    const val GANGPLANK = 1
    const val NOTICE_BOARD = 2
    const val LEDGER = 3
    const val GANGPLANK_COORD = 4

    fun ports() = dbTable("dbtable.friendscape_sailing_ports", serverOnly = true) {
        column("dock", DOCK, VarType.DBROW)
        column("gangplank", GANGPLANK, VarType.LOC)
        column("notice_board", NOTICE_BOARD, VarType.LOC)
        column("ledger", LEDGER, VarType.LOC)
        column("gangplank_coord", GANGPLANK_COORD, VarType.COORDGRID)

        fun port(slug: String, board: String?, ledger: String?, plankX: Int, plankZ: Int) {
            row("dbrow.friendscape_sailing_port_$slug") {
                columnRSCM(DOCK, "dbrow.sailing_dock_$slug")
                columnRSCM(GANGPLANK, "loc.sailing_gangplank_$slug")
                board?.let { columnRSCM(NOTICE_BOARD, "loc.port_task_board_$it") }
                ledger?.let { columnRSCM(LEDGER, "loc.dock_loading_bay_ledger_table_$it") }
                columnCoord(GANGPLANK_COORD, CoordGrid(plankX, plankZ, 0))
            }
        }

        port("port_sarim", "port_sarim", "port_sarim", 3051, 3193)
        port("the_pandemonium", "pandemonium", "pandemonium", 3070, 2987)
        port("lands_end", "lands_end", "lands_end", 1507, 3403)
        port("musa_point", "musa_point", "musa_point", 2961, 3146)
        port("hosidius", null, "hosidius", 1726, 3452)
        port("rimmington", null, null, 2906, 3225)
        port("catherby", "catherby", "catherby", 2796, 3412)
        port("port_piscarilius", "port_piscarilius", "port_piscarilius", 1845, 3687)
        port("brimhaven", "brimhaven", "brimhaven", 2758, 3230)
        port("ardougne", "ardougne", "ardougne", 2671, 3265)
        port("port_khazard", "port_khazard", "port_khazard", 2686, 3162)
        port("witchaven", null, null, 2747, 3305)
        port("entrana", null, "entrana", 2879, 3336)
        port("civitas_illa_fortis", "civitas_illa_fortis", "civitas_illa_fortis", 1775, 3142)
        port("corsair_cove", "corsair_cove", "corsair_cove", 2580, 2844)
        port("cairn_isle", null, "cairn_isle", 2750, 2952)
        port("sunset_coast", null, "sunset_coast", 1512, 2974)
        port("the_summer_shore", "the_summer_shore", "the_summer_shore", 3174, 2367)
        port("aldarin", "aldarin", "aldarin", 1452, 2970)
        port("ruins_of_unkah", "ruins_of_unkah", "ruins_of_unkah", 3144, 2825)
        port("void_knights_outpost", "void_knights_outpost", "void_knights_outpost", 2651, 2678)
        port("port_roberts", "port_roberts", "port_roberts", 1861, 3307)
        port("red_rock", "red_rock", "red_rock", 2809, 2509)
        port("rellekka", "rellekka", "rellekka", 2630, 3705)
        port("etceteria", "etceteria", "etceteria", 2613, 3840)
        port("port_tyras", "port_tyras", "port_tyras", 2144, 3120)
        port("deepfin_point", "deepfin_point", "deepfin_point", 1923, 2758)
        port("jatizso", null, "jatizso", 2412, 3780)
        port("neitiznot", null, "neitiznot", 2309, 3782)
        port("prifddinas", "prifddinas", "prifddinas", 2158, 3324)
        port("piscatoris", null, "piscatoris", 2304, 3689)
        port("lunar_isle", "lunar_isle", "lunar_isle", 2152, 3881)
    }
}
