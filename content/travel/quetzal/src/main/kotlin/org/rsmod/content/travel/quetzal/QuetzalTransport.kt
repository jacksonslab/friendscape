package org.rsmod.content.travel.quetzal

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.table.FriendscapeQuetzalSitesRow
import org.rsmod.api.table.QuetzalRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Varlamore quetzal network. Renu stands on every built landing pad; the map interface is drawn
 * entirely by Jagex's `quetzal_menu` clientscripts from `dbtable.quetzal`, which also lock any site
 * whose bit is clear in `varp.quetzals_unlocked`. Building a pad sets that bit and the site varbit,
 * which flips both the pad loc and Renu's multinpc.
 */
class QuetzalTransport : PluginScript() {
    private lateinit var sites: List<Site>

    override fun ScriptContext.startup() {
        sites = loadSites()
        for (renu in RENU_VARIANTS) {
            onOpNpc1(renu) { openMap() }
            onOpNpc3(renu) { flyToLastDestination() }
            onOpNpc4(renu) { pet(it.npc) }
        }
        onIfModalButton("component.quetzal_menu:icons") { fly(it.comsub) }
        onOpLoc1("loc.quetzal_landing_site_unbuilt") { build(it.loc) }
        onPlayerLogin { syncSites(player) }
    }

    private fun loadSites(): List<Site> {
        val byRow = FriendscapeQuetzalSitesRow.all().associateBy { it.site.rowId }
        return QuetzalRow.all().map { row ->
            val npc = checkNotNull(byRow[row.rowId]) { "No Renu npc for quetzal site ${row.name}." }.npc
            Site(row, npc.multiVarBit, npc.firstTravelVariant())
        }
    }

