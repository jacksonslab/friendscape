package org.rsmod.content.skills.farming.pack

import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import dev.openrune.tables.production.productionTable
import org.rsmod.map.CoordGrid

/**
 * Crops, the patches they grow in, and the farms those patches belong to.
 *
 * XP is stored multiplied by ten, as every xp column in the production tables is. `grow_base` is
 * the patch loc's multiloc index for a freshly planted crop; the client renders the patch from
 * `farming_transmit_*`, so every other state is an offset from it (see the script's
 * `PatchTransmit`). Disease chance is out of 128 per growth cycle with no compost.
 *
 * Rates, levels and xp follow the OSRS wiki; tick lengths and stage counts follow the patch locs'
 * own multiloc tables. Tree disease rates the wiki does not list (oak, willow, yew) sit between
 * the published fruit tree, maple and magic rates.
 */
object FarmingTables {
    const val COL_ID = 7
    const val COL_NAME = 8
    const val COL_PLANT_XP = 9
    const val COL_CHECK_XP = 10
    const val COL_GROW_BASE = 11
    const val COL_STAGES = 12
    const val COL_TICK_MINUTES = 13
    const val COL_DISEASED_BASE = 14
    const val COL_TRANSMIT = 15
    const val COL_DISEASE_CHANCE = 16
    const val COL_SAVE_CHANCE = 17
    const val COL_PROTECTION = 18
    const val COL_PAYMENT = 19
    const val COL_TREE = 20
    const val COL_SEED = 21
    const val COL_SEEDLING = 22
    const val COL_WATERED_SEEDLING = 23

    const val AREA_NAME = 0
    const val AREA_REGIONS = 1
    const val AREA_BOUNDS = 2

    const val PATCH_LOC = 0
    const val PATCH_KIND = 1
    const val PATCH_VARP = 2
    const val PATCH_AREA = 3
    const val PATCH_FARMER = 4
    const val PATCH_FARMER_OP = 5

    const val ALLOTMENT = "Allotment"
    const val FLOWER = "Flower"
    const val HERB = "Herb"
    const val TREE = "Tree"
    const val FRUIT_TREE = "FruitTree"

    private const val HERB_DISEASE = 27
    private const val ALLOTMENT_DISEASE = 27
    private const val FLOWER_DISEASE = 27
    private const val FRUIT_TREE_DISEASE = 18
    private const val HERB_SAVE_HIGH = 80
    private const val ALLOTMENT_SAVE_HIGH = 180

    /**
     * Snape grass reached the allotment patch long after the other seven crops, so its art sits in
     * whatever multiloc slots were spare. Banks are healthy, watered, diseased, dead; each is one
     * entry per stage.
     */
    private val SNAPE_GRASS_TRANSMIT =
        listOf(
            128, 129, 130, 131, 132, 133, 134, 138,
            63, 64, 65, 66, 67, 68, 69, 138,
            196, 196, 197, 198, 202, 203, 204, 204,
            193, 193, 194, 195, 209, 210, 211, 211,
        )

