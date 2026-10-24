package org.rsmod.content.skills.construction

import org.rsmod.api.table.construction.ConstructionHouseLocationRow
import org.rsmod.map.CoordGrid

/** A town a house can stand in, out of `dbtable.construction_house_location`. */
class HouseLocation(
    val id: Int,
    val name: String,
    val portalLoc: Int,
    val exit: CoordGrid,
    val level: Int,
    val cost: Int,
) {
    companion object {
        val ALL: List<HouseLocation> by lazy {
            ConstructionHouseLocationRow.all()
                .map { HouseLocation(it.location, it.name, it.portal.id, it.exit, it.level, it.cost) }
                .sortedBy { it.level }
        }

        fun byId(id: Int): HouseLocation? = ALL.firstOrNull { it.id == id }

        fun byPortal(locId: Int): HouseLocation? = ALL.firstOrNull { it.portalLoc == locId }

        const val RIMMINGTON: Int = 1
    }
}
