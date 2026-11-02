package org.rsmod.content.skills.thieving.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Every stall loc with a Steal-from op. XP is in tenths, respawn in ticks, and loot is a weighted
 * (obj, min, max, weight) list. A stall with an `empty` loc swaps to it while restocking; one
 * without keeps its model and just refuses thieves until the timer ends.
 */
object ThievingStallTable {
    const val LOC = 0
    const val EMPTY = 1
    const val LEVEL = 2
    const val XP = 3
    const val RESPAWN = 4
    const val LOOT = 5
    const val OWNERS = 6
    const val GUARDS = 7

    private val VEG =
        listOf(drop("potato", 3), drop("cabbage", 2), drop("onion", 2), drop("tomato", 2), drop("garlic", 1))
    private val BAKERY = listOf(drop("bread", 5), drop("cake", 13), drop("chocolate_slice", 2))
    private val CRAFTING =
        listOf(
            drop("amulet_mould", 12),
            drop("jewl_bracelet_mould", 12),
            drop("chisel", 6),
            drop("gold_bar", 2),
            drop("necklace_mould", 11),
            drop("ring_mould", 11),
        )
    private val FRUIT =
        listOf(
            drop("cooking_apple", 40),
            drop("banana", 20),
            drop("jangerberries", 5),
            drop("lemon", 5),
            drop("redberries", 5),
            drop("pineapple", 5),
            drop("lime", 5),
            drop("strawberry", 7),
            drop("macro_triffidfruit", 5),
            drop("golovanova_top", 2),
            drop("papaya", 1),
        )
    private val FISH = listOf(drop("raw_salmon", 14), drop("raw_tuna", 5), drop("raw_lobster", 1))
    private val SILVER = listOf(drop("silver_ore", 16), drop("silver_bar", 3), drop("tiara", 1))
    private val GEMS =
        listOf(drop("uncut_sapphire", 105), drop("uncut_emerald", 17), drop("uncut_ruby", 5), drop("uncut_diamond", 1))
    private val SEEDS =
        listOf(
            drop("hammerstone_hop_seed", 120),
            drop("potato_seed", 119),
            drop("marigold_seed", 119),
            drop("barley_seed", 118),
            drop("onion_seed", 89),
            drop("asgarnian_hop_seed", 83),
            drop("cabbage_seed", 71),
            drop("yanillian_hop_seed", 47),
            drop("rosemary_seed", 36),
            drop("nasturtium_seed", 35),
            drop("tomato_seed", 35),
            drop("jute_seed", 35),
            drop("sweetcorn_seed", 30),
            drop("krandorian_hop_seed", 24),
            drop("strawberry_seed", 18),
            drop("wildblood_hop_seed", 12),
            drop("watermelon_seed", 9),
        )
    private val WINE =
        listOf(
            drop("jug_empty", 39),
            drop("jug_water", 20),
            drop("grapes", 17),
            drop("jug_wine", 13),
            drop("rag_bottle_wine", 11),
        )

    private val ARDOUGNE_GUARDS =
        listOf(
            "ardougne_guard",
            "ardougne_guard_variant01",
            "ardougne_guard_f",
            "ardougne_guard_f_variant01",
            "knight_of_ardougne",
            "knight_of_ardougne_f",
            "paladin2",
            "paladin_f_variant01",
        )
    private val KOUREND_GUARDS =
        listOf("m1", "m2", "m3", "m4", "f1", "f2", "f3", "f4").map { "kourend_guard_$it" }
    private val MARKET_GUARDS = ARDOUGNE_GUARDS + KOUREND_GUARDS
    private val RELLEKKA_GUARDS = listOf("viking_guard", "viking_guard2")
    private val ETCETERIA_GUARDS = listOf("etc_guard1", "etc_guard2")
    private val KELDAGRIM_GUARDS = listOf("dwarf_city_black_guard1", "dwarf_city_black_guard2", "dwarf_city_black_guard3")
    private val PRIF_GUARDS = listOf("prif_guard2", "prif_guard3", "prif_guard6")
    private val FORTIS_GUARDS =
        (1..5).flatMap { listOf("varlamore_guard_m_$it", "varlamore_guard_f_$it") }
    private val PORT_ROBERTS_GUARDS =
        listOf("port_roberts_guard_m1", "port_roberts_guard_m2", "port_roberts_guard_f1", "port_roberts_guard_f2")
    private val DRAYNOR_GUARDS = listOf("farming_market_guard")
    private val MOR_UL_REK_GUARDS = listOf("tzhaar_ket_strong")

