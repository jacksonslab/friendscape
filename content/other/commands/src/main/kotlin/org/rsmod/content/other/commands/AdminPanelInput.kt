package org.rsmod.content.other.commands

import org.rsmod.map.CoordGrid

internal object AdminPanelInput {
    fun coordinates(input: String): CoordGrid? {
        val parts = input.trim().split(Regex("[\\s,]+"))
        if (parts.size != 3) return null
        val x = parts[0].toIntOrNull() ?: return null
        val z = parts[1].toIntOrNull() ?: return null
        val plane = parts[2].toIntOrNull() ?: return null
        if (x !in 0..16383 || z !in 0..16383 || plane !in 0..3) return null
        return CoordGrid(x, z, plane)
    }

    fun plain(text: String): String = text.filter { it >= ' ' && it != '<' && it != '>' }
}
