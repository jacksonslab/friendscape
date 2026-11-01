package org.rsmod.content.skills.mining.drops

import dtx.rs.RSWeightedTable
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

object CamdozaalDropTables {
    val crusher: RSWeightedTable<Player, DropRollItem> =
        rsPlayerWeightedTable(total = 4200) {
            name("Barronite deposit")
            3895 weight "obj.camdozaal_barronite_shard" count 6..8
            210 weight "obj.camdozaal_barronite_shard" count 15..35
            21 weight "obj.barronite_mace_1" count 1
            14 weight "obj.imcando_hammer_broken" count 1
            12 weight "obj.camdozaal_relic_1" count 1
            12 weight "obj.camdozaal_relic_2" count 1
            12 weight "obj.camdozaal_relic_3" count 1
            12 weight "obj.camdozaal_relic_4" count 1
            12 weight "obj.camdozaal_relic_5" count 1
        }
}
