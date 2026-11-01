package org.rsmod.content.quest.area.varlamore.twilightspromise

import dev.openrune.types.MesAnimType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.map.CoordGrid

/**
 * Values of `varbit.vmq2`. The cache's multinpcs pin most of them: the twins stand outside the
 * palace up to [FIND_METZLI], Metzli waits in the Fortis temple from [FIND_METZLI] to
 * [MEET_ITZLA], the prince and Servius are in the crypt from [MEET_ITZLA] to [RETURN_TO_TWINS],
 * the twins move inside the palace from [RETURN_TO_TWINS], the knights are around the city up to
 * [KNIGHTS_GATHERED] and at the palace from [LETTER_FOUND] to [JUDGED], and Servius lies injured
 * at [SERVIUS_INJURED].
 */
internal object TpStage {
    const val NOT_STARTED = 0
    const val FIND_METZLI = 4
    const val MEET_ITZLA = 8
    const val RETURN_TO_TWINS = 12
    const val FIND_KNIGHTS = 14
    const val KNIGHTS_GATHERED = 22
    const val SEARCH_HEADQUARTERS = 24
    const val LETTER_FOUND = 26
    const val JUDGED = 30
    const val TALK_TO_REGULUS = 32
    const val FEED_RENU = 34
    const val FLY_TO_TEOMAT = 36
    const val TALK_TO_METZLI = 40
    const val CULTISTS = 42
    const val SERVIUS_INJURED = 46
    const val COMPLETE = 50
}

internal object TpVar {
    const val STAGE = "varbit.vmq2"
    const val FIRST_TRAVEL = "varbit.vmq2_first_travel"
    const val MESSAGE_READ = "varbit.vmq2_message_read"
    const val BAZAAR_KNIGHTS = "varbit.vmq2_bazaar_knights"
    const val COTHON_KNIGHT = "varbit.vmq2_cothon_knight"
    const val PUB_KNIGHTS = "varbit.vmq2_pub_knights"
    const val COLOSSEUM_KNIGHT = "varbit.vmq2_colosseum_knight"
    const val GIVEN_CREST = "varbit.vmq2_given_crest"
    const val DRUNK_KNIGHT = "varbit.vmq2_drunk_knight_vis"
    const val TEOMAT_ITZLA = "varbit.vmq3_itzla_vis"

    const val FIRST_TRAVEL_UNSEEN = 1
    const val FIRST_TRAVEL_PRIMIO = 2
    const val FIRST_TRAVEL_DONE = 3

    const val KNIGHT_UNMET = 0
    const val KNIGHT_ASKED = 1
    const val KNIGHT_READY = 2
    const val KNIGHT_RETURNED = 3

    const val DRUNK_HIDDEN = 0
    const val DRUNK_AT_PUB = 1
    const val DRUNK_FOLLOWING = 2
    const val DRUNK_AT_FOUNTAIN = 3
}

internal object TpNpc {
    const val ENNIUS = "npc.vmq2_ennius_vis"
    const val FURIA = "npc.vmq2_furia_vis"
    const val METZLI = "npc.vmq2_metzli_vis"
    const val ITZLA = "npc.vmq2_itzla_vis"
    const val SERVIUS = "npc.vmq2_servius_vis"
    const val CITIZEN = "npc.vmq2_citizen_vis"
    const val ARRUN = "npc.vmq2_knight_1_vis"
    const val CLAUDIA = "npc.vmq2_knight_2_vis"
    const val NEL = "npc.vmq2_knight_3_vis"
    const val VELAM = "npc.vmq2_knight_4_vis"
    const val AZALI = "npc.vmq2_knight_5_vis"
    const val AZALI_DRUNK = "npc.vmq2_knight_5_drunk"
    const val AZALI_FOLLOWER = "npc.vmq2_knight_5_follower"
    const val MEZAN = "npc.vmq2_knight_6_vis"
    const val MEZAN_COMBAT = "npc.vmq2_knight_6_combat"

    const val REGULUS_VARROCK = "npc.vmq2_quetzal_keeper_1op"
    const val REGULUS_VARROCK_TRAVEL = "npc.vmq2_quetzal_keeper_2op"
    const val REGULUS_FORTIS = "npc.vmq2_quetzal_keeper_fortis"
    const val PRIMIO_VARROCK = "npc.vmq2_quetzal_op"
    const val PRIMIO_FORTIS = "npc.vmq2_quetzal_fortis"

