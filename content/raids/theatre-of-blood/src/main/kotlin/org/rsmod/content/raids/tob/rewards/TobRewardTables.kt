package org.rsmod.content.raids.tob.rewards

import dtx.rs.RSDropTable
import dtx.rs.locs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

internal object TobRewardKeys {
    const val COMMON = "tob_reward_common"
    const val UNIQUE = "tob_reward_unique"
    const val COMMON_ROLLS = 3
}

@field:RegisterDropTable
@JvmField
val tobCommonRewardTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Theatre of Blood Common Rewards",
        locs = locs(TobRewardKeys.COMMON),
        mainTable =
            rsPlayerWeightedTable(total = 30) {
                name("Theatre of Blood Common Rewards")
                2 weight "obj.cert_vial_blood" count 45..60
                1 weight "obj.deathrune" count 500..600
                1 weight "obj.bloodrune" count 500..600
                1 weight "obj.swamp_tar" count 500..600
                1 weight "obj.cert_coal" count 500..600
                1 weight "obj.cert_gold_ore" count 300..360
                1 weight "obj.cert_molten_glass" count 200..240
                1 weight "obj.cert_adamantite_ore" count 130..156
                1 weight "obj.cert_runite_ore" count 60..72
                1 weight "obj.cert_wine_of_zamorak" count 50..60
                1 weight "obj.cert_cactus_potato" count 50..60
                1 weight "obj.cert_unidentified_cadantine" count 50..60
                1 weight "obj.cert_unidentified_avantoe" count 40..48
                1 weight "obj.cert_unidentified_toadflax" count 37..44
                1 weight "obj.cert_unidentified_kwuarm" count 36..43
                1 weight "obj.cert_unidentified_irit" count 34..40
                1 weight "obj.cert_unidentified_ranarr" count 30..36
                1 weight "obj.cert_unidentified_snapdragon" count 27..32
                1 weight "obj.cert_unidentified_lantadyme" count 26..31
                1 weight "obj.cert_unidentified_dwarf_weed" count 24..28
                1 weight "obj.cert_unidentified_torstol" count 20..24
                1 weight "obj.cert_battlestaff" count 15..18
                1 weight "obj.cert_rune_battleaxe" count 4
                1 weight "obj.cert_rune_platebody" count 4
                1 weight "obj.cert_rune_chainbody" count 4
                1 weight "obj.palm_tree_seed" count 3
                1 weight "obj.yew_seed" count 3
                1 weight "obj.magic_tree_seed" count 3
                1 weight "obj.mahogany_seed" count 10..12
            },
    )

@field:RegisterDropTable
@JvmField
val tobUniqueRewardTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Theatre of Blood Unique Rewards",
        locs = locs(TobRewardKeys.UNIQUE),
        mainTable =
            rsPlayerWeightedTable(total = 19) {
                name("Theatre of Blood Unique Rewards")
                8 weight "obj.infernal_defender_hilt" count 1
                2 weight "obj.ghrazi_rapier" count 1
                2 weight "obj.sanguinesti_staff_uncharged" count 1
                2 weight "obj.justiciar_faceguard" count 1
                2 weight "obj.justiciar_chestguard" count 1
                2 weight "obj.justiciar_leg_guards" count 1
                1 weight "obj.scythe_of_vitur_uncharged" count 1
            },
    )
