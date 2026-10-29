package org.rsmod.game.entity.npc

public object GlobalNpcNames {
    @Volatile private var names: Map<Int, String> = emptyMap()

    public fun get(type: Int): String? = names[type]

    public fun replace(overrides: Map<Int, String>) {
        names = overrides.toMap()
    }
}
