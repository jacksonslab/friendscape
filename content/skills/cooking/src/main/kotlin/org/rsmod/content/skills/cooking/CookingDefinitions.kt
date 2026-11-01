package org.rsmod.content.skills.cooking

import org.rsmod.api.table.cooking.CookingAlesRow
import org.rsmod.api.table.cooking.CookingFoodsRow

val CookingFoodsRow.raw get() = input
val CookingFoodsRow.cooked get() = output
val CookingFoodsRow.level get() = statReq.first().t1

/** `CookingFoodsRow.surface` values; must match `Cooking.SURFACE_*` in or-cache. */
const val SURFACE_FIRE_ONLY = 1
const val SURFACE_RANGE_ONLY = 2

val CookingAlesRow.ingredient get() = input
val CookingAlesRow.ingredientCount get() = inputAmount
val CookingAlesRow.result get() = output.first()
val CookingAlesRow.matureResult get() = output[1]
val CookingAlesRow.level get() = statReq.first().t1
