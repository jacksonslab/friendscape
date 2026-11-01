package org.rsmod.content.skills.woodcutting.configs

import dev.openrune.ParamReferences.param
import dev.openrune.types.ItemServerType
import dev.openrune.types.enums.EnumTypeMap

object WoodcuttingParams {
    val success_rates = param<EnumTypeMap<ItemServerType, Int>>("woodcutting_axe_success_rates")
    val outfit_bonus = param<Int>("woodcutting_outfit_bonus")
    val rate_axe = param<ItemServerType>("woodcutting_rate_axe")
    val charge_limit = param<Int>("woodcutting_charge_limit")
    val nest_contents = param<EnumTypeMap<ItemServerType, Int>>("woodcutting_nest_contents")
    val nest_chance = param<Int>("woodcutting_nest_chance")
    val social = param<Int>("woodcutting_social")
    val fixed_yields = param<Int>("woodcutting_fixed_yields")
}
