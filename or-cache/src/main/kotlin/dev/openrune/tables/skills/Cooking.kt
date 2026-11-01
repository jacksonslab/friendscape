package dev.openrune.tables.skills

import dev.openrune.definition.util.VarType
import dev.openrune.tables.production.productionTable

object Cooking {

    const val COL_BURNT = 7
    const val COL_LOW = 8
    const val COL_HIGH = 9
    const val COL_COOK_TICKS = 10

    /* Optional curves; foods without them use `low`/`high` on every surface. */
    const val COL_LOW_RANGE = 11
    const val COL_HIGH_RANGE = 12
    const val COL_LOW_GAUNTLET = 13
    const val COL_HIGH_GAUNTLET = 14

    /* Optional; foods without it can be cooked on a fire or a range. */
    const val COL_SURFACE = 15
    const val SURFACE_FIRE_ONLY = 1
    const val SURFACE_RANGE_ONLY = 2

    const val COL_NEEDS_KNIFE = 7
    const val COL_CHURN_TICKS = 7

    const val COL_VAT_OFFSET = 7
    const val COL_BARREL_OFFSET = 8

    fun ales() = productionTable(
        "dbtable.cooking_ales",
        serverOnly = true,
        defaultCategory = "Brew",
        extraColumns = {
            column("vat_offset", COL_VAT_OFFSET, VarType.INT)
            column("barrel_offset", COL_BARREL_OFFSET, VarType.INT)
        },
    ) {
        row("dbrow.ale_dwarven_stout") {
            production {
                input("obj.hammerstone_hops", 4)
                statReq("stat.cooking", 19)
                xp(215)
                output("obj.dwarven_stout")
                output("obj.mature_dwarven_stout")
            }
            column(COL_VAT_OFFSET, 4)
            column(COL_BARREL_OFFSET, 4)
        }
        row("dbrow.ale_asgarnian_ale") {
            production {
                input("obj.hammerstone_hops", 4)
                statReq("stat.cooking", 24)
                xp(248)
                output("obj.asgarnian_ale")
                output("obj.mature_asgarnian_ale")
            }
            column(COL_VAT_OFFSET, 10)
            column(COL_BARREL_OFFSET, 6)
        }
        row("dbrow.ale_greenmans_ale") {
            production {
                input("obj.hammerstone_hops", 4)
                statReq("stat.cooking", 29)
                xp(281)
                output("obj.greenmans_ale")
                output("obj.mature_greenmans_ale")
            }
            column(COL_VAT_OFFSET, 16)
            column(COL_BARREL_OFFSET, 8)
        }
        row("dbrow.ale_wizards_mind_bomb") {
            production {
                input("obj.yanillian_hops", 4)
                statReq("stat.cooking", 34)
                xp(314)
                output("obj.wizards_mind_bomb")
                output("obj.mature_wizards_mind_bomb")
            }
            column(COL_VAT_OFFSET, 22)
            column(COL_BARREL_OFFSET, 10)
        }
        row("dbrow.ale_dragon_bitter") {
            production {
                input("obj.krandorian_hops", 4)
                statReq("stat.cooking", 39)
                xp(347)
                output("obj.dragon_bitter")
                output("obj.mature_dragon_bitter")
            }
            column(COL_VAT_OFFSET, 28)
            column(COL_BARREL_OFFSET, 12)
        }
        row("dbrow.ale_moonlight_mead") {
            production {
                input("obj.bittercap_mushroom", 4)
                statReq("stat.cooking", 44)
                xp(380)
                output("obj.moonlight_mead")
                output("obj.mature_moonlight_mead")
            }
            column(COL_VAT_OFFSET, 34)
            column(COL_BARREL_OFFSET, 14)
        }
        row("dbrow.ale_axemans_folly") {
            production {
                input("obj.oak_roots", 1)
                statReq("stat.cooking", 49)
                xp(413)
                output("obj.axemans_folly")
                output("obj.mature_axemans_folly")
            }
            column(COL_VAT_OFFSET, 40)
            column(COL_BARREL_OFFSET, 16)
        }
        row("dbrow.ale_chefs_delight") {
            production {
                input("obj.chocolate_dust", 2)
                statReq("stat.cooking", 54)
                xp(446)
                output("obj.chefs_delight")
                output("obj.mature_chefs_delight")
            }
            column(COL_VAT_OFFSET, 46)
            column(COL_BARREL_OFFSET, 18)
        }
        row("dbrow.ale_slayers_respite") {
            production {
                input("obj.wildblood_hops", 4)
                statReq("stat.cooking", 59)
                xp(479)
                output("obj.slayers_respite")
                output("obj.mature_slayers_respite")
            }
            column(COL_VAT_OFFSET, 52)
            column(COL_BARREL_OFFSET, 20)
        }
        row("dbrow.ale_cider") {
            production {
                input("obj.apple_mush", 4)
                statReq("stat.cooking", 14)
                xp(182)
                output("obj.cider")
                output("obj.mature_cider")
            }
            column(COL_VAT_OFFSET, 58)
            column(COL_BARREL_OFFSET, 22)
        }
    }

