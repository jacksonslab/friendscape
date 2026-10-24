package org.rsmod.content.skills.sailing

import dev.openrune.types.ItemServerType
import jakarta.inject.Singleton
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess

/** Purchases a boat with its base fittings; component materials are for subsequent upgrades. */
@Singleton
class Shipbuilding {
    fun materials(type: BoatType): Map<ItemServerType, Int> {
        if (type.purchaseCost <= 0) return emptyMap()
        val coins = dev.openrune.ServerCacheManager.getItem(dev.openrune.rscm.RSCM.getRSCM("obj.coins"))!!
        return mapOf(coins to type.purchaseCost)
    }

    suspend fun commission(access: ProtectedAccess) =
        with(access) {
            val buildable = BoatTypes.ALL.filter { it.typeId != RAFT_TYPE_ID && !player.ownsBoat(it) }
            if (buildable.isEmpty()) {
                mesbox("The shipwright has nothing more to build for you.")
                return@with
            }
            val labels = buildable.map { "${it.name} (Sailing ${it.levelRequired})" }
            val type = choose(labels, buildable, title = "Commission which boat?") ?: return@with
            if (statBase(STAT_SAILING) < type.levelRequired) {
                mesbox("You need a Sailing level of ${type.levelRequired} to commission a ${type.name.lowercase()}.")
                return@with
            }
            val cost = materials(type)
            val list = cost.entries.joinToString(", ") { "${it.value} x ${it.key.name}" }
            val missing = cost.filter { (obj, count) -> held(access, obj) < count }
            if (missing.isNotEmpty()) {
                mesbox("A ${type.name.lowercase()} needs: $list.")
                return@with
            }
            val confirmed = choice2("Build it for: $list", true, "Not now.", false, title = "Commission a ${type.name.lowercase()}?")
            if (!confirmed || player.ownsBoat(type)) {
                return@with
            }
            if (!build(access, type)) {
                mesbox("You no longer have all the materials.")
                return@with
            }
            player.sailingActiveBoat = type.typeId
            mesbox("The shipwright builds your new ${type.name.lowercase()}. Board it from any dock gangplank.")
        }

    /** Counts [obj] in the inventory, noted copies included, since a hull needs more than 28 slots. */
    fun held(access: ProtectedAccess, obj: ItemServerType): Int {
        val noted = if (obj.canCert) access.invTotal(access.inv, access.ocCert(obj.internalName).internalName) else 0
        return (access.invTotal(access.inv, obj.internalName).toLong() + noted).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun build(access: ProtectedAccess, type: BoatType): Boolean {
        if (type.purchaseCost <= 0 || access.player.ownsBoat(type) || access.statBase(STAT_SAILING) < type.levelRequired) {
            return false
        }
        val cost = materials(type)
        if (cost.any { (obj, count) -> held(access, obj) < count }) {
            return false
        }
        val result =
            access.player.invTransaction(access.inv) {
                val target = select(access.inv)
                for ((obj, count) in cost) {
                    val plain = minOf(count, access.invTotal(access.inv, obj.internalName))
                    if (plain > 0) {
                        delete(target, obj.id, plain)
                    }
                    if (count > plain) {
                        delete(target, access.ocCert(obj.internalName).id, count - plain)
                    }
                }
            }
        if (!result.success) {
            return false
        }
        access.player.grantBoat(type)
        return true
    }
}