    fun stalls() =
        dbTable("dbtable.friendscape_thieving_stall", serverOnly = true) {
            column("loc", LOC, VarType.LOC)
            column("empty", EMPTY, VarType.LOC)
            column("level", LEVEL, VarType.INT)
            column("xp", XP, VarType.INT)
            column("respawn", RESPAWN, VarType.INT)
            column("loot", LOOT, VarType.OBJ, VarType.INT, VarType.INT, VarType.INT)
            column("owners", OWNERS, VarType.NPC)
            column("guards", GUARDS, VarType.NPC)

            fun stall(
                loc: String,
                empty: String?,
                level: Int,
                xp10: Int,
                respawn: Int,
                loot: List<Drop>,
                owners: List<String> = emptyList(),
                guards: List<String> = emptyList(),
            ) =
                row("dbrow.thieving_stall_$loc") {
                    columnRSCM(LOC, "loc.$loc")
                    empty?.let { columnRSCM(EMPTY, "loc.$it") }
                    column(LEVEL, level)
                    column(XP, xp10)
                    column(RESPAWN, respawn)
                    dropColumn(LOOT, loot)
                    npcColumn(OWNERS, owners)
                    npcColumn(GUARDS, guards)
                }

            stall("seed_stall", null, 27, 100, 4, SEEDS, listOf("seed_merchant"), DRAYNOR_GUARDS)
            stall("rag_market_stall", "rag_market_stall_empty", 22, 270, 8, WINE, listOf("rag_wine_merchant"), DRAYNOR_GUARDS)
            stall("tea_stall", null, 5, 160, 4, listOf(drop("cup_of_tea")), listOf("tea_seller"))

            stall("cakethiefstall", "market", 5, 160, 4, BAKERY, listOf("baker_merchant_ardougne", "baker_merchant_ardougne2", "baker_merchant"), MARKET_GUARDS)
            stall("silkthiefstall", "market", 20, 240, 8, listOf(drop("silk")), listOf("silk_merchant_ardougne", "silk_merchant"), MARKET_GUARDS)
            stall("furthiefstall", "market", 35, 450, 12, listOf(drop("fur")), listOf("fur_merchant_ardougne"), MARKET_GUARDS)
            stall("silverthiefstall", "market", 50, 2050, 32, SILVER, listOf("silver_merchant_ardougne"), MARKET_GUARDS)
            stall("spicethiefstall", "market", 65, 920, 10, listOf(drop("spicespot")), listOf("spice_merchant_ardougne"), MARKET_GUARDS)
            stall("gemthiefstall", "market", 75, 4080, 100, GEMS, listOf("gem_merchant_ardougne", "gem_merchant"), MARKET_GUARDS)

            stall("hos_stall_bread", "hos_stall_empty", 5, 160, 4, BAKERY, listOf("hosidius_foodstore"), KOUREND_GUARDS)
            stall("hos_fruit_stall_02", "hos_fruit_stall", 25, 285, 2, FRUIT, emptyList(), KOUREND_GUARDS)
            stall("fish_stall_warrens", null, 42, 420, 12, FISH, listOf("warrens_fishmonger"))

            stall("misc_veg_market", null, 2, 100, 2, VEG, listOf("misc_veg_monger"))
            stall("etc_veg_market", null, 2, 100, 2, VEG, listOf("etc_veg_monger"), ETCETERIA_GUARDS)
            stall("misc_fish_market", null, 42, 420, 12, FISH, listOf("misc_fish_monger"))
            stall("etc_fish_market", null, 42, 420, 12, FISH, listOf("etc_fish_monger"), ETCETERIA_GUARDS)
            stall("viking_fish_market", "viking_market", 42, 420, 12, FISH, listOf("viking_fish_monger"), RELLEKKA_GUARDS)
            stall("viking_fur_market", "viking_market", 35, 450, 12, listOf(drop("grey_wolf_fur")), listOf("viking_fur_monger"), RELLEKKA_GUARDS)

            stall("rockcounter_withcakes", "empty_rock_cake_counter", 15, 64, 12, listOf(drop("rockcake")), listOf("ogre_trader1", "ogre_trader2"))

            stall("mm_stall_food", null, 5, 160, 6, listOf(drop("banana")))
            stall("mm_stall_crafting", null, 5, 200, 8, CRAFTING)
            stall("mm_stall_general", null, 5, 250, 8, listOf(drop("hammer"), drop("pot_empty"), drop("tinderbox")))
            stall(
                "mm_stall_magic", null, 65, 900, 12,
                listOf(drop("airrune", 6), drop("earthrune", 6), drop("firerune", 6), drop("lawrune", 1), drop("naturerune", 1)),
            )
            stall(
                "mm_stall_scimitar", null, 65, 2100, 32,
                listOf(drop("iron_scimitar", 23), drop("steel_scimitar", 13), drop("mithril_scimitar", 3), drop("adamant_scimitar", 1)),
            )

            stall("dwarf_market_bakery", "dwarf_market_empty_stall", 5, 160, 16, BAKERY, listOf("dwarf_city_shop_bakery"), KELDAGRIM_GUARDS)
            stall("dwarf_market_crafting", "dwarf_market_empty_stall", 5, 200, 8, CRAFTING, listOf("dwarf_city_shop_craft"), KELDAGRIM_GUARDS)
            stall(
                "xbows_dwarf_market", "dwarf_market_empty_stall", 49, 520, 8,
                listOf(
                    drop("bolt", 46, 3),
                    drop("xbows_crossbow_limbs_bronze", 18, 3),
                    drop("xbows_crossbow_bolts_mithril", 3, 3),
                    drop("xbows_crossbow_limbs_mithril", 1, 3),
                    drop("xbows_crossbow_stock_wood", 12, 3),
                ),
                listOf("xbows_sales_dwarf_keldegrim"),
                KELDAGRIM_GUARDS,
            )
            stall("dwarf_market_silver", "dwarf_market_empty_stall", 50, 2050, 32, listOf(drop("silver_ore")), listOf("dwarf_city_shop_silver"), KELDAGRIM_GUARDS)
            stall("dwarf_market_gems", "dwarf_market_empty_stall", 75, 4080, 100, GEMS, listOf("dwarf_city_shop_gems"), KELDAGRIM_GUARDS)

            stall("prif_marketstall_silk", "prif_marketstall_empty", 20, 240, 8, listOf(drop("silk")), listOf("prif_silk"), PRIF_GUARDS)
            stall("prif_marketstall_silver", "prif_marketstall_empty", 50, 2050, 32, SILVER, listOf("prif_silver"), PRIF_GUARDS)
            stall("prif_marketstall_spice", "prif_marketstall_empty", 65, 920, 10, listOf(drop("spicespot")), listOf("prif_spice"), PRIF_GUARDS)
            stall("prif_marketstall_gem", "prif_marketstall_empty", 75, 4080, 100, GEMS, listOf("prif_gem"), PRIF_GUARDS)

            stall("fortis_market_stall_bakers", "fortis_market_stall", 5, 160, 4, BAKERY, listOf("fortis_shop_baker"), FORTIS_GUARDS)
            stall("fortis_market_stall_silk", "fortis_market_stall", 20, 240, 8, listOf(drop("silk")), listOf("fortis_shop_silk"), FORTIS_GUARDS)
            stall("fortis_market_stall_fur", "fortis_market_stall", 35, 450, 12, listOf(drop("grey_wolf_fur")), listOf("fortis_shop_fur"), FORTIS_GUARDS)
            stall("fortis_market_stall_spice", "fortis_market_stall", 65, 920, 10, listOf(drop("spicespot")), listOf("fortis_shop_spices"), FORTIS_GUARDS)
            stall("fortis_market_stall_gems", "fortis_market_stall", 75, 4080, 100, GEMS, listOf("fortis_shop_gems"), FORTIS_GUARDS)

            stall("tzhaar_shopcounter_gem", "tzhaar_shopcounter_empty", 75, 4080, 175,
                listOf(
                    drop("uncut_onyx", 2),
                    drop("onyx", 1),
                    drop("uncut_sapphire", 50000),
                    drop("sapphire", 25000),
                    drop("uncut_emerald", 30000),
                    drop("emerald", 15000),
                    drop("uncut_ruby", 15000),
                    drop("ruby", 7500),
                    drop("uncut_diamond", 4900),
                    drop("diamond", 2450),
                    drop("uncut_dragonstone", 100),
                    drop("dragonstone", 50),
                ),
                listOf("tzhaar_merchant_cityoreandgem"),
                MOR_UL_REK_GUARDS,
            )
            stall("tzhaar_shopcounter_ore", "tzhaar_shopcounter_empty", 82, 3500, 50,
                listOf(
                    drop("iron_ore", 25),
                    drop("coal", 15),
                    drop("silver_ore", 10),
                    drop("gold_ore", 10),
                    drop("mithril_ore", 10),
                    drop("adamantite_ore", 8),
                    drop("runite_ore", 2),
                ),
                listOf("tzhaar_merchant_cityoreandgem"),
                MOR_UL_REK_GUARDS,
            )

            fun portRoberts(stall: String, level: Int, xp10: Int, loot: List<Drop>) =
                stall("port_roberts_market_stall_$stall", "port_roberts_market_stall", level, xp10, 4, loot, emptyList(), PORT_ROBERTS_GUARDS)

            portRoberts("veg", 2, 50, VEG)
            portRoberts("silk", 20, 240, listOf(drop("silk")))
            portRoberts("fur", 35, 385, listOf(drop("fur", 7), drop("grey_wolf_fur", 1)))
            portRoberts(
                "fish", 42, 490,
                listOf(
                    drop("raw_salmon", 170),
                    drop("raw_tuna", 62),
                    drop("raw_lobster", 12),
                    drop("raw_swordtip_squid", 7),
                    drop("raw_giant_krill", 4),
                    drop("raw_haddock", 1),
                ),
            )
            portRoberts("silver", 50, 800, listOf(drop("silver_ore", 6), drop("silver_bar", 3), drop("tiara", 1)))
            portRoberts("spice", 65, 1100, listOf(drop("spicespot")))
            portRoberts(
                "gems", 75, 1295,
                listOf(
                    drop("uncut_sapphire", 210),
                    drop("sapphire", 105),
                    drop("uncut_emerald", 34),
                    drop("emerald", 17),
                    drop("uncut_ruby", 10),
                    drop("ruby", 5),
                    drop("uncut_diamond", 2),
                    drop("diamond", 1),
                ),
            )
            portRoberts(
                "ore", 82, 1910,
                listOf(
                    drop("iron_ore", 25),
                    drop("coal", 15),
                    drop("silver_ore", 10),
                    drop("gold_ore", 10),
                    drop("mithril_ore", 10),
                    drop("adamantite_ore", 4),
                    drop("runite_ore", 1),
                ),
            )
            portRoberts(
                "cball", 87, 2230,
                listOf(
                    drop("bronze_cannonball", 79),
                    drop("iron_cannonball", 64),
                    drop("mcannonball", 56),
                    drop("mithril_cannonball", 24),
                    drop("adamant_cannonball", 24),
                    drop("rune_cannonball", 8),
                    drop("dragon_cannonball", 1),
                ),
            )
        }
}