    private fun ProtectedAccess.openMap() {
        ifOpenMainModal("interface.quetzal_menu")
        ifSetEvents("component.quetzal_menu:icons", sites.indices, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.fly(index: Int) {
        val site = sites.getOrNull(index) ?: return
        if (!player.hasLandingSite(site)) {
            mes("You need to build a landing site there before Renu can fly you to it.")
            return
        }
        if (coords.isWithinDistance(site.row.coord, CURRENT_SITE_RADIUS)) {
            mes("You're already at ${site.row.name}.")
            return
        }
        ifClose()
        flyTo(site)
    }

    private suspend fun ProtectedAccess.flyToLastDestination() {
        val last = vars[LAST_DESTINATION]
        val site = sites.firstOrNull { it.row.id == last }
        if (site == null) {
            mes("Renu hasn't flown you anywhere yet.")
            return
        }
        if (coords.isWithinDistance(site.row.coord, CURRENT_SITE_RADIUS)) {
            mes("You're already at ${site.row.name}.")
            return
        }
        flyTo(site)
    }

    private suspend fun ProtectedAccess.flyTo(site: Site) {
        vars[LAST_DESTINATION] = site.row.id
        fadeOverlay(0, FADE_CLEAR, 0, FADE_BLACK, FADE_CLIENT_CYCLES)
        delay(2)
        telejump(site.row.coord)
        delay(1)
        fadeOverlay(0, FADE_BLACK, 0, FADE_CLEAR, FADE_CLIENT_CYCLES)
        closeFadeOverlay(cycles = 2)
        mes("Renu flies you to ${site.row.name}.")
    }

    private suspend fun ProtectedAccess.pet(renu: Npc) {
        faceEntitySquare(renu)
        anim("seq.human_quetzal_headpat")
        renu.anim("seq.npc_quetzal_headpat")
        delay(2)
        mes("You give Renu a pat. Renu seems happy.")
    }

    private suspend fun ProtectedAccess.build(pad: BoundLocInfo) {
        val varbit = ServerCacheManager.getObject(pad.id)?.multiVarBit ?: return
        val site = sites.firstOrNull { it.varbit == varbit } ?: return
        if (player.hasLandingSite(site)) {
            return
        }
        if (inv.count(LIMESTONE_BRICK) < BRICKS_NEEDED ||
            inv.count(SOFT_CLAY) < CLAY_NEEDED ||
            inv.count(QUETZAL_FEED) < FEED_NEEDED
        ) {
            mes(
                "You need $BRICKS_NEEDED limestone bricks, $CLAY_NEEDED soft clay and " +
                    "$FEED_NEEDED quetzal feed to build a landing site."
            )
            return
        }
        val taken =
            invDel(
                inv,
                objType(LIMESTONE_BRICK),
                BRICKS_NEEDED,
                objType(SOFT_CLAY),
                CLAY_NEEDED,
                objType(QUETZAL_FEED),
                FEED_NEEDED,
            )
        if (taken.failure) {
            return
        }
        anim(BUILD_ANIM)
        delay(2)
        unlock(player, site)
        mes("You build a landing site at ${site.row.name}. Renu can now fly you here.")
    }

    private fun objType(internal: String) =
        checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
            "Missing $internal"
        }

    private fun Player.hasLandingSite(site: Site): Boolean =
        site.row.autoUnlocked || vars[UNLOCKED_SITES] and (1 shl site.row.id) != 0

    private fun unlock(player: Player, site: Site) {
        VarPlayerIntMapSetter.set(player, UNLOCKED_SITES, player.vars[UNLOCKED_SITES] or (1 shl site.row.id))
        syncSites(player)
    }

    private fun syncSites(player: Player) {
        val colour = player.vars[RENU_COLOUR].coerceIn(0, RENU_COLOURS - 1)
        for (site in sites) {
            val value = if (player.hasLandingSite(site)) site.travelVariant + colour else 0
            if (player.vars[site.varbitSymbol] != value) {
                VarPlayerIntMapSetter.set(player, site.varbitSymbol, value)
            }
        }
    }

    private fun NpcServerType.firstTravelVariant(): Int {
        val index =
            multiNpc.indexOfFirst { id ->
                ServerCacheManager.getNpc(id)?.actions?.getOpOrNull(0) == TRAVEL
            }
        check(index > 0) { "Quetzal multinpc ${symbol()} has no Travel variant." }
        return index
    }

    private fun NpcServerType.symbol(): String = RSCM.getReverseMapping(RSCMType.NPC, id)

    private data class Site(
        val row: QuetzalRow,
        val varbit: Int,
        val travelVariant: Int,
    ) {
        val varbitSymbol: String = RSCM.getReverseMapping(RSCMType.VARBIT, varbit)
    }

    private fun CoordGrid.isWithinDistance(other: CoordGrid, radius: Int): Boolean =
        level == other.level && chebyshevDistance(other) <= radius

    private fun CoordGrid.chebyshevDistance(other: CoordGrid): Int =
        maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    private companion object {
        val RENU_VARIANTS =
            listOf(
                "npc.quetzal_child_green",
                "npc.quetzal_child_orange",
                "npc.quetzal_child_blue",
                "npc.quetzal_child_cyan",
                "npc.quetzal_child_green_orange",
            )
        const val TRAVEL = "Travel"
        const val UNLOCKED_SITES = "varp.quetzals_unlocked"
        const val LAST_DESTINATION = "varbit.quetzal_last_destination"
        const val RENU_COLOUR = "varbit.quetzal_base_appearance"
        const val RENU_COLOURS = 5
        const val CURRENT_SITE_RADIUS = 10
        const val LIMESTONE_BRICK = "obj.limestonebrick"
        const val SOFT_CLAY = "obj.softclay"
        const val BRICKS_NEEDED = 3
        const val CLAY_NEEDED = 4
        const val QUETZAL_FEED = "obj.vmq2_quetzal_feed"
        const val FEED_NEEDED = 10
        const val BUILD_ANIM = "seq.human_poh_build"
        const val FADE_CLEAR = 255
        const val FADE_BLACK = 0
        const val FADE_CLIENT_CYCLES = 50
    }
}
