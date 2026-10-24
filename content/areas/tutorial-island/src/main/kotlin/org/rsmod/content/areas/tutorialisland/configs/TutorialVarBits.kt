@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

typealias tutorial_varbits = TutorialVarBits

object TutorialVarBits {
    val ADVENTUREPATH_PLAYER_PARTICIPATING = "varbit.adventurepath_player_participating"

    /** Drives the depressed Body Type A/B buttons on `player_design`. */
    val PLAYER_DESIGN_BODYTYPE = "varbit.player_design_bodytype"

    /** Drives pronoun radio selection; packed on `settings_tracking`. */
    val SETTINGS_TRANSMIT_PRONOUNS = "varbit.settings_transmit_pronouns"

    /** Four bits on varp 1021; the client reads it to flash a toplevel side icon. */
    val FLASHSIDE = "varbit.flashside"
}
