package org.rsmod.content.skills.sailing

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

@Singleton
class SailingControlPanel @Inject constructor(
    private val boats: BoatManager,
    private val players: PlayerList,
    private val events: EventBus,
) {
    private val aboard = HashMap<Player, Boat>()

    fun tick() {
        val present = HashSet<Player>()
        for (player in players) {
            val boat = if (player.aboardPlayerBoat != 0) boats.boatOf(player) else null
            if (boat == null) continue
            present += player
            sync(player, boat)
            if (aboard.put(player, boat) != boat) {
                open(player)
                ClientScripts.toplevelSidebuttonSwitch(player, 0)
            }
        }
        for (player in aboard.keys.toList()) {
            if (player !in present) {
                aboard.remove(player)
                reset(player)
                combat(player)
            }
        }
    }

    fun open(player: Player) {
        val boat = boats.boatOf(player) ?: return
        if (player.aboardPlayerBoat == 0) return
        sync(player, boat)
        set(player, "sailing_sidepanel_tabs", 0, force = true)
        player.ifOpenOverlay(PANEL, TARGET, events)
        player.ifSetEvents(BUTTONS, 0..2, IfEvent.Op1)
        player.ifSetEvents("component.sailing_sidepanel:switch_button", 0..11, IfEvent.Op1)
    }

    fun combat(player: Player) {
        player.ifOpenOverlay("interface.combat_interface", TARGET, events)
        player.ifSetEvents("component.combat_interface:switch_button", -1..11, IfEvent.Op1)
    }

    fun reset(player: Player) {
        set(player, "sailing_sidepanel_visible_from_combat_tab", 0)
        set(player, "sailing_sidepanel_helm_status", 0)
        set(player, "sailing_sidepanel_boat_move_mode", SailingMoveModes.STOPPED)
    }

    fun forget(player: Player) {
        aboard.remove(player)
    }

    fun press(access: ProtectedAccess, slot: Int, op: IfButtonOp) = with(access) {
        if (op != IfButtonOp.Op1 || slot !in 0..2 || player.aboardPlayerBoat == 0) return@with
        val boat = boats.boatOf(player) ?: return@with
        if (boat.helmsman != player || player.helmLockedIn != 3 || !boat.isOwnedBy(player)) {
            mes("Take the helm of your boat before using the sailing controls.")
            return@with
        }
        val mode = when (slot) {
            0 -> SailingSpeedControls.main(boat.moveMode)
            1 -> SailingSpeedControls.lower(boat.moveMode)
            else -> SailingSpeedControls.raise(boat.moveMode)
        }
        if (mode == SailingMoveModes.REVERSE && boat.speed != 0) {
            mes("Wait for the boat to stop before reversing.")
            return@with
        }
        SailingSpeedControls.set(boat, mode)
        sync(player, boat)
    }

    private fun sync(player: Player, boat: Boat) {
        set(player, "sailing_boarded_boat_type", boat.type.typeId)
        set(player, "sailing_sidepanel_boat_type", boat.type.boat.rowId)
        set(player, "sailing_sidepanel_shipyard_mode", 0)
        set(player, "sailing_sidepanel_visible_from_combat_tab", 1)
        set(player, "sailing_sidepanel_player_role", if (boat.isOwnedBy(player)) 0 else 6)
        set(player, "sailing_sidepanel_facility_sail", 0)
        set(player, "sailing_sidepanel_facility_helm", 0)
        set(player, "sailing_sidepanel_facility_hull", 0)
        for (hotspot in 0..12) set(player, "sailing_sidepanel_facility_hotspot$hotspot", 0)
        val hull = boat.type.boat.hullOption.first().facilityStats
        val hp = hull.boatHpMax ?: 0
        set(player, "sailing_sidepanel_boat_hp_max", hp)
        set(player, "sailing_sidepanel_boat_hp", hp)
        set(player, "sailing_sidepanel_boat_armour", hull.boatArmour ?: 0)
        set(player, "sailing_sidepanel_boat_move_mode", boat.moveMode)
        set(player, "sailing_sidepanel_helm_status", when (boat.helmsman) {
            player -> 2
            null -> 1
            else -> 3
        })
        set(player, "sailing_sidepanel_boat_basespeed", boat.type.baseSpeed)
        set(player, "sailing_sidepanel_boat_speedcap", boat.type.speedCap)
        set(player, "sailing_sidepanel_boat_acceleration", boat.type.acceleration)
    }

    private fun set(player: Player, name: String, value: Int, force: Boolean = false) {
        val variable = variables.getOrPut(name) {
            when {
                RSCM.getRSCMOrNull("varbit.$name", RSCMType.VARBIT) != null -> "varbit.$name"
                RSCM.getRSCMOrNull("varp.$name", RSCMType.VARP) != null -> "varp.$name"
                else -> error("Missing sailing panel variable: $name")
            }
        }
        if (force || player.vars[variable] != value) VarPlayerIntMapSetter.set(player, variable, value)
    }

    companion object {
        const val PANEL = "interface.sailing_sidepanel"
        const val BUTTONS = "component.sailing_sidepanel:facilities_content_clicklayer"
        private const val TARGET = "component.toplevel_osrs_stretch:side0"
        private val variables = HashMap<String, String>()
    }
}
