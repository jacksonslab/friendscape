package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object HunterNetTables {
    const val NPC = 0
    const val LEVEL = 1
    const val XP = 2
    const val JAR = 3
    const val PRODUCT = 4
    const val LOW = 5
    const val HIGH = 6
    const val RESPAWN = 7

    private const val BUTTERFLY_JAR = "obj.butterfly_jar"
    private const val IMPLING_JAR = "obj.ii_impling_jar"

    /** Mod Ash: implings are 12% at level 1 and 98% at level 99, before the magic net's +8%. */
    private const val IMPLING_LOW = 30
    private const val IMPLING_HIGH = 250

    fun creatures() =
        dbTable("dbtable.hunter_net_creature", serverOnly = true) {
            column("npc", NPC, VarType.NPC)
            column("level", LEVEL, VarType.INT)
            column("xp", XP, VarType.INT)
            column("jar", JAR, VarType.OBJ)
            column("product", PRODUCT, VarType.OBJ)
            column("low", LOW, VarType.INT)
            column("high", HIGH, VarType.INT)
            column("respawn", RESPAWN, VarType.INT)

            fun creature(row: String, npc: String, level: Int, xp10: Int, jar: String, product: String, respawn: Int) =
                row(row) {
                    columnRSCM(NPC, npc)
                    column(LEVEL, level)
                    column(XP, xp10)
                    columnRSCM(JAR, jar)
                    columnRSCM(PRODUCT, product)
                    column(LOW, IMPLING_LOW)
                    column(HIGH, IMPLING_HIGH)
                    column(RESPAWN, respawn)
                }

            creature("dbrow.hunter_net_ruby_harvest", "npc.butterfly_ruby", 15, 240, BUTTERFLY_JAR, "obj.butterfly_jar_ruby", 20)
            creature("dbrow.hunter_net_sapphire_glacialis", "npc.butterfly_glacialis", 25, 340, BUTTERFLY_JAR, "obj.butterfly_jar_glacialis", 20)
            creature("dbrow.hunter_net_snowy_knight", "npc.butterfly_snowy", 35, 440, BUTTERFLY_JAR, "obj.butterfly_jar_snowy", 20)
            creature("dbrow.hunter_net_black_warlock", "npc.butterfly_warlock", 45, 540, BUTTERFLY_JAR, "obj.butterfly_jar_warlock", 20)

            val implings =
                listOf(
                    Triple("baby", 17, 200),
                    Triple("young", 22, 220),
                    Triple("gourmet", 28, 240),
                    Triple("earth", 36, 270),
                    Triple("essence", 42, 290),
                    Triple("eclectic", 50, 320),
                    Triple("nature", 58, 360),
                    Triple("magpie", 65, 2160),
                    Triple("ninja", 74, 2400),
                    Triple("dragon", 83, 3000),
                    Triple("lucky", 89, 3800),
                )
            implings.forEachIndexed { index, (name, level, xp10) ->
                val type = index + 1
                creature(
                    "dbrow.hunter_net_${name}_impling",
                    "npc.ii_impling_type_$type",
                    level,
                    xp10,
                    IMPLING_JAR,
                    "obj.ii_captured_impling_$type",
                    100,
                )
            }
        }
}
