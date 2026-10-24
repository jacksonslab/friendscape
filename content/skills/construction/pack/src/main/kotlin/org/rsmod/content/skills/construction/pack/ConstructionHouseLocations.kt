package org.rsmod.content.skills.construction.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

/**
 * The towns a house can stand in. `location` is the `varbit.poh_house_location` value, numbered the
 * way the client's own town-name enum (`enum_252`) numbers them. Levels and relocation fees are the
 * estate agent's, from the wiki; `exit` is the tile in front of that town's portal.
 */
object ConstructionHouseLocations {
    const val LOCATION = 0
    const val NAME = 1
    const val PORTAL = 2
    const val EXIT = 3
    const val LEVEL = 4
    const val COST = 5

    private class Town(
        val key: String,
        val location: Int,
        val name: String,
        val portal: String,
        val exit: CoordGrid,
        val level: Int,
        val cost: Int,
    )

    private val TOWNS =
        listOf(
            Town("rimmington", 1, "Rimmington", "loc.poh_rimmington_portal", CoordGrid(2954, 3224, 0), 1, 5000),
            Town("taverley", 2, "Taverley", "loc.poh_taverly_portal", CoordGrid(2894, 3465, 0), 10, 5000),
            Town("pollnivneach", 3, "Pollnivneach", "loc.poh_pollnivneach_portal", CoordGrid(3340, 3003, 0), 20, 7500),
            Town("rellekka", 4, "Rellekka", "loc.poh_rellekka_portal", CoordGrid(2670, 3631, 0), 30, 10000),
            Town("brimhaven", 5, "Brimhaven", "loc.poh_brimhaven_portal", CoordGrid(2758, 3178, 0), 40, 15000),
            Town("yanille", 6, "Yanille", "loc.poh_yanille_portal", CoordGrid(2544, 3095, 0), 50, 25000),
            Town("hosidius", 8, "Hosidius", "loc.poh_kourend_portal", CoordGrid(1743, 3517, 0), 25, 8750),
            Town("prifddinas", 9, "Prifddinas", "loc.poh_prifddinas_portal", CoordGrid(3239, 6075, 0), 70, 50000),
            Town("aldarin", 13, "Aldarin", "loc.poh_aldarin_portal", CoordGrid(1422, 2964, 0), 35, 12500),
        )

    fun table() =
        dbTable("dbtable.construction_house_location", serverOnly = true) {
            column("location", LOCATION, VarType.INT)
            column("name", NAME, VarType.STRING)
            column("portal", PORTAL, VarType.LOC)
            column("exit", EXIT, VarType.COORDGRID)
            column("level", LEVEL, VarType.INT)
            column("cost", COST, VarType.INT)

            for (town in TOWNS) {
                row("dbrow.construction_house_location_${town.key}") {
                    column(LOCATION, town.location)
                    column(NAME, town.name)
                    columnRSCM(PORTAL, town.portal)
                    columnCoord(EXIT, town.exit)
                    column(LEVEL, town.level)
                    column(COST, town.cost)
                }
            }
        }
}
