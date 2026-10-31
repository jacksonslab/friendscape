package org.rsmod.content.skills.prayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.table.prayer.PrayerBlessedBoneRow
import org.rsmod.api.table.prayer.PrayerEctofuntusBonemealRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.prayer.blessed.BoneShardEvents
import org.rsmod.content.skills.prayer.blessed.LibationBowlEvents
import org.rsmod.content.skills.prayer.blessed.SunfireWineEvents
import org.rsmod.content.skills.prayer.ecto.EctoGrinderEvents
import org.rsmod.content.skills.prayer.ecto.EctoWorshipEvents
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

class PrayerTrainingDeps @Inject constructor(val held: HeldInteractions, val heldU: HeldUInteractions, val locU: LocUInteractions, val locs: LocRepository, val areas: AreaIndex)

private val TILE = CoordGrid(0, 50, 50, 20, 20)
private fun item(symbol: String): ItemServerType = checkNotNull(ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ)))

@Execution(ExecutionMode.SAME_THREAD)
class PrayerTrainingTest {
    @Test
    fun GameTestState.`burying preserves fractional XP for ordinary bat and zogre bones`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, PrayerBuryEvents::class) { deps ->
            prepare()
            for ((bone, fineXp) in listOf("obj.bones" to 45, "obj.bat_bones" to 53, "obj.zogre_bones" to 225)) {
                player.inv[0] = InvObj(bone)
                val before = xp()
                bury(deps)
                advance(ticks = 4)
                assertEquals(0, player.inv.count(bone))
                assertEquals(fineXp, xp() - before)
            }
        }

    @Test
    fun GameTestState.`repeated same-tick bury clicks consume one bone and grant one reward`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, PrayerBuryEvents::class) { deps ->
            prepare()
            player.inv[0] = InvObj("obj.bones")
            player.inv[1] = InvObj("obj.bones")
            val before = xp()
            player.withProtectedAccess {
                deps.held.interact(this, inv, 0, HeldOp.Op1)
                deps.held.interact(this, inv, 0, HeldOp.Op1)
            }
            advance(ticks = 4)
            assertEquals(1, player.inv.count("obj.bones"))
            assertEquals(45, xp() - before)
        }

    @Test
    fun GameTestState.`bones removed during the burial delay grant no XP`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, PrayerBuryEvents::class) { deps ->
            prepare()
            player.inv[0] = InvObj("obj.bones")
            val before = xp()
            bury(deps)
            player.clearInv()
            advance(ticks = 4)
            assertEquals(before, xp())
        }

    @Test
    fun GameTestState.`superior dragon bones require level 70 Prayer`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, PrayerBuryEvents::class) { deps ->
            prepare()
            player.setBaseLevel("stat.prayer", 69)
            player.inv[0] = InvObj("obj.dragon_bones_superior")
            val before = xp()
            bury(deps)
            advance(ticks = 4)
            assertEquals(1, player.inv.count("obj.dragon_bones_superior"))
            assertEquals(before, xp())
            player.setBaseLevel("stat.prayer", 70)
            val eligibleXp = xp()
            bury(deps)
            advance(ticks = 4)
            assertEquals(0, player.inv.count("obj.dragon_bones_superior"))
            assertEquals(1500, xp() - eligibleXp)
        }

    @Test
    fun GameTestState.`burial honors personal and realm XP rates exactly once`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, PrayerBuryEvents::class) { deps ->
            prepare()
            player.xpRate = 2.0
            player.globalXpRate = 3.0
            player.inv[0] = InvObj("obj.bones")
            val before = xp()
            bury(deps)
            advance(ticks = 4)
            assertEquals(270, xp() - before)
        }

    @Test
    fun GameTestState.`gilded altar bonuses follow zero one and two lit burners`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, GildedAltarEvents::class) { deps ->
            prepare()
            val altar = placeMapLoc(TILE.translateX(1), "loc.poh_altar_guthix_7")
            for ((burners, fineXp) in listOf(0 to 1800, 1 to 2160, 2 to 2520)) {
                if (burners > 0) placeMapLoc(TILE.translate(burners, 2), "loc.poh_torch_5_lit")
                player.inv[0] = InvObj("obj.dragon_bones")
                val before = xp()
                useOn(deps, altar, 0)
                advance(ticks = 3)
                assertEquals(0, player.inv.count("obj.dragon_bones"))
                assertEquals(fineXp, xp() - before)
            }
        }

    @Test
    fun GameTestState.`offering cannot reward bones removed before the sacrifice tick`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, GildedAltarEvents::class) { deps ->
            prepare()
            val altar = placeMapLoc(TILE.translateX(1), "loc.poh_altar_saradomin_7")
            player.inv[0] = InvObj("obj.dragon_bones")
            val before = xp()
            useOn(deps, altar, 0)
            player.clearInv()
            advance(ticks = 3)
            assertEquals(before, xp())
        }

    @Test
    fun GameTestState.`Chaos altar saving and consuming both grant one offering reward per action`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, GildedAltarEvents::class) { deps ->
            prepare()
            val wild = CoordGrid(2949, 3820, 0)
            allocZoneCollision(wild)
            player.teleport(wild)
            deps.areas.registerAll(wild, listOf("area.chaos_temple".asRSCM(RSCMType.AREA).toShort()).iterator())
            val altar = placeMapLoc(wild.translateX(-1), "loc.chaosaltar")
            player.inv[0] = InvObj("obj.dragon_bones")
            val before = xp()
            random.next = 99
            useOn(deps, altar, 0)
            advance(ticks = 2)
            assertEquals(1, player.inv.count("obj.dragon_bones"))
            assertEquals(2520, xp() - before)
            random.next = 0
            advance(ticks = 4)
            assertEquals(0, player.inv.count("obj.dragon_bones"))
            assertEquals(5040, xp() - before)
        }

    @Test
    fun GameTestState.`libation offers one hundred shards for two Prayer points`() =
        runGameTest(LibationBowlEvents::class) {
            prepare()
            player.setBaseLevel("stat.prayer", 30)
            player.setCurrentLevel("stat.prayer", 2)
            player.inv[0] = InvObj("obj.blessed_bone_shard", 200)
            VarPlayerIntMapSetter.set(player, "varbit.varlamore_prayer_winequant", 400)
            val bowl = placeMapLoc(TILE.translateX(1), "loc.varlamore_libation_bowl_full")
            val before = xp()
            player.opLoc1(bowl)
            advance(ticks = 3)
            assertEquals(100, player.inv.count("obj.blessed_bone_shard"))
            assertEquals(0, player.prayerLvl)
            assertEquals(300, player.vars["varbit.varlamore_prayer_winequant"])
            assertEquals(5000, xp() - before)
        }

    @Test
    fun GameTestState.`bone shard stack overflow rolls back the blessed bone and grants no Crafting XP`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, BoneShardEvents::class) { deps ->
            prepare()
            val row = PrayerBlessedBoneRow.all().first { it.input.internalName == "obj.bones" }
            player.inv[0] = InvObj(row.output.internalName)
            player.inv[1] = InvObj("obj.chisel")
            player.inv[2] = InvObj("obj.blessed_bone_shard", Int.MAX_VALUE)
            val crafting = player.statMap.getFineXP("stat.crafting")
            bury(deps)
            advance(ticks = 2)
            assertEquals(1, player.inv.count(row.output))
            assertEquals(Int.MAX_VALUE, player.inv.count("obj.blessed_bone_shard"))
            assertEquals(crafting, player.statMap.getFineXP("stat.crafting"))
            player.inv[2] = null
            bury(deps)
            advance(ticks = 2)
            assertEquals(0, player.inv.count(row.output))
            assertEquals(row.shardCount, player.inv.count("obj.blessed_bone_shard"))
            assertEquals(1, player.inv.count("obj.chisel"))
            assertEquals(crafting + 50, player.statMap.getFineXP("stat.crafting"))
        }

    @Test
    fun GameTestState.`sunfire wine conversion with a full inventory consumes exactly one recipe`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, SunfireWineEvents::class) { deps ->
            prepare()
            player.inv[0] = InvObj("obj.jug_wine")
            player.inv[1] = InvObj("obj.sunfiresplinter", 3)
            player.inv[2] = InvObj("obj.pestle_and_mortar")
            for (slot in 3..27) player.inv[slot] = InvObj("obj.logs")
            player.withProtectedAccess {
                repeat(2) {
                    deps.heldU.interact(this, inv, item("obj.jug_wine"), 0, item("obj.sunfiresplinter"), 1)
                }
            }
            advance(ticks = 5)
            assertEquals(0, player.inv.count("obj.jug_wine"))
            assertEquals(1, player.inv.count("obj.jug_sunfire_wine"))
            assertEquals(1, player.inv.count("obj.sunfiresplinter"))
            assertEquals(1, player.inv.count("obj.pestle_and_mortar"))
            assertEquals(25, player.inv.count("obj.logs"))
        }

    @Test
    fun GameTestState.`sunfire wine without a pestle retains both ingredients`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, SunfireWineEvents::class) { deps ->
            prepare()
            player.inv[0] = InvObj("obj.jug_wine")
            player.inv[1] = InvObj("obj.sunfiresplinter", 2)
            player.withProtectedAccess {
                deps.heldU.interact(this, inv, item("obj.jug_wine"), 0, item("obj.sunfiresplinter"), 1)
            }
            advance(ticks = 5)
            assertEquals(1, player.inv.count("obj.jug_wine"))
            assertEquals(2, player.inv.count("obj.sunfiresplinter"))
            assertEquals(0, player.inv.count("obj.jug_sunfire_wine"))
        }

    @Test
    fun GameTestState.`Ectofuntus exchanges ingredients for empty containers even with a full inventory`() =
        runGameTest(EctoWorshipEvents::class) {
            prepare()
            player.inv[0] = InvObj("obj.bucket_ectoplasm")
            player.inv[1] = InvObj("obj.pot_bonemeal_bat")
            for (slot in 2..27) player.inv[slot] = InvObj("obj.logs")
            val shrine = placeMapLoc(TILE.translateX(1), "loc.ahoy_ectofuntus")
            val before = xp()
            player.opLoc1(shrine)
            advance(ticks = 3)
            assertEquals(0, player.inv.count("obj.bucket_ectoplasm"))
            assertEquals(0, player.inv.count("obj.pot_bonemeal_bat"))
            assertEquals(1, player.inv.count("obj.bucket_empty"))
            assertEquals(1, player.inv.count("obj.pot_empty"))
            assertEquals(26, player.inv.count("obj.logs"))
            assertEquals(212, xp() - before)
            assertEquals(5, player.vars["varbit.ecto_tokens"])
        }

    @Test
    fun GameTestState.`incense lighting consumes one herb keeps the tinderbox and expires back to unlit`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, PrayerIncenseScript::class) { deps ->
            prepare()
            player.setBaseLevel("stat.firemaking", 30)
            player.setCurrentLevel("stat.firemaking", 30)
            player.inv[0] = InvObj("obj.marentill")
            player.inv[1] = InvObj("obj.tinderbox")
            val lamp = placeMapLoc(TILE.translateX(1), "loc.poh_torch_5")
            random.next = 0
            player.opLoc1(lamp)
            advance(ticks = 5)
            assertEquals(0, player.inv.count("obj.marentill"))
            assertEquals(1, player.inv.count("obj.tinderbox"))
            assertTrue(deps.locs.findLoc(lamp.coords, "loc.poh_torch_5_lit"))
            advance(ticks = 250)
            assertTrue(deps.locs.findLoc(lamp.coords, "loc.poh_torch_5"))
            assertFalse(deps.locs.findLoc(lamp.coords, "loc.poh_torch_5_lit"))
        }

    @Test
    fun GameTestState.`duplicate grinder collection callbacks cannot create extra bonemeal`() =
        runGameTest(EctoGrinderEvents::class) {
            prepare()
            val tile = CoordGrid(3658, 3524, 1)
            allocZoneCollision(tile)
            player.teleport(tile)
            val chute = placeMapLoc(tile.translateZ(1), "loc.ahoy_grinder_chute")
            val type = checkNotNull(ServerCacheManager.getObject(chute.id))
            val rows = PrayerEctofuntusBonemealRow.all()
            val index = rows.indexOfFirst { it.input.internalName == "obj.bones" }
            VarPlayerIntMapSetter.set(player, "varbit.ecto_grinder_recipe", index + 1)
            VarPlayerIntMapSetter.set(player, "varbit.ahoy_grinder_status", 0)
            player.inv[0] = InvObj("obj.pot_empty")
            player.inv[1] = InvObj("obj.pot_empty")
            player.withProtectedAccess {
                repeat(2) { eventBus.publish(this, LocEvents.Op1(chute, chute, type)) }
            }
            advance(ticks = 5)
            assertEquals(1, player.inv.count(rows[index].output))
            assertEquals(1, player.inv.count("obj.pot_empty"))
            assertEquals(0, player.vars["varbit.ecto_grinder_recipe"])
        }

    @Test
    fun GameTestState.`Ectofuntus token overflow keeps ingredients and grants no XP`() =
        runGameTest(EctoWorshipEvents::class) {
            prepare()
            player.inv[0] = InvObj("obj.bucket_ectoplasm")
            player.inv[1] = InvObj("obj.pot_bonemeal_bat")
            VarPlayerIntMapSetter.set(player, "varbit.ecto_tokens", 1020)
            val shrine = placeMapLoc(TILE.translateX(1), "loc.ahoy_ectofuntus")
            val before = xp()
            player.opLoc1(shrine)
            advance(ticks = 3)
            assertEquals(before, xp())
            assertEquals(1, player.inv.count("obj.bucket_ectoplasm"))
            assertEquals(1, player.inv.count("obj.pot_bonemeal_bat"))
            assertEquals(1020, player.vars["varbit.ecto_tokens"])
        }

    @Test
    fun GameTestState.`relighting incense replaces its old expiration timer`() =
        runInjectedGameTest(PrayerTrainingDeps::class, null, PrayerIncenseScript::class) { deps ->
            prepare()
            player.setBaseLevel("stat.firemaking", 30)
            player.setCurrentLevel("stat.firemaking", 30)
            player.inv[0] = InvObj("obj.marentill")
            player.inv[1] = InvObj("obj.tinderbox")
            player.inv[2] = InvObj("obj.marentill")
            val lamp = placeMapLoc(TILE.translateX(1), "loc.poh_torch_5")
            random.next = 0
            player.opLoc1(lamp)
            advance(ticks = 210)
            val lit = checkNotNull(deps.locs.findExact(lamp.coords, lamp.shape))
            val type = checkNotNull(ServerCacheManager.getObject(lit.id))
            player.opLoc1(BoundLocInfo(lit, type))
            advance(ticks = 35)
            assertTrue(deps.locs.findLoc(lamp.coords, "loc.poh_torch_5_lit"))
            assertEquals(0, player.inv.count("obj.marentill"))
            advance(ticks = 230)
            assertTrue(deps.locs.findLoc(lamp.coords, "loc.poh_torch_5"))
        }

    private fun GameTestScope.prepare() {
        allocZoneCollision(TILE)
        player.teleport(TILE)
        player.clearInv()
        player.setBaseLevel("stat.prayer", 80)
        player.setCurrentLevel("stat.prayer", 80)
        player.xpRate = 1.0
        player.globalXpRate = 1.0
    }

    private fun GameTestScope.xp(): Int = player.statMap.getFineXP("stat.prayer")

    private fun GameTestScope.bury(deps: PrayerTrainingDeps) {
        player.withProtectedAccess { deps.held.interact(this, inv, 0, HeldOp.Op1) }
    }

    private fun GameTestScope.useOn(deps: PrayerTrainingDeps, loc: BoundLocInfo, slot: Int) {
        val type = checkNotNull(ServerCacheManager.getObject(loc.id))
        val obj = checkNotNull(player.inv[slot])
        val held = checkNotNull(ServerCacheManager.getItem(obj.id))
        player.withProtectedAccess { deps.locU.interactOp(this, loc, loc, type, held, inv, slot) }
    }
}
