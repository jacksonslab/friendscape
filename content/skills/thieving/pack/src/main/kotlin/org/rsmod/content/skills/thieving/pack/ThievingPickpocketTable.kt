package org.rsmod.content.skills.thieving.pack

import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Pickpocket targets. `low`/`high` are the level 1 and level 99 success rolls out of 256 (wiki
 * "Skilling success chart", post-rebalance values). When `pouch_weight` is set the coin pouch is
 * one weighted outcome of the loot roll; without it the pouch comes with every success. `always`
 * items come with every success and `tertiary` items roll 1 in N on their own.
 */
object ThievingPickpocketTable {
    const val NPCS = 0
    const val OP = 1
    const val LEVEL = 2
    const val XP = 3
    const val LOW = 4
    const val HIGH = 5
    const val STUN_MIN = 6
    const val STUN_MAX = 7
    const val POUCH = 8
    const val POUCH_MIN = 9
    const val POUCH_MAX = 10
    const val POUCH_WEIGHT = 11
    const val LOOT = 12
    const val ALWAYS = 13
    const val TERTIARY = 14
    const val CAUGHT = 15
    const val POCKET_NAME = 16
    const val LOWERCASE = 17

    private const val WHAT = "What do you think you're doing?"

    private class Pouch(val obj: String, val min: Int, val max: Int = min, val weight: Int = 0)

