package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object HunterTrapTables {
    const val TRAP_ITEM = 0
    const val TRAP_LEVEL = 1
    const val TRAP_LAID = 2
    const val TRAP_FAILING = 3
    const val TRAP_FAILED = 4
    const val TRAP_LAY_ANIM = 5
    const val TRAP_WILDERNESS_BONUS = 6

    const val CREATURE_NPC = 0
    const val CREATURE_TRAP = 1
    const val CREATURE_LEVEL = 2
    const val CREATURE_XP = 3
    const val CREATURE_LOW = 4
    const val CREATURE_HIGH = 5
    const val CREATURE_CATCHING_N = 6
    const val CREATURE_CATCHING_E = 7
    const val CREATURE_CATCHING_S = 8
    const val CREATURE_CATCHING_W = 9
    const val CREATURE_FULL = 10
    const val CREATURE_RESPAWN = 11

    const val LOOT_CREATURE = 0
    const val LOOT_OBJ = 1
    const val LOOT_MIN = 2
    const val LOOT_MAX = 3

    fun traps() =
        dbTable("dbtable.hunter_trap", serverOnly = true) {
            column("item", TRAP_ITEM, VarType.OBJ)
            column("level", TRAP_LEVEL, VarType.INT)
            column("laid", TRAP_LAID, VarType.LOC)
            column("failing", TRAP_FAILING, VarType.LOC)
            column("failed", TRAP_FAILED, VarType.LOC)
            column("lay_anim", TRAP_LAY_ANIM, VarType.SEQ)
            column("wilderness_bonus", TRAP_WILDERNESS_BONUS, VarType.BOOLEAN)

            row("dbrow.hunter_trap_bird_snare") {
                columnRSCM(TRAP_ITEM, "obj.hunting_ojibway_bird_snare")
                column(TRAP_LEVEL, 1)
                columnRSCM(TRAP_LAID, "loc.hunting_ojibway_trap")
                columnRSCM(TRAP_FAILING, "loc.hunting_ojibway_trap_failing")
                columnRSCM(TRAP_FAILED, "loc.hunting_ojibway_trap_broken")
                columnRSCM(TRAP_LAY_ANIM, "seq.hunting_setting_trap_small")
                column(TRAP_WILDERNESS_BONUS, false)
            }
            row("dbrow.hunter_trap_box_trap") {
                columnRSCM(TRAP_ITEM, "obj.hunting_box_trap")
                column(TRAP_LEVEL, 27)
                columnRSCM(TRAP_LAID, "loc.hunting_boxtrap_empty")
                columnRSCM(TRAP_FAILING, "loc.hunting_boxtrap_failing")
                columnRSCM(TRAP_FAILED, "loc.hunting_boxtrap_failed")
                columnRSCM(TRAP_LAY_ANIM, "seq.human_createfire")
                column(TRAP_WILDERNESS_BONUS, true)
            }
        }

    fun creatures() =
        dbTable("dbtable.hunter_trap_creature", serverOnly = true) {
            column("npc", CREATURE_NPC, VarType.NPC)
            column("trap", CREATURE_TRAP, VarType.DBROW)
            column("level", CREATURE_LEVEL, VarType.INT)
            column("xp", CREATURE_XP, VarType.INT)
            column("low", CREATURE_LOW, VarType.INT)
            column("high", CREATURE_HIGH, VarType.INT)
            column("catching_n", CREATURE_CATCHING_N, VarType.LOC)
            column("catching_e", CREATURE_CATCHING_E, VarType.LOC)
            column("catching_s", CREATURE_CATCHING_S, VarType.LOC)
            column("catching_w", CREATURE_CATCHING_W, VarType.LOC)
            column("full", CREATURE_FULL, VarType.LOC)
            column("respawn", CREATURE_RESPAWN, VarType.INT)

            fun creature(
                row: String,
                npc: String,
                trap: String,
                level: Int,
                xp10: Int,
                low: Int,
                high: Int,
                catching: List<String>,
                full: String,
                respawn: Int,
            ) =
                row(row) {
                    columnRSCM(CREATURE_NPC, npc)
                    columnRSCM(CREATURE_TRAP, trap)
                    column(CREATURE_LEVEL, level)
                    column(CREATURE_XP, xp10)
                    column(CREATURE_LOW, low)
                    column(CREATURE_HIGH, high)
                    columnRSCM(CREATURE_CATCHING_N, catching[0])
                    columnRSCM(CREATURE_CATCHING_E, catching[1])
                    columnRSCM(CREATURE_CATCHING_S, catching[2])
                    columnRSCM(CREATURE_CATCHING_W, catching[3])
                    columnRSCM(CREATURE_FULL, full)
                    column(CREATURE_RESPAWN, respawn)
                }

            fun snare(kind: String) = List(4) { "loc.hunting_ojibway_trap_trapping_$kind" }

            fun box(kind: String) = listOf("n", "e", "s", "w").map { "loc.hunting_boxtrap_trapping_${kind}_$it" }

            val snare = "dbrow.hunter_trap_bird_snare"
            val box = "dbrow.hunter_trap_box_trap"

            creature("dbrow.hunter_crimson_swift", "npc.hunting_bird_jungle", snare, 1, 340, 100, 419, snare("jungle"), "loc.hunting_ojibway_trap_full_jungle", 10)
            creature("dbrow.hunter_golden_warbler", "npc.hunting_bird_desert", snare, 5, 470, 93, 401, snare("desert"), "loc.hunting_ojibway_trap_full_desert", 10)
            creature("dbrow.hunter_copper_longtail", "npc.hunting_bird_woodland", snare, 9, 612, 86, 388, snare("woodland"), "loc.hunting_ojibway_trap_full_woodland", 10)
            creature("dbrow.hunter_cerulean_twitch", "npc.hunting_bird_polar", snare, 11, 646, 82, 382, snare("polar"), "loc.hunting_ojibway_trap_full_polar", 10)
            creature("dbrow.hunter_tropical_wagtail", "npc.multicoloured_bird", snare, 19, 952, 75, 372, snare("coloured"), "loc.hunting_ojibway_trap_full_coloured", 10)
            creature("dbrow.hunter_ferret", "npc.hunting_ferret", box, 27, 1152, 70, 310, box("ferret"), "loc.hunting_boxtrap_full_ferret", 10)
            creature("dbrow.hunter_chinchompa", "npc.hunting_chinchompa", box, 53, 1984, 6, 268, box("chinchompa"), "loc.hunting_boxtrap_full_chinchompa", 7)
            creature("dbrow.hunter_carnivorous_chinchompa", "npc.hunting_chinchompa_big", box, 63, 2650, -76, 228, box("chinchompa_big"), "loc.hunting_boxtrap_full_chinchompa_big", 7)
            creature("dbrow.hunter_black_chinchompa", "npc.hunting_chinchompa_black", box, 73, 3150, -76, 228, box("chinchompa_black"), "loc.hunting_boxtrap_full_chinchompa_black", 7)
        }

    fun loot() =
        dbTable("dbtable.hunter_trap_loot", serverOnly = true) {
            column("creature", LOOT_CREATURE, VarType.DBROW)
            column("obj", LOOT_OBJ, VarType.OBJ)
            column("min", LOOT_MIN, VarType.INT)
            column("max", LOOT_MAX, VarType.INT)

            fun loot(row: String, creature: String, obj: String, min: Int = 1, max: Int = min) =
                row(row) {
                    columnRSCM(LOOT_CREATURE, creature)
                    columnRSCM(LOOT_OBJ, obj)
                    column(LOOT_MIN, min)
                    column(LOOT_MAX, max)
                }

            fun bird(name: String, feather: String) {
                val creature = "dbrow.hunter_$name"
                loot("dbrow.hunter_loot_${name}_bones", creature, "obj.bones")
                loot("dbrow.hunter_loot_${name}_meat", creature, "obj.spit_raw_bird_meat")
                loot("dbrow.hunter_loot_${name}_feather", creature, feather, 5, 10)
            }

            bird("crimson_swift", "obj.hunting_jungle_feather")
            bird("golden_warbler", "obj.hunting_desert_feather")
            bird("copper_longtail", "obj.hunting_woodland_feather")
            bird("cerulean_twitch", "obj.hunting_polar_feather")
            bird("tropical_wagtail", "obj.hunting_stripy_bird_feather")
            loot("dbrow.hunter_loot_ferret", "dbrow.hunter_ferret", "obj.hunting_ferret")
            loot("dbrow.hunter_loot_chinchompa", "dbrow.hunter_chinchompa", "obj.chinchompa_captured")
            loot("dbrow.hunter_loot_carnivorous_chinchompa", "dbrow.hunter_carnivorous_chinchompa", "obj.chinchompa_big_captured")
            loot("dbrow.hunter_loot_black_chinchompa", "dbrow.hunter_black_chinchompa", "obj.chinchompa_black")
        }
}
