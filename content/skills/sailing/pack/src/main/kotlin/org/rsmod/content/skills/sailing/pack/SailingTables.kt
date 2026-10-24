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
        row("dbrow.friendscape_sailing_template_raft") {
            column(0, "raft")
            column(1, 1)
            column(2, 480)
            column(3, 807)
            column(4, 1)
            column(5, 1)
            column(6, 1)
            column(7, 3)
            column(8, 4)
            column(9, 0)
            column(10, 0)
            column(11, 192)
            column(12, 320)
            column(13, 64)
            column(14, "seq.human_sailing_alpha_helm_raft01_active01")
            column(15, 10792)
            column(16, 10793)
        }
        row("dbrow.friendscape_sailing_template_skiff") {
            column(0, "skiff")
            column(1, 2)
            column(2, 480)
            column(3, 806)
            column(4, 1)
            column(5, 1)
            column(6, 1)
            column(7, 4)
            column(8, 4)
            column(9, 128)
            column(10, 0)
            column(11, 192)
            column(12, 384)
            column(13, 64)
            column(14, "seq.human_sailing_alpha_helm_small01_active01")
            column(15, 10807)
            column(16, 10808)
        }
        row("dbrow.friendscape_sailing_template_sloop") {
            column(0, "sloop")
            column(1, 3)
            column(2, 480)
            column(3, 804)
            column(4, 1)
            column(5, 2)
            column(6, 1)
            column(7, 4)
            column(8, 10)
            column(9, 192)
            column(10, 0)
            column(11, 192)
            column(12, 448)
            column(13, 64)
            column(14, "seq.human_sailing_helm_3x8_active01")
            column(15, 10807)
            column(16, 10808)
        }
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

    fun docks() = dbTable("dbtable.friendscape_sailing_docks", serverOnly = true) {
        column("name", 0, VarType.STRING)
        column("return_x", 1, VarType.INT)
        column("return_z", 2, VarType.INT)
        column("plank_x", 3, VarType.INT)
        column("plank_z", 4, VarType.INT)
        column("boat_x", 5, VarType.INT)
        column("boat_z", 6, VarType.INT)
        column("sailing_level", 7, VarType.INT)
        column("angle", 8, VarType.INT)
        row("dbrow.friendscape_sailing_dock_port_sarim") {
            column(0, "Port Sarim")
            column(1, 3050)
            column(2, 3193)
            column(3, 3051)
            column(4, 3194)
            column(5, 3053)
            column(6, 3193)
            column(7, 1)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_the_pandemonium") {
            column(0, "The Pandemonium")
            column(1, 3069)
            column(2, 2987)
            column(3, 3070)
            column(4, 2987)
            column(5, 3072)
            column(6, 2987)
            column(7, 1)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_land_s_end") {
            column(0, "Land's End")
            column(1, 1506)
            column(2, 3403)
            column(3, 1507)
            column(4, 3403)
            column(5, 1509)
            column(6, 3403)
            column(7, 5)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_hosidius") {
            column(0, "Hosidius")
            column(1, 1726)
            column(2, 3453)
            column(3, 1726)
            column(4, 3452)
            column(5, 1726)
            column(6, 3450)
            column(7, 5)
            column(8, 512)
        }
        row("dbrow.friendscape_sailing_dock_musa_point") {
            column(0, "Musa Point")
            column(1, 2960)
            column(2, 3146)
            column(3, 2961)
            column(4, 3146)
            column(5, 2963)
            column(6, 3146)
            column(7, 10)
            column(8, 1024)
        }
        row("dbrow.friendscape_sailing_dock_port_piscarilius") {
            column(0, "Port Piscarilius")
            column(1, 1845)
            column(2, 3688)
            column(3, 1845)
            column(4, 3687)
            column(5, 1845)
            column(6, 3684)
            column(7, 15)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_rimmington") {
            column(0, "Rimmington")
            column(1, 2924)
            column(2, 3175)
            column(3, 2925)
            column(4, 3175)
            column(5, 2927)
            column(6, 3175)
            column(7, 18)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_catherby") {
            column(0, "Catherby")
            column(1, 2793)
            column(2, 3408)
            column(3, 2794)
            column(4, 3408)
            column(5, 2796)
            column(6, 3408)
            column(7, 20)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_brimhaven") {
            column(0, "Brimhaven")
            column(1, 2751)
            column(2, 3231)
            column(3, 2752)
            column(4, 3231)
            column(5, 2754)
            column(6, 3231)
            column(7, 25)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_ardougne") {
            column(0, "Ardougne")
            column(1, 2667)
            column(2, 3259)
            column(3, 2668)
            column(4, 3259)
            column(5, 2670)
            column(6, 3259)
            column(7, 28)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_port_khazard") {
            column(0, "Port Khazard")
            column(1, 2685)
            column(2, 3162)
            column(3, 2686)
            column(4, 3162)
            column(5, 2688)
            column(6, 3162)
            column(7, 30)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_witchaven") {
            column(0, "Witchaven")
            column(1, 2726)
            column(2, 3286)
            column(3, 2727)
            column(4, 3286)
            column(5, 2729)
            column(6, 3286)
            column(7, 34)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_entrana") {
            column(0, "Entrana")
            column(1, 2880)
            column(2, 3336)
            column(3, 2881)
            column(4, 3336)
            column(5, 2883)
            column(6, 3336)
            column(7, 36)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_civitas_illa_fortis") {
            column(0, "Civitas illa Fortis")
            column(1, 1766)
            column(2, 3144)
            column(3, 1767)
            column(4, 3144)
            column(5, 1769)
            column(6, 3144)
            column(7, 38)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_corsair_cove") {
            column(0, "Corsair Cove")
            column(1, 2583)
            column(2, 2844)
            column(3, 2584)
            column(4, 2844)
            column(5, 2586)
            column(6, 2844)
            column(7, 40)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_cairn_isle") {
            column(0, "Cairn Isle")
            column(1, 2742)
            column(2, 2952)
            column(3, 2743)
            column(4, 2952)
            column(5, 2745)
            column(6, 2952)
            column(7, 42)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_sunset_coast") {
            column(0, "Sunset Coast")
            column(1, 1513)
            column(2, 2974)
            column(3, 1512)
            column(4, 2974)
            column(5, 1510)
            column(6, 2974)
            column(7, 44)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_the_summer_shore") {
            column(0, "The Summer Shore")
            column(1, 3174)
            column(2, 2368)
            column(3, 3172)
            column(4, 2367)
            column(5, 3174)
            column(6, 2364)
            column(7, 45)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_aldarin") {
            column(0, "Aldarin")
            column(1, 1452)
            column(2, 2969)
            column(3, 1452)
            column(4, 2970)
            column(5, 1452)
            column(6, 2973)
            column(7, 46)
            column(8, 1024)
        }
        row("dbrow.friendscape_sailing_dock_ruins_of_unkah") {
            column(0, "Ruins of Unkah")
            column(1, 3145)
            column(2, 2825)
            column(3, 3141)
            column(4, 2824)
            column(5, 3143)
            column(6, 2824)
            column(7, 48)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_void_knights_outpost") {
            column(0, "Void Knights' Outpost")
            column(1, 2648)
            column(2, 2683)
            column(3, 2649)
            column(4, 2683)
            column(5, 2651)
            column(6, 2683)
            column(7, 50)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_port_roberts") {
            column(0, "Port Roberts")
            column(1, 1855)
            column(2, 3307)
            column(3, 1856)
            column(4, 3307)
            column(5, 1858)
            column(6, 3307)
            column(7, 50)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_red_rock") {
            column(0, "Red Rock")
            column(1, 2811)
            column(2, 2510)
            column(3, 2812)
            column(4, 2510)
            column(5, 2814)
            column(6, 2510)
            column(7, 50)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_rellekka") {
            column(0, "Rellekka")
            column(1, 2627)
            column(2, 3709)
            column(3, 2628)
            column(4, 3709)
            column(5, 2630)
            column(6, 3709)
            column(7, 62)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_etceteria") {
            column(0, "Etceteria")
            column(1, 2609)
            column(2, 3836)
            column(3, 2610)
            column(4, 3836)
            column(5, 2612)
            column(6, 3836)
            column(7, 65)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_port_tyras") {
            column(0, "Port Tyras")
            column(1, 2138)
            column(2, 3115)
            column(3, 2139)
            column(4, 3115)
            column(5, 2141)
            column(6, 3115)
            column(7, 66)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_deepfin_point") {
            column(0, "Deepfin Point")
            column(1, 1920)
            column(2, 2752)
            column(3, 1921)
            column(4, 2752)
            column(5, 1923)
            column(6, 2752)
            column(7, 67)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_jatizso") {
            column(0, "Jatizso")
            column(1, 2409)
            column(2, 3776)
            column(3, 2410)
            column(4, 3776)
            column(5, 2412)
            column(6, 3776)
            column(7, 68)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_neitiznot") {
            column(0, "Neitiznot")
            column(1, 2299)
            column(2, 3782)
            column(3, 2300)
            column(4, 3782)
            column(5, 2302)
            column(6, 3782)
            column(7, 68)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_prifddinas") {
            column(0, "Prifddinas")
            column(1, 2155)
            column(2, 3319)
            column(3, 2156)
            column(4, 3319)
            column(5, 2158)
            column(6, 3319)
            column(7, 70)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_piscatoris") {
            column(0, "Piscatoris")
            column(1, 2297)
            column(2, 3689)
            column(3, 2298)
            column(4, 3689)
            column(5, 2300)
            column(6, 3689)
            column(7, 75)
            column(8, 0)
        }
        row("dbrow.friendscape_sailing_dock_lunar_isle") {
            column(0, "Lunar Isle")
            column(1, 2154)
            column(2, 3881)
            column(3, 2155)
            column(4, 3881)
            column(5, 2157)
            column(6, 3881)
            column(7, 76)
            column(8, 0)
        }
    }
}