    fun pickpockets() =
        dbTable("dbtable.friendscape_thieving_pickpocket", serverOnly = true) {
            column("npcs", NPCS, VarType.NPC)
            column("op", OP, VarType.INT)
            column("level", LEVEL, VarType.INT)
            column("xp", XP, VarType.INT)
            column("low", LOW, VarType.INT)
            column("high", HIGH, VarType.INT)
            column("stun_min", STUN_MIN, VarType.INT)
            column("stun_max", STUN_MAX, VarType.INT)
            column("pouch", POUCH, VarType.OBJ)
            column("pouch_min", POUCH_MIN, VarType.INT)
            column("pouch_max", POUCH_MAX, VarType.INT)
            column("pouch_weight", POUCH_WEIGHT, VarType.INT)
            column("loot", LOOT, VarType.OBJ, VarType.INT, VarType.INT, VarType.INT)
            column("always", ALWAYS, VarType.OBJ, VarType.INT)
            column("tertiary", TERTIARY, VarType.OBJ, VarType.INT)
            column("caught", CAUGHT, VarType.STRING)
            column("pocket_name", POCKET_NAME, VarType.STRING)
            column("lowercase", LOWERCASE, VarType.BOOLEAN)

            fun target(
                name: String,
                npcs: List<String>,
                level: Int,
                xp10: Int,
                low: Int,
                high: Int,
                stun: IntRange,
                pouch: Pouch? = null,
                loot: List<Drop> = emptyList(),
                always: List<Pair<String, Int>> = emptyList(),
                tertiary: List<Pair<String, Int>> = emptyList(),
                op: Int = 3,
                caught: String = WHAT,
                pocketName: String? = null,
                lowercase: Boolean = true,
            ) =
                row("dbrow.thieving_pickpocket_$name") {
                    npcColumn(NPCS, npcs)
                    column(OP, op)
                    column(LEVEL, level)
                    column(XP, xp10)
                    column(LOW, low)
                    column(HIGH, high)
                    column(STUN_MIN, stun.first)
                    column(STUN_MAX, stun.last)
                    if (pouch != null) {
                        columnRSCM(POUCH, "obj.pickpocket_coin_pouch_${pouch.obj}")
                        column(POUCH_MIN, pouch.min)
                        column(POUCH_MAX, pouch.max)
                        if (pouch.weight > 0) column(POUCH_WEIGHT, pouch.weight)
                    }
                    dropColumn(LOOT, loot)
                    if (always.isNotEmpty()) {
                        column(ALWAYS, always.flatMap { listOf(ConstantProvider.getMapping("obj.${it.first}"), it.second) })
                    }
                    if (tertiary.isNotEmpty()) {
                        column(TERTIARY, tertiary.flatMap { listOf(ConstantProvider.getMapping("obj.${it.first}"), it.second) })
                    }
                    column(CAUGHT, caught)
                    pocketName?.let { column(POCKET_NAME, it) }
                    column(LOWERCASE, lowercase)
                }

            target(
                "citizen",
                listOf(
                    "man", "man2", "man3", "man4", "man5", "varrock_man1", "al_kharid_man",
                    "falador_man1", "falador_man2", "falador_man3", "ardougnian_male1", "karamja_man",
                    "man_indoor", "zeah_man", "zeah_man2", "zeah_man3", "shayzien_man_1", "shayzien_man_2",
                    "woman", "woman2", "woman3", "woman4", "varrock_woman1", "falador_woman2",
                    "ardougnian_female1", "zeah_woman", "zeah_woman2", "zeah_woman3", "zeah_woman_outside",
                    "shayzien_woman_1", "shayzien_woman_2",
                ) + listOf("poor", "normal", "rich").flatMap { tier ->
                    (1..5).flatMap { listOf("varlamore_citizen_${tier}_m_$it", "varlamore_citizen_${tier}_f_$it") }
                },
                level = 1, xp10 = 80, low = 180, high = 240, stun = 1..1,
                pouch = Pouch("citizen", 3),
            )
            target(
                "farmer",
                listOf(
                    "farmer1", "farmer2", "farmer3", "farmer1_f", "farmer2_f", "farmer3_f", "farmer4",
                    "kastori_farmer_m_1", "kastori_farmer_m_2", "kastori_farmer_f_1", "kastori_farmer_f_2",
                    "tal_teklan_farmer",
                ) + (1..4).flatMap { listOf("varlamore_farmer_m_$it", "varlamore_farmer_f_$it") },
                level = 10, xp10 = 145, low = 150, high = 240, stun = 1..1,
                pouch = Pouch("farmer", 9, weight = 123),
                loot = listOf(drop("potato_seed", 5)),
            )
            target(
                "ham",
                listOf("favour_male_ham_civilian", "favour_female_ham_civilian"),
                level = 15, xp10 = 222, low = 135, high = 239, stun = 1..3, op = 1,
                pouch = Pouch("ham", 1, 21, weight = 187),
                loot =
                    listOf("ham_boots", "ham_cloak", "ham_gloves", "ham_hood", "ham_badge", "ham_robe", "ham_shirt")
                        .map { drop(it, 11) } +
                        listOf(
                            drop("bronze_arrow", 33, 1, 13),
                            drop("bronze_axe", 33),
                            drop("bronze_dagger", 33),
                            drop("bronze_pickaxe", 33),
                            drop("iron_axe", 33),
                            drop("iron_dagger", 33),
                            drop("iron_pickaxe", 33),
                            drop("leather_armour", 33),
                            drop("steel_arrow", 22, 1, 13),
                            drop("steel_axe", 22),
                            drop("steel_dagger", 22),
                            drop("steel_pickaxe", 22),
                            drop("digsitebuttons", 44),
                            drop("feather", 33, 1, 7),
                            drop("knife", 22),
                            drop("logs", 33),
                            drop("needle", 22),
                            drop("raw_anchovies", 22),
                            drop("raw_chicken", 22),
                            drop("thread", 33, 1, 10),
                            drop("tinderbox", 22),
                            drop("uncut_opal", 22),
                            drop("coal", 22),
                            drop("cow_hide", 33),
                            drop("digsitearmour1", 44),
                            drop("unidentified_guam", 12),
                            drop("unidentified_marentill", 6),
                            drop("unidentified_tarromin", 4),
                            drop("iron_ore", 22),
                            drop("digsitesword", 44),
                            drop("uncut_jade", 22),
                        ),
                pocketName = "the H.A.M. Member",
                lowercase = false,
            )
            target(
                "warrior",
                listOf(
                    "al_kharid_warrior", "warrior_woman", "warrior_woman_variant01", "warrior_woman_variant02",
                    "warrior_man", "warrior_man_variant01", "warrior_man_variant02",
                ),
                level = 25, xp10 = 260, low = 100, high = 240, stun = 2..2,
                pouch = Pouch("warrior", 18),
                pocketName = "the warrior",
            )
            target(
                "rogue",
                listOf("rogue"),
                level = 32, xp10 = 365, low = 75, high = 240, stun = 2..2,
                pouch = Pouch("rogue", 25, 40, weight = 123),
                loot =
                    listOf(
                        drop("airrune", 9, 8),
                        drop("jug_wine", 6),
                        drop("lockpick", 5),
                        drop("iron_dagger_p", 1),
                    ),
            )
            target(
                "cave_goblin",
                (1..9).flatMap { listOf("dorgesh_male_$it", "dorgesh_female_$it") },
                level = 36, xp10 = 400, low = 70, high = 240, stun = 1..1,
                pouch = Pouch("cavegoblin", 10, 50, weight = 7),
                loot =
                    listOf(
                        drop("dorgesh_bat_shish"),
                        drop("dorgesh_crispy_froglegs"),
                        drop("dorgesh_wall_beast_fingers"),
                        drop("dorgesh_frog_burger"),
                        drop("dorgesh_frog_spawn_gumbo"),
                        drop("dorgesh_green_gloop_soup"),
                        drop("bullseye_lantern_unlit"),
                        drop("dorgesh_wire"),
                        drop("iron_ore", 1, 1, 4),
                        drop("oil_lantern_unlit"),
                        drop("swamp_tar"),
                        drop("tinderbox"),
                        drop("torch_unlit"),
                    ),
            )
            target(
                "master_farmer",
                listOf(
                    "master_farmer_1", "master_farmer_2", "master_farmer_1_f", "master_farmer_2_f",
                    "martin_the_master_farmer", "kastori_master_farmer_m_1", "kastori_master_farmer_m_2",
                    "kastori_master_farmer_f_1", "kastori_master_farmer_f_2",
                ) + (1..4).flatMap { listOf("varlamore_master_farmer_m_$it", "varlamore_master_farmer_f_$it") },
                level = 38, xp10 = 430, low = 90, high = 240, stun = 3..3,
                loot = MASTER_FARMER,
                caught = "Cor blimey mate, what are ye doing in me pockets?",
            )
            target(
                "guard",
                listOf(
                    "hos_town_guard_01", "hos_town_guard_02", "hos_town_guard_03", "hos_town_guard_04",
                    "jail_guard_1", "jail_guard_2", "jail_guard_3", "jail_guard_4", "jail_guard_5",
                    "fai_varrock_guard", "fai_varrock_guard_captain", "guard1", "guard1_variant01", "guard1_f",
                    "guard1_f_variant01", "fai_falador_guard1", "fai_falador_guard2", "fai_falador_guard3",
                    "fai_falador_guard4", "fai_falador_guard5", "fai_falador_guard6", "fai_falador_guard1_variant01",
                    "fai_falador_guard1_f", "fai_falador_guard1_variant02", "fai_falador_guard2_f",
                    "fai_falador_guard3_f", "fai_falador_guard4_f", "falador_doric_area_guard", "ardougne_guard",
                    "ardougne_guard_variant01", "ardougne_guard_f", "ardougne_guard_f_variant01",
                    "fai_varrock_guard02", "fai_varrock_guard02_variant01", "fai_varrock_guard02_variant02",
                    "fai_varrock_guard02_f", "fai_varrock_guard02_f_variant01", "fai_varrock_guard02_f_variant02",
                    "fai_varrock_guard_captain02", "port_roberts_guard_m1", "port_roberts_guard_m2",
                    "port_roberts_guard_f1", "port_roberts_guard_f2",
                ) +
                    listOf("m1", "m2", "m3", "m4", "f1", "f2", "f3", "f4").map { "kourend_guard_$it" } +
                    listOf("varlamore", "aldarin").flatMap { town ->
                        (1..5).flatMap { listOf("${town}_guard_m_$it", "${town}_guard_f_$it") }
                    } +
                    listOf("auburnvale", "tlati").flatMap { town ->
                        (1..4).flatMap { listOf("${town}_guard_m_$it", "${town}_guard_f_$it") }
                    },
                level = 40, xp10 = 468, low = 50, high = 240, stun = 2..2,
                pouch = Pouch("guard", 30),
            )
            target(
                "fremennik",
                listOf(
                    "viking_man", "viking_man2", "viking_man3", "viking_man4", "viking_man5",
                    "viking_woman_indoors", "viking_woman2", "viking_woman3", "viking_woman4",
                ),
                level = 45, xp10 = 650, low = 50, high = 240, stun = 2..2,
                pouch = Pouch("fremennik", 40),
                lowercase = false,
            )
            target(
                "wealthy_citizen",
                listOf("a", "b", "c", "d").map { "varlamore_wealthy_citizen_$it" },
                level = 50, xp10 = 960, low = 35, high = 200, stun = 3..3, op = 1,
                pouch = Pouch("varlamore_wealthy", 85, weight = 79),
                loot = listOf(drop("varlamore_thieving_house_key", 5)),
            )
            target(
                "desert_bandit",
                listOf("fourdiamonds_sword_bandit_1", "fourdiamonds_sword_bandit_free"),
                level = 53, xp10 = 794, low = 50, high = 240, stun = 3..3,
                pouch = Pouch("desertbandit", 30, weight = 5),
                loot = listOf(drop("1doseantipoison"), drop("lockpick")),
            )
            target(
                "knight",
                listOf(
                    "knight_of_ardougne", "knight_of_ardougne2", "knight_of_ardougne_f",
                    "knight_of_ardougne_west_vis", "knight_of_ardougne_f_west_vis",
                ) + (1..3).flatMap { listOf("varlamore_knight_m_$it", "varlamore_knight_f_$it") },
                level = 55, xp10 = 843, low = 50, high = 240, stun = 3..3,
                pouch = Pouch("knight", 50),
                pocketName = "the knight",
            )
            target(
                "pirate",
                (1..5).map { "pirate_pickpocketable_$it" },
                level = 60, xp10 = 720, low = 45, high = 220, stun = 3..3,
                pouch = Pouch("pirate", 20, weight = 170),
                loot =
                    listOf(
                        drop("bronze_cannonball", 14),
                        drop("iron_cannonball", 10),
                        drop("mcannonball", 5),
                        drop("coral_elkhorn_frag", 1),
                    ),
            )
            target(
                "watchman",
                listOf("yanille_watchman"),
                level = 65, xp10 = 1375, low = 15, high = 160, stun = 3..3,
                pouch = Pouch("watchman", 60),
                always = listOf("bread" to 1),
            )
            target(
                "paladin",
                listOf(
                    "paladin", "paladin2", "paladin_west_vis", "paladin_west_f_vis", "paladin_variant01",
                    "paladin_variant02", "paladin_f", "paladin_f_variant01",
                ),
                level = 70, xp10 = 1318, low = 40, high = 170, stun = 3..3,
                pouch = Pouch("paladin", 80),
                always = listOf("chaosrune" to 2),
            )
            target(
                "gnome",
                listOf(
                    "gnome", "browclothedgnome", "darkskinned_gnome", "gnomefemale", "gnomefemale_dskinned",
                    "gnomechildgreen", "gnomechildblue", "gnomechild",
                ),
                level = 75, xp10 = 1333, low = 43, high = 175, stun = 1..1,
                pouch = Pouch("gnome", 300, weight = 30),
                loot =
                    listOf(
                        drop("arrow_shaft", 56, 2, 4),
                        drop("swamp_toad", 24),
                        drop("gold_ore", 8),
                        drop("earthrune", 5),
                        drop("king_worm", 3),
                        drop("fire_orb", 2),
                    ),
            )
            target(
                "hero",
                listOf("hero", "hero_variant01", "hero_f"),
                level = 80, xp10 = 1633, low = 39, high = 160, stun = 3..3,
                pouch = Pouch("hero", 200, 300, weight = 105),
                loot =
                    listOf(
                        drop("deathrune", 8, 2),
                        drop("jug_wine", 6),
                        drop("bloodrune", 5),
                        drop("diamond", 1),
                        drop("fire_orb", 2),
                        drop("gold_ore", 1),
                    ),
            )
            target(
                "vyre",
                listOf(
                    "alek_constantine", "caninelle_draynar", "carnivus_belamorta", "crimsonette_van_marr",
                    "diphylla_bechstein", "draconis_sanguine", "episcula_helsing", "grigor_rasputin",
                    "haemas_lamescus", "lasenna_rasputin", "misdrievus_shadum", "mort_nightshade",
                    "mortina_daubenton", "nakasa_jovkai", "natalidae_shadum", "noctillion_lugosi",
                    "pipistrelle_draynar", "remus_kaninus", "valentin_rasputin", "valentina_diaemus",
                    "vallessia_dracyula", "vallessia_von_pitt", "vampyressa_van_von", "vampyrus_diaemus",
                    "violetta_sanguine", "vlad_bechstein", "vlad_diaemus", "von_van_von", "vonnetta_varnis",
                    "vormar_vakan",
                ),
                level = 82, xp10 = 3069, low = 8, high = 128, stun = 5..5,
                pouch = Pouch("vyre", 230, 315, weight = 109),
                loot =
                    listOf(
                        drop("deathrune", 8, 2),
                        drop("bloodrune", 2, 4),
                        drop("blood_pint", 6),
                        drop("uncut_ruby", 5),
                        drop("diamond", 1),
                        drop("cooked_mystery_meat", 1),
                    ),
                tertiary = listOf("blood_shard" to 5000),
                lowercase = false,
            )
            target(
                "elf",
                listOf(
                    "anaire", "aranwe", "aredhel", "caranthir", "celebrian", "celegorm", "cirdan", "curufin",
                    "earwen", "edrahil", "elenwe", "elladan", "enel", "enelye", "enerdhil", "erestor", "feanor",
                    "findis", "finduilas", "fingolfin", "fingon", "galathil", "gelmir", "glorfindel", "guilin",
                    "hendor", "idril", "imin", "iminye", "indis", "ingwe", "ingwion", "lenwe", "lindir",
                    "maeglin", "mahtan", "miriel", "mithrellas", "nellas", "nerdanel", "nimloth", "oropher",
                    "orophin", "saeros", "salgant", "tatie", "thingol", "turgon", "vaire",
                ).map { "prif_citizen_$it" } +
                    listOf(
                        "mourning_town_elf_1", "mourning_town_elf_3", "mourning_town_elf_4",
                        "mourning_town_elf_5_vis",
                    ),
                level = 85, xp10 = 3533, low = 6, high = 100, stun = 5..5,
                pouch = Pouch("elf", 280, 350, weight = 105),
                loot =
                    listOf(
                        drop("deathrune", 8, 2),
                        drop("naturerune", 5, 3),
                        drop("jug_wine", 6),
                        drop("diamond", 1),
                        drop("fire_orb", 2),
                        drop("gold_ore", 1),
                    ),
                tertiary = listOf("prif_crystal_shard" to 35, "prif_teleport_seed" to 1024),
                lowercase = false,
            )
            target(
                "tzhaar_hur",
                (1..6).map { "tzhaar_hur_city$it" },
                level = 90, xp10 = 1034, low = -200, high = 200, stun = 4..4,
                loot =
                    listOf(
                        drop("tzhaar_token", 182, 3, 7),
                        drop("uncut_sapphire", 5),
                        drop("uncut_emerald", 4),
                        drop("uncut_ruby", 3),
                        drop("uncut_diamond", 1),
                    ),
                pocketName = "the TzHaar-Hur",
                lowercase = false,
            )
        }

