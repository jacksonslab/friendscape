package org.rsmod.content.generic.locs.doors

import dev.openrune.types.ObjectServerType
import kotlin.math.abs

/**
 * Pairs every untagged door/gate loc with its other state so doors that were never given a
 * `content.*_door` group in `loc.toml` still open. A closed door has op1 "Open", an opened one op1
 * "Close", and both share a display name. The partner is the gameval-name variant
 * (`x` ↔ `x_open`, `x_closed` ↔ `x_open`, ...) when one exists, otherwise the nearest id within
 * [MAX_ID_GAP], which is how Jagex numbers most door pairs.
 */
class DoorPairs(types: Collection<ObjectServerType>) {
    val closed: Set<Int>
    val nextStage: Map<Int, Int>
    private val symbols = HashMap<Int, String>()

    init {
        val closedDoors = types.filter { it.isDoor() && it.op1().equals(OPEN, ignoreCase = true) }
        val openDoors = types.filter { it.isDoor() && it.op1().equals(CLOSE, ignoreCase = true) }
        val openByName = openDoors.groupBy { it.name.lowercase() }
        val closedByName = closedDoors.groupBy { it.name.lowercase() }

        val pairs = HashMap<Int, Int>()
        for (door in closedDoors) {
            val partner = partner(door, openByName[door.name.lowercase()].orEmpty()) ?: continue
            pairs[door.id] = partner.id
        }
        for (door in openDoors) {
            val partner = partner(door, closedByName[door.name.lowercase()].orEmpty()) ?: continue
            pairs.putIfAbsent(door.id, partner.id)
        }
        closed = closedDoors.mapTo(HashSet()) { it.id }
        nextStage = pairs
    }

    private fun partner(door: ObjectServerType, candidates: List<ObjectServerType>): ObjectServerType? {
        if (candidates.isEmpty()) {
            return null
        }
        val name = door.symbol()
        val variants = nameVariants(name)
        candidates.firstOrNull { it.symbol() in variants }?.let { return it }
        return candidates
            .filter { abs(it.id - door.id) <= MAX_ID_GAP }
            .minWithOrNull(
                compareBy<ObjectServerType> { -commonPrefix(name, it.symbol()) }
                    .thenBy { abs(it.id - door.id) }
            )
    }

    private fun ObjectServerType.isDoor(): Boolean = DOOR_NAME.containsMatchIn(name)

    private fun ObjectServerType.op1(): String? = actions.getOpOrNull(0)

    private fun ObjectServerType.symbol(): String =
        symbols.getOrPut(id) { runCatching { internalName }.getOrDefault("") }

    companion object {
        private const val OPEN = "Open"
        private const val CLOSE = "Close"
        private const val MAX_ID_GAP = 6
        private val DOOR_NAME = Regex("\\b(door|doors|doorway|gate|gates)\\b", RegexOption.IGNORE_CASE)

        fun nameVariants(name: String): Set<String> {
            if (name.isEmpty()) {
                return emptySet()
            }
            val variants = linkedSetOf<String>()
            variants += name + "_open"
            variants += name + "open"
            variants += name.removeSuffix("_open")
            variants += name.removeSuffix("open")
            variants += name.replace("closed", "open")
            variants += name.replace("open", "closed")
            variants += name.replace("_open", "")
            variants += name.replaceFirst(Regex("(door|gate)"), "$1_open")
            variants += name.replaceFirst(Regex("(door|gate)"), "$1open")
            variants += name.replaceFirst(Regex("(door|gate)_?open"), "$1")
            variants -= name
            return variants
        }

        private fun commonPrefix(a: String, b: String): Int {
            val max = minOf(a.length, b.length)
            var i = 0
            while (i < max && a[i] == b[i]) {
                i++
            }
            return i
        }
    }
}
