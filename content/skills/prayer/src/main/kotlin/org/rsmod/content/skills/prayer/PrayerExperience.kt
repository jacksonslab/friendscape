package org.rsmod.content.skills.prayer

import org.rsmod.api.table.prayer.SkillPrayerRow

val SkillPrayerRow.baseXp: Double
    get() = expTenths / 10.0
