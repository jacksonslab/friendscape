package org.rsmod.content.skills.sailing.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object SailingTables {
    fun templates() = dbTable("dbtable.friendscape_sailing_templates", serverOnly = true) {
        column("key", 0, VarType.STRING)
        column("world_entity_type", 1, VarType.INT)
        column("template_zone_x", 2, VarType.INT)
        column("template_zone_z", 3, VarType.INT)
        column("size_zones_x", 4, VarType.INT)
        column("size_zones_z", 5, VarType.INT)
        column("deck_level", 6, VarType.INT)
        column("board_dest_dx", 7, VarType.INT)
        column("board_dest_dz", 8, VarType.INT)
        column("dock_fine_dx", 9, VarType.INT)
        column("dock_fine_dz", 10, VarType.INT)
        column("base_speed", 11, VarType.INT)
        column("speed_cap", 12, VarType.INT)
        column("acceleration", 13, VarType.INT)
        column("helm_player_grab", 14, VarType.STRING)
        column("helm_synth_grab", 15, VarType.INT)
        column("helm_synth_release", 16, VarType.INT)
        column("boat", 17, VarType.DBROW)
        column("hull_width", 18, VarType.INT)
        column("hull_length", 19, VarType.INT)
        column("hook", 20, VarType.LOC)
        column("hook_dx", 21, VarType.INT)
        column("hook_dz", 22, VarType.INT)
        column("hook_replaces_cargo_hold", 23, VarType.BOOLEAN)

        fun template(
            row: String,
            key: String,
            worldEntityType: Int,
            zoneZ: Int,
            sizeZonesZ: Int,
            boardDz: Int,
            dockFineDx: Int,
            speedCap: Int,
            helmGrab: String,
            synthGrab: Int,
            synthRelease: Int,
            hullWidth: Int,
            hullLength: Int,
            hook: String,
            hookDx: Int,
            hookDz: Int,
            hookReplacesCargoHold: Boolean,
        ) {
            row(row) {
                column(0, key)
                column(1, worldEntityType)
                column(2, 480)
                column(3, zoneZ)
                column(4, 1)
                column(5, sizeZonesZ)
                column(6, 1)
                column(7, if (key == "raft") 3 else 4)
                column(8, boardDz)
                column(9, dockFineDx)
                column(10, 0)
                column(11, 192)
                column(12, speedCap)
                column(13, 64)
                column(14, helmGrab)
                column(15, synthGrab)
                column(16, synthRelease)
                columnRSCM(17, "dbrow.sailing_boat_$key")
                column(18, hullWidth)
                column(19, hullLength)
                columnRSCM(20, hook)
                column(21, hookDx)
                column(22, hookDz)
                column(23, hookReplacesCargoHold)
            }
        }

        template(
            "dbrow.friendscape_sailing_template_raft", "raft", 1, 807, 1, 4, 0, 320,
            "seq.human_sailing_alpha_helm_raft01_active01", 10792, 10793,
            hullWidth = 1, hullLength = 3, hook = "loc.salvaging_hook_raft_bronze",
            hookDx = 3, hookDz = 2, hookReplacesCargoHold = true,
        )
        template(
            "dbrow.friendscape_sailing_template_skiff", "skiff", 2, 806, 1, 4, 128, 384,
            "seq.human_sailing_alpha_helm_small01_active01", 10807, 10808,
            hullWidth = 2, hullLength = 5, hook = "loc.salvaging_hook_bronze",
            hookDx = 4, hookDz = 3, hookReplacesCargoHold = false,
        )
        template(
            "dbrow.friendscape_sailing_template_sloop", "sloop", 3, 804, 2, 10, 192, 448,
            "seq.human_sailing_helm_3x8_active01", 10807, 10808,
            hullWidth = 3, hullLength = 8, hook = "loc.salvaging_hook_large_bronze",
            hookDx = 4, hookDz = 7, hookReplacesCargoHold = false,
        )
    }

    fun fixtures() = dbTable("dbtable.friendscape_sailing_fixtures", serverOnly = true) {
        column("boat_key", 0, VarType.STRING)
        column("loc_symbol", 1, VarType.STRING)
        column("dx", 2, VarType.INT)
        column("dz", 3, VarType.INT)
        column("level", 4, VarType.INT)
        column("shape", 5, VarType.INT)
        row("dbrow.friendscape_sailing_raft_fixture_0") {
            column(0, "raft")
            column(1, "loc.sailing_boat_steering_kandarin_1x3_wood")
            column(2, 3)
            column(3, 4)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_raft_fixture_1") {
            column(0, "raft")
            column(1, "loc.sailing_boat_sail_kandarin_1x3_wood")
            column(2, 3)
            column(3, 3)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_raft_fixture_2") {
            column(0, "raft")
            column(1, "loc.sailing_boat_sail_kandarin_1x3_linen")
            column(2, 3)
            column(3, 5)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_raft_fixture_3") {
            column(0, "raft")
            column(1, "loc.sailing_boat_cargo_hold_regular_raft")
            column(2, 3)
            column(3, 2)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_raft_fixture_4") {
            column(0, "raft")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 3)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_raft_fixture_5") {
            column(0, "raft")
            column(1, "loc.randomsound_ardent_ocean_gulls")
            column(2, 4)
            column(3, 3)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_raft_fixture_6") {
            column(0, "raft")
            column(1, "loc.bgsound_sailing_ocean_water_loop_01")
            column(2, 2)
            column(3, 4)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_raft_fixture_7") {
            column(0, "raft")
            column(1, "loc.randomsound_ardent_ocean_crashing_waves")
            column(2, 4)
            column(3, 4)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_raft_fixture_8") {
            column(0, "raft")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 2)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_raft_fixture_9") {
            column(0, "raft")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 4)
            column(3, 2)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_raft_fixture_10") {
            column(0, "raft")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 5)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_raft_fixture_11") {
            column(0, "raft")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 4)
            column(3, 5)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_0") {
            column(0, "skiff")
            column(1, "loc.sailing_keel_kandarin_2x5_bronze")
            column(2, 2)
            column(3, 1)
            column(4, 0)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_1") {
            column(0, "skiff")
            column(1, "loc.sailing_boat_skiff_trim_wooden")
            column(2, 1)
            column(3, 1)
            column(4, 0)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_2") {
            column(0, "skiff")
            column(1, "loc.sailing_boat_steering_kandarin_2x5_wood")
            column(2, 4)
            column(3, 6)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_3") {
            column(0, "skiff")
            column(1, "loc.sailing_boat_sail_kandarin_2x5_wood")
            column(2, 4)
            column(3, 4)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_4") {
            column(0, "skiff")
            column(1, "loc.sailing_boat_sail_kandarin_2x5_linen")
            column(2, 4)
            column(3, 5)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_5") {
            column(0, "skiff")
            column(1, "loc.sailing_boat_cargo_hold_regular_2x5")
            column(2, 4)
            column(3, 1)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_6") {
            column(0, "skiff")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 3)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_7") {
            column(0, "skiff")
            column(1, "loc.randomsound_ardent_ocean_gulls")
            column(2, 5)
            column(3, 3)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_8") {
            column(0, "skiff")
            column(1, "loc.bgsound_sailing_ocean_water_loop_01")
            column(2, 2)
            column(3, 4)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_9") {
            column(0, "skiff")
            column(1, "loc.randomsound_ardent_ocean_crashing_waves")
            column(2, 5)
            column(3, 4)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_10") {
            column(0, "skiff")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 2)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_11") {
            column(0, "skiff")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 5)
            column(3, 2)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_12") {
            column(0, "skiff")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 5)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_skiff_fixture_13") {
            column(0, "skiff")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 5)
            column(3, 5)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_0") {
            column(0, "sloop")
            column(1, "loc.sailing_keel_kandarin_3x8_bronze")
            column(2, 1)
            column(3, 3)
            column(4, 0)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_1") {
            column(0, "sloop")
            column(1, "loc.sailing_boat_sloop_trim_wooden")
            column(2, 1)
            column(3, 2)
            column(4, 0)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_2") {
            column(0, "sloop")
            column(1, "loc.sailing_boat_steering_kandarin_3x8_wood")
            column(2, 3)
            column(3, 11)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_3") {
            column(0, "sloop")
            column(1, "loc.sailing_boat_sail_kandarin_3x8_wood")
            column(2, 4)
            column(3, 10)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_4") {
            column(0, "sloop")
            column(1, "loc.sailing_boat_sail_kandarin_3x8_linen")
            column(2, 4)
            column(3, 11)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_5") {
            column(0, "sloop")
            column(1, "loc.sailing_boat_cargo_hold_regular_large")
            column(2, 3)
            column(3, 4)
            column(4, 1)
            column(5, 10)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_6") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 5)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_7") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 4)
            column(3, 5)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_8") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 6)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_9") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 4)
            column(3, 6)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_10") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 7)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_11") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 4)
            column(3, 7)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_12") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 2)
            column(3, 8)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_13") {
            column(0, "sloop")
            column(1, "loc.invisible_type0_nonblocking")
            column(2, 4)
            column(3, 8)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_14") {
            column(0, "sloop")
            column(1, "loc.randomsound_ardent_ocean_gulls")
            column(2, 4)
            column(3, 10)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_15") {
            column(0, "sloop")
            column(1, "loc.bgsound_sailing_ocean_water_loop_01")
            column(2, 2)
            column(3, 9)
            column(4, 1)
            column(5, 22)
        }
        row("dbrow.friendscape_sailing_sloop_fixture_16") {
            column(0, "sloop")
            column(1, "loc.randomsound_ardent_ocean_crashing_waves")
            column(2, 4)
            column(3, 9)
            column(4, 1)
            column(5, 22)
        }
    }

}