    private val MASTER_FARMER =
        listOf(
            drop("potato_seed", 17700, 1, 4),
            drop("onion_seed", 13280, 1, 3),
            drop("cabbage_seed", 6944, 1, 3),
            drop("tomato_seed", 6369, 1, 2),
            drop("sweetcorn_seed", 2212, 1, 2),
            drop("strawberry_seed", 1106),
            drop("watermelon_seed", 529),
            drop("snape_grass_seed", 385),
            drop("barley_seed", 5556, 1, 12),
            drop("hammerstone_hop_seed", 5556, 1, 9),
            drop("asgarnian_hop_seed", 4184, 1, 6),
            drop("jute_seed", 4149, 1, 9),
            drop("yanillian_hop_seed", 2770, 1, 6),
            drop("krandorian_hop_seed", 1385, 1, 6),
            drop("wildblood_hop_seed", 704, 1, 3),
            drop("marigold_seed", 4587),
            drop("nasturtium_seed", 3040),
            drop("rosemary_seed", 1965),
            drop("woad_seed", 1451),
            drop("limpwurt_seed", 1159),
            drop("redberry_bush_seed", 3876),
            drop("cadavaberry_bush_seed", 2717),
            drop("dwellberry_bush_seed", 1942),
            drop("jangerberry_bush_seed", 775),
            drop("whiteberry_bush_seed", 282),
            drop("poisonivy_bush_seed", 107),
            drop("mushroom_seed", 203),
            drop("belladonna_seed", 122),
            drop("cactus_seed", 81),
            drop("seaweed_seed", 53),
            drop("potato_cactus_seed", 41),
            drop("guam_seed", 1713),
            drop("marrentill_seed", 1046),
            drop("tarromin_seed", 714),
            drop("harralander_seed", 485),
            drop("ranarr_seed", 372),
            drop("toadflax_seed", 226),
            drop("irit_seed", 154),
            drop("avantoe_seed", 106),
            drop("kwuarm_seed", 72),
            drop("snapdragon_seed", 54),
            drop("cadantine_seed", 34),
            drop("lantadyme_seed", 24),
            drop("dwarf_weed_seed", 14),
            drop("torstol_seed", 11),
        )
}
