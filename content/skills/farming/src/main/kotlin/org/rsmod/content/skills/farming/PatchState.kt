package org.rsmod.content.skills.farming

enum class Health {
    Healthy,
    Diseased,
    Dead,
}

enum class Compost(val obj: String, val xp: Double, val diseaseFactor: Double) {
    None("", 0.0, 1.0),
    Normal("obj.bucket_compost", 18.0, 0.5),
    Super("obj.bucket_supercompost", 26.0, 0.2),
    Ultra("obj.bucket_ultracompost", 36.0, 0.1);

    companion object {
        fun byObj(obj: String): Compost? = entries.firstOrNull { it != None && it.obj == obj }
    }
}

/**
 * One patch, packed into a single varp so it persists with the rest of the character.
 *
 * [raked] counts rake strokes: 0 is fully overgrown and [RAKED] is clear. [lives] is harvest lives
 * for allotments and herbs and the fruit left on a fruit tree. [checked] is set once a tree's
 * health has been checked; [stump] once it has been chopped down.
 */
data class PatchState(
    val cropId: Int = 0,
    val stage: Int = 0,
    val raked: Int = 0,
    val health: Health = Health.Healthy,
    val watered: Boolean = false,
    val compost: Compost = Compost.None,
    val lives: Int = 0,
    val protected: Boolean = false,
    val checked: Boolean = false,
    val stump: Boolean = false,
) {
    val empty: Boolean
        get() = cropId == 0

    val clear: Boolean
        get() = raked >= RAKED

    fun pack(): Int =
        (cropId and 0x7F) or
            (stage.coerceIn(0, 15) shl 7) or
            (raked.coerceIn(0, RAKED) shl 11) or
            (health.ordinal shl 13) or
            (if (watered) 1 shl 15 else 0) or
            (compost.ordinal shl 16) or
            (lives.coerceIn(0, 15) shl 18) or
            (if (protected) 1 shl 22 else 0) or
            (if (checked) 1 shl 23 else 0) or
            (if (stump) 1 shl 24 else 0)

    companion object {
        const val RAKED: Int = 3

        val EMPTY_CLEAR: PatchState = PatchState(raked = RAKED)

        fun unpack(value: Int): PatchState =
            PatchState(
                cropId = value and 0x7F,
                stage = (value ushr 7) and 0xF,
                raked = (value ushr 11) and 0x3,
                health = Health.entries[((value ushr 13) and 0x3).coerceAtMost(2)],
                watered = (value ushr 15) and 1 == 1,
                compost = Compost.entries[(value ushr 16) and 0x3],
                lives = (value ushr 18) and 0xF,
                protected = (value ushr 22) and 1 == 1,
                checked = (value ushr 23) and 1 == 1,
                stump = (value ushr 24) and 1 == 1,
            )
    }
}
