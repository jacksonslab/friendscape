package org.rsmod.content.skills.sailing

import org.rsmod.api.table.sailing.FriendscapeSailingPortsRow
import org.rsmod.api.table.sailing.SailingDockRow
import org.rsmod.map.CoordGrid

class Port(private val row: FriendscapeSailingPortsRow) {
    val dock: SailingDockRow
        get() = row.dock

    val name: String
        get() = dock.niceName

    val level: Int
        get() = dock.levelRequired

    val dockRowId: Int
        get() = dock.rowId

    val gangplank: Int
        get() = row.gangplank.id

    val noticeBoard: Int?
        get() = row.noticeBoard?.id

    val ledger: Int?
        get() = row.ledger?.id

    val portMaster: Int?
        get() = row.portMaster?.id

    val gangplankCoord: CoordGrid
        get() = row.gangplankCoord

    override fun toString(): String = "Port($name)"
}

object Ports {
    val ALL: List<Port> by lazy { FriendscapeSailingPortsRow.all().map(::Port) }

    private val byGangplank by lazy { ALL.associateBy { it.gangplank } }
    private val byBoard by lazy { ALL.filter { it.noticeBoard != null }.associateBy { it.noticeBoard!! } }
    private val byLedger by lazy { ALL.filter { it.ledger != null }.associateBy { it.ledger!! } }
    private val byDock by lazy { ALL.associateBy { it.dockRowId } }

    val PANDEMONIUM: Port by lazy { byDock.getValue(SailingDockRow.getRow("dbrow.sailing_dock_the_pandemonium").rowId) }

    fun byGangplank(loc: Int): Port? = byGangplank[loc]

    fun byNoticeBoard(loc: Int): Port? = byBoard[loc]

    fun byLedger(loc: Int): Port? = byLedger[loc]

    fun byDock(dockRowId: Int): Port? = byDock[dockRowId]
}