    fun crops() =
        productionTable(
            "dbtable.farming_crop",
            serverOnly = true,
            extraColumns = {
                column("id", COL_ID, VarType.INT)
                column("name", COL_NAME, VarType.STRING)
                column("plant_xp", COL_PLANT_XP, VarType.INT)
                column("check_xp", COL_CHECK_XP, VarType.INT)
                column("grow_base", COL_GROW_BASE, VarType.INT)
                column("stages", COL_STAGES, VarType.INT)
                column("tick_minutes", COL_TICK_MINUTES, VarType.INT)
                column("diseased_base", COL_DISEASED_BASE, VarType.INT)
                column("transmit", COL_TRANSMIT, VarType.INT)
                column("disease_chance", COL_DISEASE_CHANCE, VarType.INT)
                column("save_chance", COL_SAVE_CHANCE, VarType.INT, VarType.INT)
                column("protection", COL_PROTECTION, VarType.DBROW)
                column("payment", COL_PAYMENT, VarType.OBJ, VarType.INT)
                column("tree", COL_TREE, VarType.LOC)
                column("seed", COL_SEED, VarType.OBJ)
                column("seedling", COL_SEEDLING, VarType.OBJ)
                column("watered_seedling", COL_WATERED_SEEDLING, VarType.OBJ)
            },
        ) {
            fun crop(
                id: Int,
                name: String,
                kind: String,
                planted: String,
                plantedCount: Int,
                produce: String,
                level: Int,
                plantXp: Double,
                harvestXp: Double,
                growBase: Int,
                stages: Int,
                tickMinutes: Int,
                diseaseChance: Int,
                checkXp: Double = 0.0,
                diseasedBase: Int = -1,
                saveChance: Pair<Int, Int>? = null,
                protection: String? = null,
                payment: Pair<String, Int>? = null,
                tree: String? = null,
                seed: String? = null,
                transmit: List<Int> = emptyList(),
            ) =
                row("dbrow.farming_crop_${name.replace(' ', '_')}") {
                    production {
                        input(planted, plantedCount)
                        statReq("stat.farming", level)
                        xp((harvestXp * 10).toInt())
                        output(produce)
                        category(kind)
                    }
                    column(COL_ID, id)
                    column(COL_NAME, name)
                    column(COL_PLANT_XP, (plantXp * 10).toInt())
                    column(COL_CHECK_XP, (checkXp * 10).toInt())
                    column(COL_GROW_BASE, growBase)
                    column(COL_STAGES, stages)
                    column(COL_TICK_MINUTES, tickMinutes)
                    column(COL_DISEASED_BASE, diseasedBase)
                    column(COL_DISEASE_CHANCE, diseaseChance)
                    if (transmit.isNotEmpty()) {
                        column(COL_TRANSMIT, *transmit.toTypedArray())
                    }
                    saveChance?.let { (low, high) -> column(COL_SAVE_CHANCE, low, high) }
                    protection?.let { columnRSCM(COL_PROTECTION, it) }
                    payment?.let { (obj, count) -> column(COL_PAYMENT, ConstantProvider.getMapping(obj), count) }
                    tree?.let { columnRSCM(COL_TREE, it) }
                    if (seed != null) {
                        columnRSCM(COL_SEED, seed)
                        columnRSCM(COL_SEEDLING, seedling(planted))
                        columnRSCM(COL_WATERED_SEEDLING, seedling(planted) + "_watered")
                    }
                }

            fun allotment(
                id: Int,
                name: String,
                level: Int,
                plantXp: Double,
                harvestXp: Double,
                growBase: Int,
                stages: Int,
                saveLow: Int,
                payment: Pair<String, Int>,
                protection: String? = null,
                seed: String = "obj.${name.replace(' ', '_')}_seed",
                produce: String = "obj.${name.replace(' ', '_')}",
                saveHigh: Int = ALLOTMENT_SAVE_HIGH,
                transmit: List<Int> = emptyList(),
            ) =
                crop(
                    id = id,
                    name = name,
                    kind = ALLOTMENT,
                    planted = seed,
                    plantedCount = 3,
                    produce = produce,
                    level = level,
                    plantXp = plantXp,
                    harvestXp = harvestXp,
                    growBase = growBase,
                    stages = stages,
                    tickMinutes = 10,
                    diseaseChance = ALLOTMENT_DISEASE,
                    saveChance = saveLow to saveHigh,
                    protection = protection?.let { "dbrow.farming_crop_$it" },
                    payment = payment,
                    transmit = transmit,
                )

            fun flower(
                id: Int,
                name: String,
                produce: String,
                level: Int,
                plantXp: Double,
                harvestXp: Double,
                growBase: Int,
            ) =
                crop(
                    id = id,
                    name = name,
                    kind = FLOWER,
                    planted = "obj.${name.replace(' ', '_')}_seed",
                    plantedCount = 1,
                    produce = produce,
                    level = level,
                    plantXp = plantXp,
                    harvestXp = harvestXp,
                    growBase = growBase,
                    stages = 5,
                    tickMinutes = 5,
                    diseaseChance = FLOWER_DISEASE,
                )

            fun herb(
                id: Int,
                name: String,
                produce: String,
                level: Int,
                plantXp: Double,
                harvestXp: Double,
                growBase: Int,
                diseasedBase: Int,
                saveLow: Int,
            ) =
                crop(
                    id = id,
                    name = name,
                    kind = HERB,
                    planted = "obj.${name.replace(' ', '_')}_seed",
                    plantedCount = 1,
                    produce = produce,
                    level = level,
                    plantXp = plantXp,
                    harvestXp = harvestXp,
                    growBase = growBase,
                    stages = 5,
                    tickMinutes = 20,
                    diseaseChance = HERB_DISEASE,
                    diseasedBase = diseasedBase,
                    saveChance = saveLow to HERB_SAVE_HIGH,
                )

            fun tree(
                id: Int,
                name: String,
                sapling: String,
                seed: String,
                logs: String,
                tree: String,
                level: Int,
                plantXp: Double,
                checkXp: Double,
                growBase: Int,
                stages: Int,
                diseaseChance: Int,
                payment: Pair<String, Int>,
            ) =
                crop(
                    id = id,
                    name = name,
                    kind = TREE,
                    planted = sapling,
                    plantedCount = 1,
                    produce = logs,
                    level = level,
                    plantXp = plantXp,
                    harvestXp = 0.0,
                    checkXp = checkXp,
                    growBase = growBase,
                    stages = stages,
                    tickMinutes = 40,
                    diseaseChance = diseaseChance,
                    payment = payment,
                    tree = tree,
                    seed = seed,
                )

            fun fruitTree(
                id: Int,
                name: String,
                seed: String,
                produce: String,
                level: Int,
                plantXp: Double,
                checkXp: Double,
                harvestXp: Double,
                growBase: Int,
                payment: Pair<String, Int>,
                sapling: String = "obj.plantpot_${name}_sapling",
            ) =
                crop(
                    id = id,
                    name = name,
                    kind = FRUIT_TREE,
                    planted = sapling,
                    plantedCount = 1,
                    produce = produce,
                    level = level,
                    plantXp = plantXp,
                    harvestXp = harvestXp,
                    checkXp = checkXp,
                    growBase = growBase,
                    stages = 7,
                    tickMinutes = 160,
                    diseaseChance = FRUIT_TREE_DISEASE,
                    payment = payment,
                    seed = seed,
                )

            allotment(1, "potato", 1, 8.0, 9.0, 6, 5, 101, "obj.bucket_compost" to 2, "marigold")
            allotment(2, "onion", 5, 9.5, 10.5, 13, 5, 105, "obj.sack_potato_10" to 1, "marigold")
            allotment(3, "cabbage", 7, 10.0, 11.5, 20, 5, 107, "obj.sack_onion_10" to 1, "rosemary")
            allotment(4, "tomato", 12, 12.5, 14.0, 27, 5, 112, "obj.sack_cabbage_10" to 2, "marigold")
            allotment(5, "sweetcorn", 20, 17.0, 19.0, 34, 7, 88, "obj.jute_fibre" to 10)
            allotment(6, "strawberry", 31, 26.0, 29.0, 43, 7, 103, "obj.basket_apple_5" to 1)
            allotment(7, "watermelon", 47, 48.5, 54.5, 52, 9, 126, "obj.curry_leaf" to 10, "nasturtium")
            allotment(
                id = 8,
                name = "snape grass",
                level = 61,
                plantXp = 82.0,
                harvestXp = 82.0,
                growBase = 128,
                stages = 8,
                saveLow = 148,
                saveHigh = 195,
                payment = "obj.jangerberries" to 5,
                protection = "white_lily",
                transmit = SNAPE_GRASS_TRANSMIT,
            )

            flower(9, "marigold", "obj.marigold", 2, 8.5, 47.0, 8)
            flower(10, "rosemary", "obj.rosemary", 11, 12.0, 66.5, 13)
            flower(11, "nasturtium", "obj.nasturtium", 24, 19.5, 111.0, 18)
            flower(12, "woad", "obj.woadleaf", 25, 20.5, 115.5, 23)
            flower(13, "limpwurt", "obj.limpwurt_root", 26, 21.5, 120.0, 28)
            flower(14, "white lily", "obj.whitelilly", 58, 42.0, 250.0, 37)

            herb(15, "guam", "obj.unidentified_guam", 9, 11.0, 12.5, 4, 128, 25)
            herb(16, "marrentill", "obj.unidentified_marentill", 14, 13.5, 15.0, 11, 131, 28)
            herb(17, "tarromin", "obj.unidentified_tarromin", 19, 16.0, 18.0, 18, 134, 31)
            herb(18, "harralander", "obj.unidentified_harralander", 26, 21.5, 24.0, 25, 137, 36)
            herb(19, "ranarr", "obj.unidentified_ranarr", 32, 26.5, 30.5, 32, 140, 39)
            herb(20, "toadflax", "obj.unidentified_toadflax", 38, 34.0, 38.5, 39, 143, 43)
            herb(21, "irit", "obj.unidentified_irit", 44, 43.0, 48.5, 46, 146, 46)
            herb(22, "avantoe", "obj.unidentified_avantoe", 50, 54.5, 61.5, 53, 149, 50)
            herb(23, "kwuarm", "obj.unidentified_kwuarm", 56, 69.0, 78.0, 68, 152, 54)
            herb(24, "snapdragon", "obj.unidentified_snapdragon", 62, 87.5, 98.5, 75, 155, 57)
            herb(25, "cadantine", "obj.unidentified_cadantine", 67, 106.5, 120.0, 82, 158, 60)
            herb(26, "lantadyme", "obj.unidentified_lantadyme", 73, 134.5, 151.5, 89, 161, 64)
            herb(27, "dwarf weed", "obj.unidentified_dwarf_weed", 79, 170.5, 192.0, 96, 164, 67)
            herb(28, "torstol", "obj.unidentified_torstol", 85, 199.5, 224.5, 103, 167, 71)

            tree(
                id = 29,
                name = "oak",
                sapling = "obj.plantpot_oak_sapling",
                seed = "obj.acorn",
                logs = "obj.oak_logs",
                tree = "loc.oaktree",
                level = 15,
                plantXp = 14.0,
                checkXp = 467.3,
                growBase = 8,
                stages = 5,
                diseaseChance = 18,
                payment = "obj.basket_tomato_5" to 1,
            )
            tree(
                id = 30,
                name = "willow",
                sapling = "obj.plantpot_willow_sapling",
                seed = "obj.willow_seed",
                logs = "obj.willow_logs",
                tree = "loc.willowtree",
                level = 30,
                plantXp = 25.0,
                checkXp = 1456.5,
                growBase = 15,
                stages = 7,
                diseaseChance = 15,
                payment = "obj.basket_apple_5" to 1,
            )
            tree(
                id = 31,
                name = "maple",
                sapling = "obj.plantpot_maple_sapling",
                seed = "obj.maple_seed",
                logs = "obj.maple_logs",
                tree = "loc.mapletree",
                level = 45,
                plantXp = 45.0,
                checkXp = 3403.4,
                growBase = 24,
                stages = 9,
                diseaseChance = 13,
                payment = "obj.basket_orange_5" to 1,
            )
            tree(
                id = 32,
                name = "yew",
                sapling = "obj.plantpot_yew_sapling",
                seed = "obj.yew_seed",
                logs = "obj.yew_logs",
                tree = "loc.yewtree",
                level = 60,
                plantXp = 81.0,
                checkXp = 7069.9,
                growBase = 35,
                stages = 11,
                diseaseChance = 11,
                payment = "obj.cactus_spine" to 10,
            )
            tree(
                id = 33,
                name = "magic",
                sapling = "obj.plantpot_magic_tree_sapling",
                seed = "obj.magic_tree_seed",
                logs = "obj.magic_logs",
                tree = "loc.magictree",
                level = 75,
                plantXp = 145.5,
                checkXp = 13768.3,
                growBase = 48,
                stages = 13,
                diseaseChance = 9,
                payment = "obj.coconut" to 25,
            )

            fruitTree(34, "apple", "obj.apple_tree_seed", "obj.cooking_apple", 27, 22.0, 1199.5, 8.5, 8, "obj.sweetcorn" to 9)
            fruitTree(35, "banana", "obj.banana_tree_seed", "obj.banana", 33, 28.0, 1750.5, 10.5, 35, "obj.basket_apple_5" to 4)
            fruitTree(36, "orange", "obj.orange_tree_seed", "obj.orange", 39, 35.5, 2470.2, 13.5, 72, "obj.basket_strawberry_5" to 3)
            fruitTree(37, "curry", "obj.curry_tree_seed", "obj.curry_leaf", 42, 40.0, 2906.9, 15.0, 99, "obj.basket_banana_5" to 5)
            fruitTree(38, "pineapple", "obj.pineapple_tree_seed", "obj.pineapple", 51, 57.0, 4605.0, 21.5, 136, "obj.watermelon" to 10)
            fruitTree(39, "papaya", "obj.papaya_tree_seed", "obj.papaya", 57, 72.0, 6146.6, 27.0, 163, "obj.pineapple" to 10)
            fruitTree(40, "palm", "obj.palm_tree_seed", "obj.coconut", 68, 110.5, 10150.1, 41.5, 200, "obj.papaya" to 15)
            fruitTree(41, "dragonfruit", "obj.dragonfruit_tree_seed", "obj.dragonfruit", 81, 140.0, 17335.0, 70.0, 227, "obj.coconut" to 15)
        }

