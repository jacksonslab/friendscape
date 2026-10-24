@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

typealias tutorial_interfaces = TutorialInterfaces

typealias tutorial_components = TutorialComponents

object TutorialInterfaces {
    /** Live OSRS Tutorial Island chrome (`614`). */
    val TUTORIAL_OVERLAY = "interface.tutorial_overlay"

    /** Live character creator modal (`679`). */
    val PLAYER_DESIGN = "interface.player_design"

    /** Live Past Experience / familiarity modal (`929`). */
    val TUTORIAL_PLAYER_EXPERIENCE = "interface.tutorial_player_experience"

    /** Chat-layer text host opened with [org.rsmod.content.areas.tutorialisland.progression.TutorialClientScripts.mesOverlay]. */
    val MESOVERLAY = "interface.mesoverlay"

    val MAKEOVER = "interface.makeover"
    val MAKEOVER_MAGE = "interface.makeover_mage"
    val IRONMAN_SETUP = "interface.ironman_setup"
    val CR_TUTORIAL = "interface.cr_tutorial"
}

object TutorialComponents {
    val TUTORIAL_NOCLICK = "component.tutorial_overlay:noclick"
    val HINT_LAYER = "component.tutorial_overlay:hint_layer"
    val HINT_TEXT_1 = "component.tutorial_overlay:hint_text_1"
    val HINT_TEXT_2 = "component.tutorial_overlay:hint_text_2"
    val HINT_TEXT_3 = "component.tutorial_overlay:hint_text_3"
    val HINT_TEXT_4 = "component.tutorial_overlay:hint_text_4"
    val PROGRESSBAR = "component.tutorial_overlay:progressbar"
    val PROGRESSBAR_CONTAINER = "component.tutorial_overlay:progressbar_container"

    val PLAYER_DESIGN_CONFIRM = "component.player_design:confirm"
    val PLAYER_DESIGN_PRONOUNS = "component.player_design:pronouns_buttons"

    val EXPERIENCE_CONTENT = "component.tutorial_player_experience:content"
    val EXPERIENCE_BUTTON_NEW = "component.tutorial_player_experience:button_new"
    val EXPERIENCE_BUTTON_RETURNING = "component.tutorial_player_experience:button_returning"
    val EXPERIENCE_BUTTON_EXPERIENCED = "component.tutorial_player_experience:button_experienced"

    val HEAD_LEFT = "component.player_design:head_left"
    val HEAD_RIGHT = "component.player_design:head_right"
    val JAW_LEFT = "component.player_design:jaw_left"
    val JAW_RIGHT = "component.player_design:jaw_right"
    val TORSO_LEFT = "component.player_design:torso_left"
    val TORSO_RIGHT = "component.player_design:torso_right"
    val ARMS_LEFT = "component.player_design:arms_left"
    val ARMS_RIGHT = "component.player_design:arms_right"
    val HANDS_LEFT = "component.player_design:hands_left"
    val HANDS_RIGHT = "component.player_design:hands_right"
    val LEGS_LEFT = "component.player_design:legs_left"
    val LEGS_RIGHT = "component.player_design:legs_right"
    val FEET_LEFT = "component.player_design:feet_left"
    val FEET_RIGHT = "component.player_design:feet_right"

    val HAIR_LEFT = "component.player_design:hair_left"
    val HAIR_RIGHT = "component.player_design:hair_right"
    val TORSO_COL_LEFT = "component.player_design:torso_col_left"
    val TORSO_COL_RIGHT = "component.player_design:torso_col_right"
    val LEGS_COL_LEFT = "component.player_design:legs_col_left"
    val LEGS_COL_RIGHT = "component.player_design:legs_col_right"
    val FEET_COL_LEFT = "component.player_design:feet_col_left"
    val FEET_COL_RIGHT = "component.player_design:feet_col_right"
    val SKIN_LEFT = "component.player_design:skin_left"
    val SKIN_RIGHT = "component.player_design:skin_right"

    val GENDER_MALE = "component.player_design:gender_male"
    val GENDER_FEMALE = "component.player_design:gender_female"

    val SPELL_WIND_STRIKE = "component.magic_spellbook:wind_strike"
    val SPELL_HOME_TELEPORT = "component.magic_spellbook:teleport_home_standard"
    val WORN_EQUIPMENT_STATS = "component.wornitems:equipment"

    val FIXED_STONE0 = "component.toplevel:stone0"
    val FIXED_STONE1 = "component.toplevel:stone1"
    val FIXED_STONE2 = "component.toplevel:stone2"
    val FIXED_STONE3 = "component.toplevel:stone3"
    val FIXED_STONE4 = "component.toplevel:stone4"
    val FIXED_STONE5 = "component.toplevel:stone5"
    val FIXED_STONE6 = "component.toplevel:stone6"
    val FIXED_STONE8 = "component.toplevel:stone8"

    val PRE_EOC_STONE0 = "component.toplevel_pre_eoc:stone0"
    val PRE_EOC_STONE1 = "component.toplevel_pre_eoc:stone1"
    val PRE_EOC_STONE2 = "component.toplevel_pre_eoc:stone2"
    val PRE_EOC_STONE3 = "component.toplevel_pre_eoc:stone3"
    val PRE_EOC_STONE4 = "component.toplevel_pre_eoc:stone4"
    val PRE_EOC_STONE5 = "component.toplevel_pre_eoc:stone5"
    val PRE_EOC_STONE6 = "component.toplevel_pre_eoc:stone6"
    val PRE_EOC_STONE8 = "component.toplevel_pre_eoc:stone8"
}