    fun foods() = productionTable(
        "dbtable.cooking_foods",
        serverOnly = true,
        extraColumns = {
            column("burnt", COL_BURNT, VarType.OBJ)
            column("low", COL_LOW, VarType.INT)
            column("high", COL_HIGH, VarType.INT)
            column("cook_ticks", COL_COOK_TICKS, VarType.INT)
            column("low_range", COL_LOW_RANGE, VarType.INT)
            column("high_range", COL_HIGH_RANGE, VarType.INT)
            column("low_gauntlet", COL_LOW_GAUNTLET, VarType.INT)
            column("high_gauntlet", COL_HIGH_GAUNTLET, VarType.INT)
            column("surface", COL_SURFACE, VarType.INT)
        },
    ) {
        row("dbrow.cooking_shrimp") {
            production {
                input("obj.raw_shrimp")
                statReq("stat.cooking", 1)
                xp(300)
                output("obj.shrimp")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_shrimp")
            column(COL_LOW, 128)
            column(COL_HIGH, 512)
        }
        row("dbrow.cooking_anchovies") {
            production {
                input("obj.raw_anchovies")
                statReq("stat.cooking", 1)
                xp(300)
                output("obj.anchovies")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish1")
            column(COL_LOW, 128)
            column(COL_HIGH, 512)
        }
        row("dbrow.cooking_sardine") {
            production {
                input("obj.raw_sardine")
                statReq("stat.cooking", 1)
                xp(400)
                output("obj.sardine")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish1")
            column(COL_LOW, 118)
            column(COL_HIGH, 492)
        }
        row("dbrow.cooking_herring") {
            production {
                input("obj.raw_herring")
                statReq("stat.cooking", 5)
                xp(500)
                output("obj.herring")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish2")
            column(COL_LOW, 108)
            column(COL_HIGH, 472)
        }
        row("dbrow.cooking_mackerel") {
            production {
                input("obj.raw_mackerel")
                statReq("stat.cooking", 10)
                xp(600)
                output("obj.mackerel")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish3")
            column(COL_LOW, 98)
            column(COL_HIGH, 452)
        }
        row("dbrow.cooking_trout") {
            production {
                input("obj.raw_trout")
                statReq("stat.cooking", 15)
                xp(700)
                output("obj.trout")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish3")
            column(COL_LOW, 88)
            column(COL_HIGH, 432)
        }
        row("dbrow.cooking_cod") {
            production {
                input("obj.raw_cod")
                statReq("stat.cooking", 18)
                xp(750)
                output("obj.cod")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish3")
            column(COL_LOW, 83)
            column(COL_HIGH, 422)
            column(COL_LOW_RANGE, 88)
            column(COL_HIGH_RANGE, 432)
        }
        row("dbrow.cooking_pike") {
            production {
                input("obj.raw_pike")
                statReq("stat.cooking", 20)
                xp(800)
                output("obj.pike")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish3")
            column(COL_LOW, 78)
            column(COL_HIGH, 412)
        }
        row("dbrow.cooking_salmon") {
            production {
                input("obj.raw_salmon")
                statReq("stat.cooking", 25)
                xp(900)
                output("obj.salmon")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish3")
            column(COL_LOW, 68)
            column(COL_HIGH, 392)
        }
        row("dbrow.cooking_tuna") {
            production {
                input("obj.raw_tuna")
                statReq("stat.cooking", 30)
                xp(1000)
                output("obj.tuna")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish4")
            column(COL_LOW, 58)
            column(COL_HIGH, 372)
        }
        row("dbrow.cooking_lobster") {
            production {
                input("obj.raw_lobster")
                statReq("stat.cooking", 40)
                xp(1200)
                output("obj.lobster")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_lobster")
            column(COL_LOW, 38)
            column(COL_HIGH, 332)
            column(COL_LOW_GAUNTLET, 55)
            column(COL_HIGH_GAUNTLET, 368)
        }
        row("dbrow.cooking_bass") {
            production {
                input("obj.raw_bass")
                statReq("stat.cooking", 43)
                xp(1300)
                output("obj.bass")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burntfish5")
            column(COL_LOW, 33)
            column(COL_HIGH, 312)
        }
        row("dbrow.cooking_swordfish") {
            production {
                input("obj.raw_swordfish")
                statReq("stat.cooking", 45)
                xp(1400)
                output("obj.swordfish")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_swordfish")
            column(COL_LOW, 18)
            column(COL_HIGH, 292)
            column(COL_LOW_RANGE, 30)
            column(COL_HIGH_RANGE, 310)
            column(COL_LOW_GAUNTLET, 30)
            column(COL_HIGH_GAUNTLET, 310)
        }
        row("dbrow.cooking_monkfish") {
            production {
                input("obj.raw_monkfish")
                statReq("stat.cooking", 62)
                xp(1500)
                output("obj.monkfish")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_monkfish")
            column(COL_LOW, 11)
            column(COL_HIGH, 275)
            column(COL_LOW_RANGE, 13)
            column(COL_HIGH_RANGE, 280)
            column(COL_LOW_GAUNTLET, 24)
            column(COL_HIGH_GAUNTLET, 290)
        }
        row("dbrow.cooking_shark") {
            production {
                input("obj.raw_shark")
                statReq("stat.cooking", 80)
                xp(2100)
                output("obj.shark")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_shark")
            column(COL_LOW, 1)
            column(COL_HIGH, 202)
            column(COL_LOW_RANGE, 1)
            column(COL_HIGH_RANGE, 232)
            column(COL_LOW_GAUNTLET, 15)
            column(COL_HIGH_GAUNTLET, 270)
        }
        row("dbrow.cooking_manta_ray") {
            production {
                input("obj.raw_mantaray")
                statReq("stat.cooking", 91)
                xp(2163)
                output("obj.mantaray")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_mantaray")
            column(COL_LOW, 1)
            column(COL_HIGH, 202)
            column(COL_LOW_RANGE, 1)
            column(COL_HIGH_RANGE, 222)
        }
        row("dbrow.cooking_sea_turtle") {
            production {
                input("obj.raw_seaturtle")
                statReq("stat.cooking", 82)
                xp(2113)
                output("obj.seaturtle")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_seaturtle")
            column(COL_LOW, 1)
            column(COL_HIGH, 202)
            column(COL_LOW_RANGE, 1)
            column(COL_HIGH_RANGE, 222)
        }
        row("dbrow.cooking_dark_crab") {
            production {
                input("obj.raw_dark_crab")
                statReq("stat.cooking", 90)
                xp(2150)
                output("obj.dark_crab")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_dark_crab")
            column(COL_LOW, 10)
            column(COL_HIGH, 222)
        }
        row("dbrow.cooking_anglerfish") {
            production {
                input("obj.raw_anglerfish")
                statReq("stat.cooking", 84)
                xp(2300)
                output("obj.anglerfish")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_anglerfish")
            column(COL_LOW, 1)
            column(COL_HIGH, 200)
            column(COL_LOW_RANGE, 1)
            column(COL_HIGH_RANGE, 220)
            column(COL_LOW_GAUNTLET, 12)
            column(COL_HIGH_GAUNTLET, 260)
        }
        row("dbrow.cooking_chicken") {
            production {
                input("obj.raw_chicken")
                statReq("stat.cooking", 1)
                xp(300)
                output("obj.cooked_chicken")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.burnt_chicken")
            column(COL_LOW, 128)
            column(COL_HIGH, 512)
        }
        row("dbrow.cooking_meat") {
            production {
                input("obj.raw_beef")
                statReq("stat.cooking", 1)
                xp(300)
                output("obj.cooked_meat")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.burnt_meat")
            column(COL_LOW, 128)
            column(COL_HIGH, 512)
        }
        row("dbrow.cooking_bear_meat") {
            production {
                input("obj.raw_bear_meat")
                statReq("stat.cooking", 1)
                xp(300)
                output("obj.cooked_meat")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.burnt_meat")
            column(COL_LOW, 128)
            column(COL_HIGH, 512)
        }
        row("dbrow.cooking_rat_meat") {
            production {
                input("obj.raw_rat_meat")
                statReq("stat.cooking", 1)
                xp(300)
                output("obj.cooked_meat")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.burnt_meat")
            column(COL_LOW, 128)
            column(COL_HIGH, 512)
        }
        row("dbrow.cooking_rabbit") {
            production {
                input("obj.raw_rabbit")
                statReq("stat.cooking", 1)
                xp(300)
                output("obj.cooked_rabbit")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.burnt_meat")
            column(COL_LOW, 128)
            column(COL_HIGH, 512)
        }
        row("dbrow.cooking_cave_eel") {
            production {
                input("obj.raw_cave_eel")
                statReq("stat.cooking", 38)
                xp(1150)
                output("obj.cave_eel")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_cave_eel")
            column(COL_LOW, 38)
            column(COL_HIGH, 332)
        }
        row("dbrow.cooking_lava_eel") {
            production {
                input("obj.raw_lava_eel")
                statReq("stat.cooking", 53)
                xp(300)
                output("obj.lava_eel")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_eel")
            column(COL_LOW, 256)
            column(COL_HIGH, 256)
        }
        row("dbrow.cooking_karambwan") {
            production {
                input("obj.tbwt_raw_karambwan")
                statReq("stat.cooking", 30)
                xp(1900)
                output("obj.tbwt_cooked_karambwan")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.tbwt_burnt_karambwan")
            column(COL_LOW, 70)
            column(COL_HIGH, 255)
        }
        row("dbrow.cooking_bread") {
            production {
                input("obj.bread_dough")
                statReq("stat.cooking", 1)
                xp(400)
                output("obj.bread")
                category("Bread")
            }
            columnRSCM(COL_BURNT, "obj.burnt_bread")
            column(COL_LOW, 118)
            column(COL_HIGH, 492)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_redberry_pie") {
            production {
                input("obj.uncooked_redberry_pie")
                statReq("stat.cooking", 10)
                xp(780)
                output("obj.redberry_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 98)
            column(COL_HIGH, 452)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_meat_pie") {
            production {
                input("obj.uncooked_meat_pie")
                statReq("stat.cooking", 20)
                xp(1100)
                output("obj.meat_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 78)
            column(COL_HIGH, 412)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_apple_pie") {
            production {
                input("obj.uncooked_apple_pie")
                statReq("stat.cooking", 30)
                xp(1300)
                output("obj.apple_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 58)
            column(COL_HIGH, 372)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_pizza") {
            production {
                input("obj.uncooked_pizza")
                statReq("stat.cooking", 35)
                xp(1430)
                output("obj.plain_pizza")
                category("Pizza")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pizza")
            column(COL_LOW, 48)
            column(COL_HIGH, 352)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_mud_pie") {
            production {
                input("obj.uncooked_mud_pie")
                statReq("stat.cooking", 29)
                xp(1280)
                output("obj.mud_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 58)
            column(COL_HIGH, 372)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_garden_pie") {
            production {
                input("obj.uncooked_garden_pie")
                statReq("stat.cooking", 34)
                xp(1380)
                output("obj.garden_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 48)
            column(COL_HIGH, 352)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_fish_pie") {
            production {
                input("obj.uncooked_fish_pie")
                statReq("stat.cooking", 47)
                xp(1640)
                output("obj.fish_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 38)
            column(COL_HIGH, 332)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_botanical_pie") {
            production {
                input("obj.uncooked_botanical_pie")
                statReq("stat.cooking", 52)
                xp(1800)
                output("obj.botanical_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 20)
            column(COL_HIGH, 300)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_mushroom_pie") {
            production {
                input("obj.uncooked_mushroom_pie")
                statReq("stat.cooking", 60)
                xp(2000)
                output("obj.mushroom_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 17)
            column(COL_HIGH, 285)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_admiral_pie") {
            production {
                input("obj.uncooked_admiral_pie")
                statReq("stat.cooking", 70)
                xp(2100)
                output("obj.admiral_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 15)
            column(COL_HIGH, 270)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_dragonfruit_pie") {
            production {
                input("obj.uncooked_dragonfruit_pie")
                statReq("stat.cooking", 73)
                xp(2100)
                output("obj.dragonfruit_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 8)
            column(COL_HIGH, 250)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_wild_pie") {
            production {
                input("obj.uncooked_wild_pie")
                statReq("stat.cooking", 85)
                xp(2400)
                output("obj.wild_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 1)
            column(COL_HIGH, 222)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_summer_pie") {
            production {
                input("obj.uncooked_summer_pie")
                statReq("stat.cooking", 95)
                xp(2600)
                output("obj.summer_pie")
                category("Pie")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pie")
            column(COL_LOW, 1)
            column(COL_HIGH, 212)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_stew") {
            production {
                input("obj.uncooked_stew")
                statReq("stat.cooking", 25)
                xp(1170)
                output("obj.stew")
                category("Stew")
            }
            columnRSCM(COL_BURNT, "obj.burnt_stew")
            column(COL_LOW, 68)
            column(COL_HIGH, 392)
        }
        row("dbrow.cooking_paddlefish") {
            production {
                input("obj.gauntlet_raw_food")
                statReq("stat.cooking", 1)
                xp(150)
                output("obj.gauntlet_food")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.gauntlet_burnt_food")
            column(COL_LOW, 155)
            column(COL_HIGH, 368)
            column(COL_COOK_TICKS, 1)
        }
        row("dbrow.cooking_cake") {
            production {
                input("obj.uncooked_cake")
                statReq("stat.cooking", 40)
                xp(1800)
                output("obj.cake")
                category("Cake")
            }
            columnRSCM(COL_BURNT, "obj.burnt_cake")
            column(COL_LOW, 38)
            column(COL_HIGH, 332)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_giant_krill") {
            production {
                input("obj.raw_giant_krill")
                statReq("stat.cooking", 69)
                xp(1775)
                output("obj.giant_krill")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_giant_krill")
            column(COL_LOW, -229)
            column(COL_HIGH, 242)
            column(COL_LOW_RANGE, -239)
            column(COL_HIGH_RANGE, 252)
        }
        row("dbrow.cooking_jumbo_squid") {
            production {
                input("obj.raw_jumbo_squid")
                statReq("stat.cooking", 71)
                xp(1800)
                output("obj.jumbo_squid")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_jumbo_squid")
            column(COL_LOW, -260)
            column(COL_HIGH, 230)
            column(COL_LOW_RANGE, -260)
            column(COL_HIGH_RANGE, 250)
        }
        row("dbrow.cooking_haddock") {
            production {
                input("obj.raw_haddock")
                statReq("stat.cooking", 73)
                xp(1800)
                output("obj.haddock")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_haddock")
            column(COL_LOW, -330)
            column(COL_HIGH, 237)
            column(COL_LOW_RANGE, -320)
            column(COL_HIGH_RANGE, 247)
        }
        row("dbrow.cooking_yellowfin") {
            production {
                input("obj.raw_yellowfin")
                statReq("stat.cooking", 79)
                xp(2000)
                output("obj.yellowfin")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_yellowfin")
            column(COL_LOW, -505)
            column(COL_HIGH, 232)
            column(COL_LOW_RANGE, -495)
            column(COL_HIGH_RANGE, 242)
        }
        row("dbrow.cooking_halibut") {
            production {
                input("obj.raw_halibut")
                statReq("stat.cooking", 83)
                xp(2125)
                output("obj.halibut")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_halibut")
            column(COL_LOW, -694)
            column(COL_HIGH, 227)
            column(COL_LOW_RANGE, -684)
            column(COL_HIGH_RANGE, 237)
        }
        row("dbrow.cooking_bluefin") {
            production {
                input("obj.raw_bluefin")
                statReq("stat.cooking", 87)
                xp(2150)
                output("obj.bluefin")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_bluefin")
            column(COL_LOW, -1007)
            column(COL_HIGH, 222)
            column(COL_LOW_RANGE, -997)
            column(COL_HIGH_RANGE, 232)
        }
        row("dbrow.cooking_marlin") {
            production {
                input("obj.raw_marlin")
                statReq("stat.cooking", 91)
                xp(2250)
                output("obj.marlin")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_marlin")
            column(COL_LOW, -1626)
            column(COL_HIGH, 217)
            column(COL_LOW_RANGE, -1616)
            column(COL_HIGH_RANGE, 227)
        }
        row("dbrow.cooking_rainbow_fish") {
            production {
                input("obj.hunting_raw_fish_special")
                statReq("stat.cooking", 35)
                xp(1100)
                output("obj.hunting_fish_special")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.hunting_raw_fish_special_burnt")
            column(COL_LOW, 56)
            column(COL_HIGH, 370)
        }
        row("dbrow.cooking_slimy_eel") {
            production {
                input("obj.mort_slimey_eel")
                statReq("stat.cooking", 28)
                xp(950)
                output("obj.mort_slimey_eel_cooked")
                category("Fish")
            }
            columnRSCM(COL_BURNT, "obj.burnt_eel")
            column(COL_LOW, 63)
            column(COL_HIGH, 382)
        }
        row("dbrow.cooking_roast_bird_meat") {
            production {
                input("obj.spit_skewered_bird_meat")
                statReq("stat.cooking", 11)
                xp(625)
                output("obj.spit_roasted_bird_meat")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.spit_burned_bird_meat")
            column(COL_LOW, 155)
            column(COL_HIGH, 255)
            column(COL_SURFACE, SURFACE_FIRE_ONLY)
        }
        row("dbrow.cooking_roast_rabbit") {
            production {
                input("obj.spit_skewered_rabbit_meat")
                statReq("stat.cooking", 16)
                xp(725)
                output("obj.spit_roasted_rabbit_meat")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.spit_burned_rabbit_meat")
            column(COL_LOW, 160)
            column(COL_HIGH, 255)
            column(COL_SURFACE, SURFACE_FIRE_ONLY)
        }
        row("dbrow.cooking_roast_beast_meat") {
            production {
                input("obj.spit_skewered_beast_meat")
                statReq("stat.cooking", 21)
                xp(825)
                output("obj.spit_roasted_beast_meat")
                category("Meat")
            }
            columnRSCM(COL_BURNT, "obj.spit_burned_beast_meat")
            column(COL_LOW, 180)
            column(COL_HIGH, 255)
            column(COL_SURFACE, SURFACE_FIRE_ONLY)
        }
        row("dbrow.cooking_sweetcorn") {
            production {
                input("obj.sweetcorn")
                statReq("stat.cooking", 28)
                xp(1040)
                output("obj.sweetcorn_cooked")
                category("Vegetable")
            }
            columnRSCM(COL_BURNT, "obj.sweetcorn_burnt")
            column(COL_LOW, 78)
            column(COL_HIGH, 412)
        }
        row("dbrow.cooking_baked_potato") {
            production {
                input("obj.potato")
                statReq("stat.cooking", 7)
                xp(150)
                output("obj.potato_baked")
                category("Vegetable")
            }
            columnRSCM(COL_BURNT, "obj.potato_burnt")
            column(COL_LOW, 108)
            column(COL_HIGH, 472)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_scrambled_egg") {
            production {
                input("obj.bowl_egg_raw")
                statReq("stat.cooking", 13)
                xp(500)
                output("obj.bowl_egg_scrambled")
                category("Bowl")
            }
            columnRSCM(COL_BURNT, "obj.bowl_egg_burnt")
            column(COL_LOW, 90)
            column(COL_HIGH, 438)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
        row("dbrow.cooking_fried_onions") {
            production {
                input("obj.bowl_onion")
                statReq("stat.cooking", 42)
                xp(600)
                output("obj.bowl_onion_fried")
                category("Bowl")
            }
            columnRSCM(COL_BURNT, "obj.bowl_onion_burnt")
            column(COL_LOW, 36)
            column(COL_HIGH, 322)
        }
        row("dbrow.cooking_fried_mushrooms") {
            production {
                input("obj.bowl_mushroom_raw")
                statReq("stat.cooking", 46)
                xp(600)
                output("obj.bowl_mushroom_fried")
                category("Bowl")
            }
            columnRSCM(COL_BURNT, "obj.bowl_mushroom_burnt")
            column(COL_LOW, 16)
            column(COL_HIGH, 282)
        }
        row("dbrow.cooking_curry") {
            production {
                input("obj.uncooked_curry")
                statReq("stat.cooking", 60)
                xp(2800)
                output("obj.curry")
                category("Stew")
            }
            columnRSCM(COL_BURNT, "obj.burnt_curry")
            column(COL_LOW, 38)
            column(COL_HIGH, 332)
        }
        row("dbrow.cooking_pitta_bread") {
            production {
                input("obj.uncooked_pitta_bread")
                statReq("stat.cooking", 58)
                xp(400)
                output("obj.pitta_bread")
                category("Bread")
            }
            columnRSCM(COL_BURNT, "obj.burnt_pitta_bread")
            column(COL_LOW, 118)
            column(COL_HIGH, 492)
            column(COL_SURFACE, SURFACE_RANGE_ONLY)
        }
    }

    fun combinations() = productionTable(
        "dbtable.cooking_combinations",
        serverOnly = true,
        includeCategory = false,
        extraColumns = {
            column("needs_knife", COL_NEEDS_KNIFE, VarType.BOOLEAN)
        },
    ) {
        row("dbrow.cooking_combine_skewer_bird_meat") {
            production {
                input("obj.spit_iron", "obj.spit_raw_bird_meat")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.spit_skewered_bird_meat")
            }
        }
        row("dbrow.cooking_combine_skewer_rabbit") {
            production {
                input("obj.spit_iron", "obj.raw_rabbit")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.spit_skewered_rabbit_meat")
            }
        }
        row("dbrow.cooking_combine_skewer_beast_meat") {
            production {
                input("obj.spit_iron", "obj.spit_raw_beast_meat")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.spit_skewered_beast_meat")
            }
        }
        row("dbrow.cooking_combine_uncooked_egg") {
            production {
                input("obj.bowl_empty", "obj.egg")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_egg_raw")
            }
        }
        row("dbrow.cooking_combine_chopped_onion") {
            production {
                input("obj.bowl_empty", "obj.onion")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_onion")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_sliced_mushrooms") {
            production {
                input("obj.bowl_empty", "obj.bittercap_mushroom")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_mushroom_raw")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_chopped_tomato") {
            production {
                input("obj.bowl_empty", "obj.tomato")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_tomato")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_chopped_garlic") {
            production {
                input("obj.bowl_empty", "obj.garlic")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_garlic")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_chopped_tuna") {
            production {
                input("obj.bowl_empty", "obj.tuna")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_tuna")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_minced_meat") {
            production {
                input("obj.bowl_empty", "obj.cooked_meat")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_carne")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_sweetcorn_bowl") {
            production {
                input("obj.bowl_empty", "obj.sweetcorn_cooked")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_sweetcorn")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_spicy_sauce") {
            production {
                input("obj.bowl_garlic", "obj.gnome_spice")
                statReq("stat.cooking", 1)
                xp(0)
                output("obj.bowl_chilli")
            }
        }
        row("dbrow.cooking_combine_chilli_con_carne_minced") {
            production {
                input("obj.bowl_chilli", "obj.bowl_carne")
                statReq("stat.cooking", 11)
                xp(0)
                output("obj.bowl_chilli+carne")
                output("obj.bowl_empty")
            }
        }
        row("dbrow.cooking_combine_chilli_con_carne_meat") {
            production {
                input("obj.bowl_chilli", "obj.cooked_meat")
                statReq("stat.cooking", 11)
                xp(0)
                output("obj.bowl_chilli+carne")
            }
            column(COL_NEEDS_KNIFE, true)
        }
        row("dbrow.cooking_combine_egg_and_tomato") {
            production {
                input("obj.bowl_egg_scrambled", "obj.tomato")
                statReq("stat.cooking", 23)
                xp(0)
                output("obj.bowl_egg+tomato")
            }
        }
        row("dbrow.cooking_combine_mushroom_and_onion") {
            production {
                input("obj.bowl_mushroom_fried", "obj.bowl_onion_fried")
                statReq("stat.cooking", 57)
                xp(0)
                output("obj.bowl_mushroom+onion")
                output("obj.bowl_empty")
            }
        }
        row("dbrow.cooking_combine_tuna_and_corn") {
            production {
                input("obj.bowl_tuna", "obj.sweetcorn_cooked")
                statReq("stat.cooking", 67)
                xp(0)
                output("obj.bowl_tuna+sweetcorn")
            }
        }
        row("dbrow.cooking_combine_tuna_and_corn_bowl") {
            production {
                input("obj.bowl_tuna", "obj.bowl_sweetcorn")
                statReq("stat.cooking", 67)
                xp(0)
                output("obj.bowl_tuna+sweetcorn")
                output("obj.bowl_empty")
            }
        }
        row("dbrow.cooking_combine_potato_with_butter") {
            production {
                input("obj.potato_baked", "obj.pot_of_butter")
                statReq("stat.cooking", 39)
                xp(400)
                output("obj.potato_butter")
                output("obj.pot_empty")
            }
        }
        row("dbrow.cooking_combine_potato_with_cheese") {
            production {
                input("obj.potato_butter", "obj.cheese")
                statReq("stat.cooking", 47)
                xp(400)
                output("obj.potato_cheese")
            }
        }
        row("dbrow.cooking_combine_chilli_potato") {
            production {
                input("obj.potato_butter", "obj.bowl_chilli+carne")
                statReq("stat.cooking", 41)
                xp(150)
                output("obj.potato_chilli+carne")
                output("obj.bowl_empty")
            }
        }
        row("dbrow.cooking_combine_egg_potato") {
            production {
                input("obj.potato_butter", "obj.bowl_egg+tomato")
                statReq("stat.cooking", 51)
                xp(450)
                output("obj.potato_egg+tomato")
                output("obj.bowl_empty")
            }
        }
        row("dbrow.cooking_combine_mushroom_potato") {
            production {
                input("obj.potato_butter", "obj.bowl_mushroom+onion")
                statReq("stat.cooking", 64)
                xp(550)
                output("obj.potato_mushroom+onion")
                output("obj.bowl_empty")
            }
        }
        row("dbrow.cooking_combine_tuna_potato") {
            production {
                input("obj.potato_butter", "obj.bowl_tuna+sweetcorn")
                statReq("stat.cooking", 68)
                xp(100)
                output("obj.potato_tuna+sweetcorn")
                output("obj.bowl_empty")
            }
        }
    }

    fun churning() = productionTable(
        "dbtable.cooking_churning",
        serverOnly = true,
        includeCategory = false,
        extraColumns = {
            column("ticks", COL_CHURN_TICKS, VarType.INT)
        },
    ) {
        row("dbrow.cooking_churn_cream_from_milk") {
            production {
                input("obj.bucket_milk")
                statReq("stat.cooking", 21)
                xp(180)
                output("obj.pot_of_cream")
                output("obj.bucket_empty")
            }
            column(COL_CHURN_TICKS, 10)
        }
        row("dbrow.cooking_churn_butter_from_milk") {
            production {
                input("obj.bucket_milk")
                statReq("stat.cooking", 38)
                xp(405)
                output("obj.pot_of_butter")
                output("obj.bucket_empty")
            }
            column(COL_CHURN_TICKS, 19)
        }
        row("dbrow.cooking_churn_cheese_from_milk") {
            production {
                input("obj.bucket_milk")
                statReq("stat.cooking", 48)
                xp(640)
                output("obj.cheese")
                output("obj.bucket_empty")
            }
            column(COL_CHURN_TICKS, 26)
        }
        row("dbrow.cooking_churn_butter_from_cream") {
            production {
                input("obj.pot_of_cream")
                statReq("stat.cooking", 38)
                xp(225)
                output("obj.pot_of_butter")
                output("obj.pot_empty")
            }
            column(COL_CHURN_TICKS, 10)
        }
        row("dbrow.cooking_churn_cheese_from_cream") {
            production {
                input("obj.pot_of_cream")
                statReq("stat.cooking", 48)
                xp(460)
                output("obj.cheese")
                output("obj.pot_empty")
            }
            column(COL_CHURN_TICKS, 19)
        }
        row("dbrow.cooking_churn_cheese_from_butter") {
            production {
                input("obj.pot_of_butter")
                statReq("stat.cooking", 48)
                xp(235)
                output("obj.cheese")
                output("obj.pot_empty")
            }
            column(COL_CHURN_TICKS, 10)
        }
    }
}