    /** The plant pot a seed turns into once it is sown; saplings are named after their tree. */
    private fun seedling(sapling: String): String =
        when (sapling) {
            "obj.plantpot_oak_sapling" -> "obj.plantpot_acorn"
            "obj.plantpot_willow_sapling" -> "obj.plantpot_willow_seed"
            "obj.plantpot_maple_sapling" -> "obj.plantpot_maple_seed"
            "obj.plantpot_yew_sapling" -> "obj.plantpot_yew_seed"
            "obj.plantpot_magic_tree_sapling" -> "obj.plantpot_magic_tree_seed"
            else -> sapling.removeSuffix("_sapling") + "_seed"
        }

    /**
     * A farm is the set of map squares the client is sent that farm's patches in, as live does:
     * all farms share the same `farming_transmit_*` varbits. `bounds` narrows a farm to one box
     * where it shares a map square with another (Catherby's allotments and fruit tree).
     */
    fun areas() =
        dbTable("dbtable.farming_area", serverOnly = true) {
            column("name", AREA_NAME, VarType.STRING)
            column("regions", AREA_REGIONS, VarType.INT)
            column("bounds", AREA_BOUNDS, VarType.COORDGRID, VarType.COORDGRID)

            fun area(row: String, name: String, vararg regions: Int, bounds: Pair<CoordGrid, CoordGrid>? = null) =
                row("dbrow.farming_area_$row") {
                    column(AREA_NAME, name)
                    column(AREA_REGIONS, *regions.toTypedArray())
                    bounds?.let { (min, max) -> column(AREA_BOUNDS, min.packed, max.packed) }
                }

            area("falador", "Falador", 12083)
            area(
                "catherby",
                "Catherby",
                11062,
                11061,
                11318,
                bounds = CoordGrid(2816, 3440, 0) to CoordGrid(2839, 3455, 0),
            )
            area("ardougne", "Ardougne", 10548)
            area("morytania", "Morytania", 14391, 14390)
            area("hosidius", "Hosidius", 6967, 6711)
            area("lumbridge_tree", "Lumbridge", 12594, 12850)
            area("varrock_tree", "Varrock", 12854, 12853)
            area("falador_tree", "Falador Park", 11828, 12084)
            area("taverley_tree", "Taverley", 11573, 11829)
            area("gnome_stronghold", "Gnome Stronghold", 9781, 9782, 9526, 9525)
            area("tree_gnome_village", "Tree Gnome Village", 9777, 10033)
            area("brimhaven", "Brimhaven", 11058, 11057)
            area("catherby_fruit", "Catherby", 11317)
            area("lletya", "Lletya", 9265, 11103)
        }

