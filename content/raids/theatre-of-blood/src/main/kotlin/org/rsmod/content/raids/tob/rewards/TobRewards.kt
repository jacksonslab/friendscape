package org.rsmod.content.raids.tob.rewards

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.game.entity.Player

internal data class TobRaiderResult(val player: Player, val deaths: Int, val mvpPoints: Int) {
    val points: Int
        get() = TobPoints.personal(deaths, mvpPoints)
}

internal data class TobChestContents(val player: Player, val loot: List<Pair<String, Int>>, val rare: Boolean)

@Singleton
internal class TobRewards
@Inject
constructor(
    private val registry: DropTableRegistry,
    private val random: GameRandom,
    private val pets: PetRewards,
) {
    fun roll(raiders: List<TobRaiderResult>): List<TobChestContents> {
        if (raiders.isEmpty()) return emptyList()
        val teamPoints = raiders.sumOf { it.points }
        val uniqueChance = TobPoints.uniqueChance(teamPoints, raiders.size)
        val recipient =
            if (TobPoints.rollsChance(random, uniqueChance)) {
                TobPoints.pickRecipient(random, raiders.associateWith { it.points })
            } else {
                null
            }
        return raiders.map { raider ->
            val loot = mutableListOf<Pair<String, Int>>()
            val performance = TobPoints.performance(raider.points)
            when {
                raider === recipient -> loot += rollTable(TobRewardKeys.UNIQUE, raider.player, 1.0, rolls = 1)
                raider.points <= 0 -> {
                    loot += CABBAGE to 1
                    loot += MESSAGE to 1
                }
                else -> loot += rollTable(TobRewardKeys.COMMON, raider.player, performance, TobRewardKeys.COMMON_ROLLS)
            }
            if (raider.points > 0) {
                val clueChance = performance * TobPoints.CLUE_NUMERATOR / TobPoints.CLUE_DENOMINATOR
                if (TobPoints.rollsChance(random, clueChance)) loot += ELITE_CLUE to 1
                val petChance = performance / TobPoints.PET_DENOMINATOR
                if (TobPoints.rollsChance(random, petChance)) awardPet(raider.player)
            }
            TobChestContents(raider.player, merge(loot), rare = raider === recipient)
        }
    }

    private fun rollTable(key: String, player: Player, performance: Double, rolls: Int): List<Pair<String, Int>> {
        val table = registry.forLoc(key) ?: return emptyList()
        val drops = mutableListOf<Pair<String, Int>>()
        repeat(rolls) {
            when (val result = table.roll(player, ArgMap()).flatten()) {
                is RollResult.Nothing -> Unit
                is RollResult.Single -> drops += result.result.toLoot(player, performance)
                is RollResult.ListOf -> result.results.forEach { drops += it.toLoot(player, performance) }
            }
        }
        return drops
    }

    private fun DropRollItem.toLoot(player: Player, performance: Double): List<Pair<String, Int>> {
        if (isNothing || !condition(player)) return emptyList()
        val obj = transformObj(player) ?: obj
        return listOf(obj to TobPoints.scaledCount(rollCount(random), performance))
    }

    private fun awardPet(player: Player) {
        if (pets.give(player, PET)) {
            player.mes("<col=ff0000>You have a funny feeling like you're being followed.</col>")
        }
    }

    private fun merge(loot: List<Pair<String, Int>>): List<Pair<String, Int>> =
        loot.groupBy({ it.first }, { it.second }).map { (obj, counts) -> obj to counts.sum() }

    private companion object {
        const val CABBAGE = "obj.cabbage"
        const val MESSAGE = "obj.tob_vampyre_message"
        const val ELITE_CLUE = "obj.trail_elite_emote_exp1"
        const val PET = "obj.verzikpet"
    }
}
