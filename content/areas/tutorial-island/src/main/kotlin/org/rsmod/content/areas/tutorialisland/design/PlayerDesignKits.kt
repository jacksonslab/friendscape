package org.rsmod.content.areas.tutorialisland.design

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants

/**
 * Ordered identkit lists for `player_design`, loaded from cache style DB tables.
 *
 * Appearance kit slot layout (matches [org.rsmod.game.entity.player.Appearance]): `0=hair, 1=jaw,
 * 2=torso, 3=arms, 4=hands, 5=legs, 6=feet`.
 */
@Singleton
class PlayerDesignKits @Inject constructor() {
    private lateinit var hairA: IntArray
    private lateinit var hairB: IntArray
    private lateinit var jawA: IntArray
    private lateinit var jawB: IntArray
    private lateinit var torsoA: IntArray
    private lateinit var torsoB: IntArray
    private lateinit var armsA: IntArray
    private lateinit var armsB: IntArray
    private lateinit var handsA: IntArray
    private lateinit var handsB: IntArray
    private lateinit var legsA: IntArray
    private lateinit var legsB: IntArray
    private lateinit var feetA: IntArray
    private lateinit var feetB: IntArray

    fun ensureLoaded() {
        if (this::hairA.isInitialized) {
            return
        }
        hairA = org.rsmod.api.table.HairStylesRow.all().map { it.playerKitIdTypeA }.filter { it >= 0 }.distinct().toIntArray()
        hairB = org.rsmod.api.table.HairStylesRow.all().map { it.playerKitIdTypeB }.filter { it >= 0 }.distinct().toIntArray()
        jawA = org.rsmod.api.table.FacialHairStylesRow.all().map { it.playerKitIdTypeA }.filter { it >= 0 }.distinct().toIntArray()
        jawB = org.rsmod.api.table.FacialHairStylesRow.all().map { it.playerKitIdTypeB }.filter { it >= 0 }.distinct().toIntArray()
        torsoA = org.rsmod.api.table.TorsoStylesRow.all().map { it.playerKitIdTypeA }.filter { it >= 0 }.distinct().toIntArray()
        torsoB = org.rsmod.api.table.TorsoStylesRow.all().map { it.playerKitIdTypeB }.filter { it >= 0 }.distinct().toIntArray()
        armsA = org.rsmod.api.table.SleeveStylesRow.all().map { it.playerKitIdTypeA }.filter { it >= 0 }.distinct().toIntArray()
        armsB = org.rsmod.api.table.SleeveStylesRow.all().map { it.playerKitIdTypeB }.filter { it >= 0 }.distinct().toIntArray()
        handsA = org.rsmod.api.table.HandStylesRow.all().map { it.playerKitIdTypeA }.filter { it >= 0 }.distinct().toIntArray()
        handsB = org.rsmod.api.table.HandStylesRow.all().map { it.playerKitIdTypeB }.filter { it >= 0 }.distinct().toIntArray()
        legsA = org.rsmod.api.table.LeggingStylesRow.all().map { it.playerKitIdTypeA }.filter { it >= 0 }.distinct().toIntArray()
        legsB = org.rsmod.api.table.LeggingStylesRow.all().map { it.playerKitIdTypeB }.filter { it >= 0 }.distinct().toIntArray()
        feetA = org.rsmod.api.table.ShoeStylesRow.all().map { it.playerKitIdTypeA }.filter { it >= 0 }.distinct().toIntArray()
        feetB = org.rsmod.api.table.ShoeStylesRow.all().map { it.playerKitIdTypeB }.filter { it >= 0 }.distinct().toIntArray()
    }

    fun kitsFor(slot: DesignKitSlot, bodyType: Int): IntArray {
        ensureLoaded()
        val bodyA = bodyType == constants.bodytype_a
        return when (slot) {
            DesignKitSlot.Hair -> if (bodyA) hairA else hairB
            DesignKitSlot.Jaw -> if (bodyA) jawA else jawB
            DesignKitSlot.Torso -> if (bodyA) torsoA else torsoB
            DesignKitSlot.Arms -> if (bodyA) armsA else armsB
            DesignKitSlot.Hands -> if (bodyA) handsA else handsB
            DesignKitSlot.Legs -> if (bodyA) legsA else legsB
            DesignKitSlot.Feet -> if (bodyA) feetA else feetB
        }
    }

    fun defaultKits(bodyType: Int): IntArray {
        ensureLoaded()
        return IntArray(DesignKitSlot.entries.size) { i ->
            val kits = kitsFor(DesignKitSlot.entries[i], bodyType)
            checkNotNull(kits.firstOrNull()) { "Missing cache appearance styles for slot $i" }
        }
    }
}

enum class DesignKitSlot(val appearanceIndex: Int) {
    Hair(0),
    Jaw(1),
    Torso(2),
    Arms(3),
    Hands(4),
    Legs(5),
    Feet(6),
}

enum class DesignColourSlot(val appearanceIndex: Int, val maxInclusive: Int) {
    Hair(0, 29),
    Torso(1, 27),
    Legs(2, 27),
    Feet(3, 5),
    Skin(4, 7),
}