    fun patches() =
        dbTable("dbtable.farming_patch", serverOnly = true) {
            column("loc", PATCH_LOC, VarType.LOC)
            column("kind", PATCH_KIND, VarType.STRING)
            column("varp", PATCH_VARP, VarType.VARP)
            column("area", PATCH_AREA, VarType.DBROW)
            column("farmer", PATCH_FARMER, VarType.NPC)
            column("farmer_op", PATCH_FARMER_OP, VarType.INT)

            var varp = 0

            fun patch(row: String, loc: String, kind: String, area: String, farmer: String? = null, op: Int = 3) =
                row("dbrow.farming_patch_$row") {
                    columnRSCM(PATCH_LOC, loc)
                    column(PATCH_KIND, kind)
                    columnRSCM(PATCH_VARP, "varp.farming_patch_%02d".format(++varp))
                    columnRSCM(PATCH_AREA, "dbrow.farming_area_$area")
                    farmer?.let {
                        columnRSCM(PATCH_FARMER, it)
                        column(PATCH_FARMER_OP, op)
                    }
                }

            fun farm(prefix: String, area: String, first: String, second: String, veg: Int, flower: Int, herb: Int, farmer: String) {
                patch("${prefix}_$first", "loc.farming_veg_patch_$veg", ALLOTMENT, area, farmer, 3)
                patch("${prefix}_$second", "loc.farming_veg_patch_${veg + 1}", ALLOTMENT, area, farmer, 4)
                patch("${prefix}_flower", "loc.farming_flower_patch_$flower", FLOWER, area)
                patch("${prefix}_herb", "loc.farming_herb_patch_$herb", HERB, area)
            }

            farm("falador", "falador", "nw", "se", 1, 1, 1, "npc.elstan")
            farm("catherby", "catherby", "n", "s", 3, 2, 2, "npc.dantaera")
            farm("ardougne", "ardougne", "n", "s", 5, 3, 3, "npc.kragen")
            farm("morytania", "morytania", "nw", "se", 7, 4, 4, "npc.lyra")
            farm("hosidius", "hosidius", "ne", "sw", 10, 5, 6, "npc.hosidius_allotment_gardener")

            patch("lumbridge_tree", "loc.farming_tree_patch_4", TREE, "lumbridge_tree", "npc.farming_gardener_tree_4")
            patch("varrock_tree", "loc.farming_tree_patch_3", TREE, "varrock_tree", "npc.farming_gardener_tree_3_02")
            patch("falador_tree", "loc.farming_tree_patch_2", TREE, "falador_tree", "npc.farming_gardener_tree_2")
            patch("taverley_tree", "loc.farming_tree_patch_1", TREE, "taverley_tree", "npc.farming_gardener_tree_1")
            patch(
                "gnome_stronghold_tree",
                "loc.farming_tree_patch_5",
                TREE,
                "gnome_stronghold",
                "npc.farming_gardener_tree_gnome",
            )
            patch(
                "gnome_stronghold_fruit",
                "loc.farming_fruit_tree_patch_1",
                FRUIT_TREE,
                "gnome_stronghold",
                "npc.farming_gardener_fruit_1",
            )
            patch(
                "tree_gnome_village_fruit",
                "loc.farming_fruit_tree_patch_2",
                FRUIT_TREE,
                "tree_gnome_village",
                "npc.farming_gardener_fruit_2",
            )
            patch("brimhaven_fruit", "loc.farming_fruit_tree_patch_3", FRUIT_TREE, "brimhaven", "npc.garth")
            patch(
                "catherby_fruit",
                "loc.farming_fruit_tree_patch_4",
                FRUIT_TREE,
                "catherby_fruit",
                "npc.farming_gardener_fruit_4",
            )
            patch(
                "lletya_fruit",
                "loc.farming_fruit_tree_patch_5",
                FRUIT_TREE,
                "lletya",
                "npc.farming_gardener_fruit_tree_5",
            )
        }
}
