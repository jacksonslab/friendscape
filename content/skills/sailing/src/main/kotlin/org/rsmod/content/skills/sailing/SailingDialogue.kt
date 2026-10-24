package org.rsmod.content.skills.sailing

import org.rsmod.api.player.protect.ProtectedAccess

const val STAT_SAILING: String = "stat.sailing"

/**
 * Lets the player pick one of [values] from a chat menu, three at a time with a "More..." option
 * when there are more than four. Returns `null` if they cancel.
 */
internal suspend fun <T> ProtectedAccess.choose(labels: List<String>, values: List<T>, title: String = "Select an Option"): T? {
    require(labels.size == values.size)
    if (values.isEmpty()) {
        return null
    }
    var page = 0
    while (true) {
        val pageSize = if (values.size <= MAX_OPTIONS) MAX_OPTIONS else MAX_OPTIONS - 1
        val start = page * pageSize
        val end = minOf(start + pageSize, values.size)
        val options = (start until end).map { labels[it] to Pick<T>(values[it]) }.toMutableList()
        if (values.size > MAX_OPTIONS) {
            options += "More..." to Pick(null, more = true)
        } else if (options.size < MAX_OPTIONS) {
            options += "Cancel" to Pick(null)
        }
        val pick =
            when (options.size) {
                1 -> options[0].second
                2 -> choice2(options[0].first, options[0].second, options[1].first, options[1].second, title)
                3 ->
                    choice3(
                        options[0].first, options[0].second,
                        options[1].first, options[1].second,
                        options[2].first, options[2].second,
                        title,
                    )
                else ->
                    choice4(
                        options[0].first, options[0].second,
                        options[1].first, options[1].second,
                        options[2].first, options[2].second,
                        options[3].first, options[3].second,
                        title,
                    )
            }
        if (!pick.more) {
            return pick.value
        }
        page = if (end >= values.size) 0 else page + 1
    }
}

private class Pick<T>(val value: T?, val more: Boolean = false)

private const val MAX_OPTIONS = 4
