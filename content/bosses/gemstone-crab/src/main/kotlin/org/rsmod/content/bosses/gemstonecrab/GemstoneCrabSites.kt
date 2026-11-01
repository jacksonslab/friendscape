package org.rsmod.content.bosses.gemstonecrab

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

@Singleton
public class GemstoneCrabSites
@Inject
constructor(
    npcRepo: NpcRepository,
    mapClock: MapClock,
    playerList: PlayerList,
    hpBar: BossHpBarScript,
) {
    public val varlamore: GemstoneCrabManager =
        GemstoneCrabManager(npcRepo, mapClock, playerList, hpBar, VARLAMORE)

    public val swamp: GemstoneCrabManager =
        GemstoneCrabManager(npcRepo, mapClock, playerList, hpBar, LUMBRIDGE_SWAMP)

    public val all: List<GemstoneCrabManager> = listOf(varlamore, swamp)

    public fun owning(remains: Npc): GemstoneCrabManager? = all.firstOrNull { it.remainsNpc === remains }

    private companion object {
        val VARLAMORE =
            CrabSite(
                crab = "npc.gemstone_crab",
                remains = "npc.gemstone_crab_remains",
                spots =
                    listOf(
                        Spot(
                            crab = CoordGrid(1271, 3171, 0),
                            cave = CoordGrid(1278, 3167, 0),
                            caveExit = CoordGrid(1277, 3169, 0),
                        ),
                        Spot(
                            crab = CoordGrid(1351, 3110, 0),
                            cave = CoordGrid(1350, 3123, 0),
                            caveExit = CoordGrid(1351, 3122, 0),
                        ),
                        Spot(
                            crab = CoordGrid(1237, 3042, 0),
                            cave = CoordGrid(1245, 3035, 0),
                            caveExit = CoordGrid(1247, 3038, 0),
                        ),
                    ),
                areas =
                    listOf(
                        "area.gemstone_crab_spot_1",
                        "area.gemstone_crab_spot_2",
                        "area.gemstone_crab_spot_3",
                    ),
            )

        val LUMBRIDGE_SWAMP =
            CrabSite(
                crab = "npc.swamp_gemstone_crab",
                remains = "npc.swamp_gemstone_crab_remains",
                spots = listOf(Spot(crab = CoordGrid(3196, 3188, 0))),
                areas = listOf("area.gemstone_crab_swamp"),
                spawnAnimation = "seq.devito_boss_idle",
                attackAnimation = "seq.devito_boss_attack",
                kickAnimation = "seq.devito_boss_kick",
                deathAnimation = "seq.devito_boss_death",
                disintegrateAnimation = "seq.devito_boss_disintegrate",
                crabEffects = false,
                preserveDeathFacing = true,
            )
    }
}
