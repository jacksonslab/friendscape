package org.rsmod.content.skills.hunter.netting

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.hunter.HunterNetCreatureRow
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HunterNetScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val invisibleLvls: InvisibleLevels,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (creature in HunterNetCreatureRow.all()) {
            onOpNpc1(creature.npc) { catch(it.npc, creature) }
        }
        val butterflies = HunterNetCreatureRow.all().filter { it.jar.internalName == BUTTERFLY_JAR }
        for (product in butterflies.map { it.product }.distinctBy { it.id }) {
            onOpHeld4(product) { release(product.internalName) }
        }
    }

    private suspend fun ProtectedAccess.catch(npc: Npc, creature: HunterNetCreatureRow) {
        val net = heldNet()
        val barehanded = net == null && player.righthand == null && player.lefthand == null
        val levelReq = if (net == null) creature.level + BAREHANDED_LEVELS else creature.level
        if (net == null && !barehanded) {
            mes("You need to be wielding a butterfly net to catch this.")
            return
        }
        if (player.hunterLvl < levelReq) {
            mes("You need a Hunter level of $levelReq to catch this.")
            return
        }
        val isImpling = creature.jar.internalName == IMPLING_JAR
        if (isImpling && creature.jar.internalName !in inv) {
            mes("You need an empty impling jar to catch this.")
            return
        }

        anim(NET_SWING)
        delay(SWING_DELAY)
        if (!npc.isVisible || npc.coords.chebyshevDistance(player.coords) > REACH) {
            return
        }

        val bonus = if (net == MAGIC_NET || barehanded) ENHANCED_BONUS else 0
        val caught = statRandom(HUNTER, creature.low + bonus, creature.high + bonus, invisibleLvls)
        val kind = if (isImpling) "impling" else "butterfly"
        if (!caught) {
            mes("You fail to catch the $kind.")
            return
        }

        val jarred = creature.jar.internalName in inv &&
            invReplace(inv, creature.jar.internalName, 1, creature.product.internalName).success
        if (isImpling && !jarred) {
            return
        }
        npcRepo.despawn(npc, creature.respawn)
        statAdvance(HUNTER, creature.xp / 10.0 * xpMods.get(player, HUNTER))
        when {
            isImpling -> mes("You manage to catch the impling and squeeze it into a jar.")
            jarred -> mes("You manage to catch the butterfly.")
            else -> mes("You manage to catch the butterfly, but have no jar to keep it in.")
        }
    }

    private fun ProtectedAccess.heldNet(): String? {
        val righthand = player.righthand ?: return null
        return NETS.firstOrNull { it.asRSCM(RSCMType.OBJ) == righthand.id }
    }

    private fun ProtectedAccess.release(product: String) {
        if (invReplace(inv, product, 1, BUTTERFLY_JAR).success) {
            mes("You release the butterfly.")
        }
    }

    companion object {
        private const val HUNTER = "stat.hunter"
        private const val BUTTERFLY_JAR = "obj.butterfly_jar"
        private const val IMPLING_JAR = "obj.ii_impling_jar"
        private const val BUTTERFLY_NET = "obj.hunting_butterfly_net"
        private const val MAGIC_NET = "obj.ii_magic_butterfly_net"
        private const val NET_SWING = "seq.human_butterflynet_swing"

        private const val BAREHANDED_LEVELS = 10
        private const val ENHANCED_BONUS = 20
        private const val SWING_DELAY = 2
        private const val REACH = 1

        private val NETS = listOf(BUTTERFLY_NET, MAGIC_NET)
    }
}
