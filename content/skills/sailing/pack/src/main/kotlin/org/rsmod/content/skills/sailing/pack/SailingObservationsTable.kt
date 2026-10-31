package org.rsmod.content.skills.sailing.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.rsmod.map.CoordGrid

object SailingObservationsTable {
    fun observations() = dbTable("dbtable.friendscape_sailing_observations", serverOnly = true) {
        column("unique_id", 0, VarType.INT)
        column("level", 1, VarType.INT)
        column("coords", 2, VarType.COORDGRID)
        column("radius", 3, VarType.INT)

        fun observation(id: Int, level: Int, x: Int, z: Int) {
            row("dbrow.friendscape_sailing_observation_$id") {
                column(0, id)
                column(1, level)
                column(2, CoordGrid(x, z, 0).packed)
                column(3, 3)
            }
        }

        observation(357, 1, 3051, 9388)
        observation(14, 1, 3045, 2995)
        observation(2, 1, 3104, 3181)
        observation(19, 1, 3145, 3134)
        observation(10, 1, 3008, 3049)
        observation(23, 1, 2839, 3210)
        observation(27, 1, 2784, 3401)
        observation(31, 1, 2779, 3265)
        observation(36, 1, 2664, 3219)
        observation(108, 1, 2597, 3027)
        observation(78, 24, 2945, 2878)
        observation(109, 1, 2605, 2867)
        observation(77, 1, 2828, 2785)
        observation(191, 1, 3145, 2734)
        observation(76, 1, 2654, 2682)
        observation(80, 24, 3144, 2854)
        observation(79, 40, 3050, 2656)
        observation(81, 45, 3187, 2430)
        observation(83, 1, 2895, 2326)
        observation(82, 1, 3154, 2548)
        observation(270, 1, 3261, 2506)
        observation(285, 1, 1823, 3046)
        observation(286, 1, 1737, 2907)
        observation(287, 1, 1634, 2883)
        observation(110, 40, 2429, 2852)
        observation(112, 40, 2399, 3027)
        observation(113, 1, 2128, 2983)
        observation(114, 40, 2199, 3036)
        observation(111, 61, 2342, 2768)
        observation(288, 1, 1943, 2737)
        observation(289, 1, 1796, 2664)
        observation(343, 1, 1470, 2980)
        observation(344, 1, 1387, 2840)
        observation(316, 1, 1413, 3394)
        observation(317, 1, 1600, 3310)
        observation(318, 1, 1744, 3405)
        observation(179, 1, 1846, 3769)
        observation(178, 1, 1861, 3528)
        observation(150, 66, 2160, 3155)
        observation(151, 66, 2163, 3318)
        observation(319, 1, 1886, 3266)
        observation(177, 1, 2303, 3700)
        observation(320, 1, 2101, 3702)
        observation(228, 1, 2636, 3707)
        observation(229, 1, 2504, 3612)
        observation(230, 1, 2477, 3862)
        observation(231, 1, 2366, 3890)
        observation(232, 1, 2636, 3874)
        observation(233, 1, 2160, 3901)
        observation(234, 1, 2051, 3913)
        observation(235, 78, 2237, 4064)
        observation(180, 78, 1793, 3978)
        observation(236, 78, 2351, 3904)
        observation(237, 78, 2781, 3987)
        observation(238, 78, 2620, 4059)
        observation(239, 78, 2879, 4078)
        observation(240, 78, 2990, 3971)
    }
}
