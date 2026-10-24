package org.rsmod.content.skills.sailing

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

var Player.sailingShore by intVarp("varp.friendscape_sailing_shore")

/** Row id of the dock the player's boat was last moored at, plus one; zero before first boarding. */
var Player.sailingBoatDock by intVarp("varp.friendscape_sailing_boat_dock")

/** Bitmask of owned boats keyed by `sailing_boat:type_id`. The raft is always owned. */
var Player.sailingBoatsOwned by intVarp("varp.friendscape_sailing_boats_owned")

var Player.sailingActiveBoat by intVarp("varp.friendscape_sailing_active_boat")

var Player.sailingRaftFacility by intVarp("varp.friendscape_sailing_raft_facility")

var Player.aboardPlayerBoat by intVarBit("varbit.sailing_player_is_on_player_boat")

var Player.helmLockedIn by intVarBit("varbit.sailing_boat_facility_lockedin")

var Player.carryingCargo by intVarBit("varbit.sailing_carrying_cargo")

var Player.portTasksCompleted by intVarp("varp.port_tasks_completed")

fun Player.ownsBoat(type: BoatType): Boolean =
    type.typeId == RAFT_TYPE_ID || (sailingBoatsOwned and (1 shl type.typeId)) != 0

fun Player.grantBoat(type: BoatType) {
    sailingBoatsOwned = sailingBoatsOwned or (1 shl type.typeId)
}

const val RAFT_TYPE_ID = 0
const val RAFT_FACILITY_HOOK = 1