    val CULTISTS =
        listOf(
            "npc.vmq2_cultist_m_1",
            "npc.vmq2_cultist_f_1",
            "npc.vmq2_cultist_m_2",
            "npc.vmq2_cultist_f_2",
            "npc.vmq2_cultist_m_3",
            "npc.vmq2_cultist_f_3",
            "npc.vmq2_cultist_m_1",
            "npc.vmq2_cultist_f_1",
        )

    val RENU =
        listOf(
            "npc.quetzal_child_green",
            "npc.quetzal_child_orange",
            "npc.quetzal_child_blue",
            "npc.quetzal_child_cyan",
            "npc.quetzal_child_green_orange",
        )
}

internal object TpObj {
    const val INVITATION = "obj.vmq2_start_message"
    const val CREST = "obj.vmq2_crest"
    const val LETTER = "obj.vmq2_incriminating_letter"
    const val FEED = "obj.vmq2_quetzal_feed"
    const val AMULET = "obj.vmq2_amulet"
}

internal object TpLoc {
    const val TEMPLE_STAIRS_TOP = "loc.vmq2_temple_stairs_top"
    const val TEMPLE_STAIRS_BOTTOM = "loc.vmq2_temple_stairs_bottom"
    const val TEMPLE_GATE = "loc.vmq2_temple_gate"
    const val TEMPLE_GATE_OPEN = "loc.vmq2_temple_gate_open"
    const val COTHON_CRATE = "loc.vmq2_cothon_crate"
    const val COTHON_CRATES = "loc.vmq2_cothon_crates"
    const val LETTER_CHEST = "loc.vmq2_knight_4_chest"

    val OTHER_CHESTS =
        listOf(
            "loc.vmq2_knight_1_chest",
            "loc.vmq2_knight_2_chest",
            "loc.vmq2_knight_3_chest",
            "loc.vmq2_knight_5_chest",
            "loc.vmq2_knight_6_chest",
        )
}

internal object TpCoords {
    val VARROCK_LANDING = CoordGrid(3281, 3411)
    val FORTIS_LANDING = CoordGrid(1701, 3140)
    val SPARK_CRATE = CoordGrid(1778, 3149)
    val FOUNTAIN = CoordGrid(1758, 3069)
    val TEMPLE_GATE_NORTH = CoordGrid(1698, 9496)
    val TEMPLE_GATE_SOUTH = CoordGrid(1698, 9495)
    val TEMPLE_TOP_SOUTH = CoordGrid(1694, 3084)
    val TEMPLE_TOP_NORTH = CoordGrid(1694, 3088)
    val CRYPT_SOUTH = CoordGrid(1692, 9482)
    val CRYPT_NORTH = CoordGrid(1692, 9494)
    val MEZAN_ARENA = CoordGrid(1804, 9520)
    val TEOMAT_LIBRARY = CoordGrid(1449, 3189)
    val TEOMAT_CULTIST_SPAWNS =
        listOf(
            CoordGrid(1446, 3186),
            CoordGrid(1452, 3186),
            CoordGrid(1445, 3189),
            CoordGrid(1453, 3189),
            CoordGrid(1446, 3192),
            CoordGrid(1452, 3192),
            CoordGrid(1447, 3184),
            CoordGrid(1451, 3184),
        )
}

internal suspend fun Dialogue.ennius(anim: MesAnimType, text: String) =
    chatNpcSpecific("Ennius Tullus", TpNpc.ENNIUS, anim, text)

internal suspend fun Dialogue.furia(anim: MesAnimType, text: String) =
    chatNpcSpecific("Furia Tullus", TpNpc.FURIA, anim, text)

internal suspend fun Dialogue.metzli(anim: MesAnimType, text: String) =
    chatNpcSpecific("Metzli, Teokan of Ranul", TpNpc.METZLI, anim, text)

internal suspend fun Dialogue.itzla(anim: MesAnimType, text: String) =
    chatNpcSpecific("Prince Itzla Arkan", TpNpc.ITZLA, anim, text)

internal suspend fun Dialogue.servius(anim: MesAnimType, text: String) =
    chatNpcSpecific("Servius, Teokan of Ralos", TpNpc.SERVIUS, anim, text)

internal suspend fun Dialogue.regulus(anim: MesAnimType, text: String) =
    chatNpcSpecific("Regulus Cento", TpNpc.REGULUS_FORTIS, anim, text)

internal suspend fun Dialogue.knight(type: String, anim: MesAnimType, text: String) =
    chatNpcSpecific("Knight of Varlamore", type, anim, text)
