package org.rsmod.content.skills.thieving.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Thievable chests, all opened properly through op2 (Search for traps or Pick-lock); Open
 * on a `trapped` chest springs the trap instead. Every `always` item is handed over
 * together with one weighted `loot` roll, all or nothing.
 */
object ThievingChestTable {
    const val LOC = 0
    const val EMPTY = 1
    const val LEVEL = 2
    const val XP = 3
    const val RESPAWN = 4
    const val TRAPPED = 5
    const val LOCKPICK = 6
    const val LOOT = 7
    const val ALWAYS = 8

    fun chests() =
        dbTable("dbtable.friendscape_thieving_chest", serverOnly = true) {
            column("loc", LOC, VarType.LOC)
            column("empty", EMPTY, VarType.LOC)
            column("level", LEVEL, VarType.INT)
            column("xp", XP, VarType.INT)
            column("respawn", RESPAWN, VarType.INT)
            column("trapped", TRAPPED, VarType.BOOLEAN)
            column("lockpick", LOCKPICK, VarType.BOOLEAN)
            column("loot", LOOT, VarType.OBJ, VarType.INT, VarType.INT, VarType.INT)
            column("always", ALWAYS, VarType.OBJ, VarType.INT, VarType.INT, VarType.INT)

            fun chest(
                loc: String,
                empty: String,
                level: Int,
                xp10: Int,
                respawn: Int,
                always: List<Drop> = emptyList(),
                loot: List<Drop> = emptyList(),
                trapped: Boolean = true,
                lockpick: Boolean = false,
            ) =
                row("dbrow.thieving_chest_$loc") {
                    columnRSCM(LOC, "loc.$loc")
                    columnRSCM(EMPTY, "loc.$empty")
                    column(LEVEL, level)
                    column(XP, xp10)
                    column(RESPAWN, respawn)
                    column(TRAPPED, trapped)
                    column(LOCKPICK, lockpick)
                    dropColumn(LOOT, loot)
                    dropColumn(ALWAYS, always)
                }

            chest("trapchest1", "emptytrapchest", 13, 78, 12, always = listOf(drop("coins", min = 10)))
            chest(
                "trapchest2", "emptytrapchest", 28, 250, 25,
                always = listOf(drop("naturerune"), drop("coins", min = 3)),
            )
            chest("trapchest3", "emptytrapchest", 43, 1250, 83, always = listOf(drop("coins", min = 50)))
            chest(
                "pickchest3", "emptypickchest", 47, 1500, 125,
                always = listOf(drop("steel_arrowheads", min = 5), drop("coins", min = 20)),
                lockpick = true,
            )
            chest(
                "trapchest4", "emptytrapchest", 59, 2500, 225,
                always = listOf(drop("bloodrune", min = 2), drop("coins", min = 500)),
            )
            chest(
                "trapchest5", "emptytrapchest", 72, 5000, 400,
                always =
                    listOf(
                        drop("coins", min = 1000),
                        drop("raw_shark"),
                        drop("adamantite_ore"),
                        drop("uncut_sapphire"),
                    ),
            )
            chest(
                "wilderness_rogue_chest", "wilderness_rogue_chest_open", 84, 7017, 34,
                loot =
                    listOf(
                        drop("naturerune", 11, 40),
                        drop("cert_red_spiders_eggs", 10, 6),
                        drop("lawrune", 10, 40),
                        drop("cert_coal", 8, 20),
                        drop("coins", 6, 4500),
                        drop("cert_vile_ashes", 3, 15),
                        drop("cert_uncut_diamond", 3, 3),
                        drop("cert_uncut_diamond", 2, 5),
                        drop("cert_uncut_emerald", 2, 10),
                        drop("blighted_sack_icebarrage", 2, 13),
                        drop("cert_iron_ore", 1, 40),
                        drop("chaosrune", 1, 60),
                        drop("deathrune", 1, 50),
                        drop("cert_blighted_mantaray", 1, 20),
                        drop("cert_blighted_anglerfish", 1, 15),
                        drop("cert_uncut_sapphire", 1, 15),
                        drop("2doseprayerrestore", 2),
                        drop("cert_dragonstone", 1, 2),
                    ),
            )
            chest(
                "dorgesh_average_chest_closed", "dorgesh_average_chest_closed_empty", 52, 2000, 150,
                loot =
                    listOf(
                        drop("coins", 3, 1, 250),
                        drop("oil_lantern_unlit"),
                        drop("bullseye_lantern_unlit"),
                        drop("cave_goblin_mining_helmet_unlit"),
                        drop("dorgesh_wire"),
                    ),
                trapped = false,
            )
            chest(
                "dorgesh_rich_chest_closed", "dorgesh_rich_chest_closed_empty", 78, 6500, 500,
                loot =
                    listOf(
                        "uncut_sapphire", "uncut_emerald", "uncut_ruby", "uncut_diamond", "uncut_opal",
                        "uncut_jade", "uncut_red_topaz", "bullseye_lantern_unlit", "cave_goblin_mining_helmet_unlit",
                        "dorgesh_frog_armour_bottoms", "dorgesh_frog_armour_top", "iron_bar", "dorgesh_wire",
                        "dorgesh_light_bulb", "dorgesh_lightbulb_nofilament",
                    ).map { drop(it) },
                trapped = false,
            )
        }
}
