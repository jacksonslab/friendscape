package org.rsmod.api.game.process.player

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.cheat.adminInfiniteStamina
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.autoEnableRunOnMove
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RunAutoEnableTest {
    private val processor = PlayerRunUpdateProcessor(realm(infiniteRunEnergy = false))

    @Test
    fun `realm infinite run energy keeps every player's energy full`() {
        val player = player(threshold = 0)
        player.runEnergy = 1
        player.pendingStepCount = 2
        player.setActiveMoveSpeed(MoveSpeed.Run)
        val infinite = PlayerRunUpdateProcessor(realm(infiniteRunEnergy = true))
        repeat(3) { infinite.process(player) }
        assertEquals(10_000, player.runEnergy)
        assertEquals(MoveSpeed.Run, player.varMoveSpeed)
    }

    @Test
    fun `admin infinite stamina refills energy and prevents running drain`() {
        val player = player(threshold = 0)
        player.modLevel = Rights.ADMINISTRATOR
        player.adminInfiniteStamina = true
        player.runEnergy = 1
        player.pendingStepCount = 2
        player.setActiveMoveSpeed(MoveSpeed.Run)
        repeat(3) { processor.process(player) }
        assertEquals(10_000, player.runEnergy)
        assertEquals(MoveSpeed.Run, player.varMoveSpeed)
    }

    @Test
    fun `disabling infinite stamina restores normal energy drain`() {
        val player = player(threshold = 0)
        player.modLevel = Rights.ADMINISTRATOR
        player.adminInfiniteStamina = true
        player.adminInfiniteStamina = false
        player.runEnergy = 1
        player.pendingStepCount = 2
        player.setActiveMoveSpeed(MoveSpeed.Run)
        processor.process(player)
        assertEquals(0, player.runEnergy)
        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)
    }

    @Test
    fun `infinite stamina flag cannot bypass administrator rights`() {
        val player = player(threshold = 0)
        player.adminInfiniteStamina = true
        player.runEnergy = 1
        player.pendingStepCount = 2
        player.setActiveMoveSpeed(MoveSpeed.Run)
        processor.process(player)
        assertEquals(0, player.runEnergy)
        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)
    }

    @Test
    fun `energy climbing past the threshold while standing leaves run off`() {
        val player = depleted(threshold = 50)
        player.runEnergy = 4_990
        processor.process(player)

        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)
        assertFalse(ToggleQueue in player.queueList)
    }

    @Test
    fun `a move request at or above the threshold turns run back on after running out`() {
        val player = depleted(threshold = 50)
        player.runEnergy = 5_000
        player.autoEnableRunOnMove()

        assertEquals(MoveSpeed.Run, player.varMoveSpeed)
        assertEquals(MoveSpeed.Run, player.moveSpeed)
    }

    @Test
    fun `a move request below the threshold keeps walking until energy reaches it`() {
        val player = depleted(threshold = 50)
        player.runEnergy = 4_999
        player.autoEnableRunOnMove()
        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)

        player.runEnergy = 5_000
        player.autoEnableRunOnMove()
        assertEquals(MoveSpeed.Run, player.varMoveSpeed)
    }

    @Test
    fun `run is re-enabled once per depletion`() {
        val player = depleted(threshold = 50)
        player.runEnergy = 6_000
        player.autoEnableRunOnMove()
        player.setActiveMoveSpeed(MoveSpeed.Walk)

        player.autoEnableRunOnMove()

        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)
    }

    @Test
    fun `walking by choice is never overridden`() {
        val player = player(threshold = 50)
        player.runEnergy = 9_000

        player.autoEnableRunOnMove()

        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)
    }

    @Test
    fun `running out during a one-off run while walk is selected does not re-enable run`() {
        val player = player(threshold = 50)
        player.moveSpeed = MoveSpeed.Run
        player.runEnergy = 1
        player.pendingStepCount = 2
        processor.process(player)
        player.pendingStepCount = 0
        player.runEnergy = 6_000

        player.autoEnableRunOnMove()

        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)
    }

    @Test
    fun `a zero threshold leaves the setting off`() {
        val player = depleted(threshold = 0)
        player.runEnergy = 9_000

        player.autoEnableRunOnMove()

        assertEquals(MoveSpeed.Walk, player.varMoveSpeed)
    }

    private fun depleted(threshold: Int): Player =
        player(threshold).apply {
            setActiveMoveSpeed(MoveSpeed.Run)
            runEnergy = 1
            pendingStepCount = 2
            processor.process(this)
            pendingStepCount = 0
            check(runEnergy == 0 && varMoveSpeed == MoveSpeed.Walk)
        }

    private fun player(threshold: Int): Player =
        Player().apply {
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            VarPlayerIntMapSetter.set(this, "varbit.runenergy_autoenable", threshold)
        }

    private fun realm(infiniteRunEnergy: Boolean): Realm =
        Realm("test").apply {
            updateConfig(
                RealmConfig(
                    id = 0,
                    loginMessage = null,
                    loginBroadcast = null,
                    baseXpRate = 1.0,
                    globalXpRate = 1.0,
                    spawnCoord = CoordGrid(0, 50, 50, 0, 0),
                    respawnCoord = CoordGrid(0, 50, 50, 0, 0),
                    devMode = false,
                    requireRegistration = false,
                    autoAssignDisplayNames = true,
                    infiniteRunEnergy = infiniteRunEnergy,
                )
            )
        }

    companion object {
        private const val ToggleQueue = "queue.runmode_toggle"

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
