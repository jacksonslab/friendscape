package org.rsmod.api.net.rsprot

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.common.RSProtConstants
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.net.central.logging.CentralActivityLogWriter
import org.rsmod.api.net.rsprot.player.SessionStart
import org.rsmod.api.player.events.interact.HeldDropEvents
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.script.onEvent
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.utils.zone.SharedZoneEnclosedBuffers
import org.rsmod.game.GameUpdate
import org.rsmod.game.MapClock
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.worldentity.WorldEntityStateEvents
import org.rsmod.game.world.WorldType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class NetworkScript
@Inject
constructor(
    private val mapClock: MapClock,
    private val service: NetworkService<Player>,
    private val regionReg: RegionRegistry,
    private val config: ServerConfig,
    private val openRuneCentral: OpenRuneCentralWorldLink,
    private val gameUpdate: GameUpdate,
    private val database: GameDatabase,
    private val characterRepository: CharacterAccountRepository,
    private val playerRegistry: PlayerRegistry,
    private val worldEntityList: WorldEntityList,
    private val zoneUpdates: ZoneUpdateMap,
    private val locReg: LocRegistry,
    private val objReg: ObjRegistry,
    private val enclosedBuffers: SharedZoneEnclosedBuffers,
    private val centralActivityLogWriter: CentralActivityLogWriter,
) : PluginScript() {
    private val logger = InlineLogger()

    private companion object {
        private const val DB_ONLINE_SESSION_TIMING_INFO_MS = 100L
        private const val CENTRAL_HEARTBEAT_INTERVAL_CYCLES = 50L
    }

    override fun ScriptContext.startup() {
        check(RSProtConstants.REVISION == config.revision) {
            "RSProt and ${config.name} have mismatching revision builds! " +
                "(${config.name}=${config.revision}, rsprot=${RSProtConstants.REVISION})"
        }
        onEvent<GameLifecycle.Startup> {
            centralActivityLogWriter.start()
            initService()
            if (openRuneCentral.isEnabled) {
                openRuneCentral.startInboundWatch()
            }
        }
        onEvent<GameLifecycle.Shutdown> {
            openRuneCentral.stopInboundWatch()
            centralActivityLogWriter.stop()
        }
        onEvent<HeldDropEvents.Drop> {
            centralActivityLogWriter.logItemDrop(player, type, obj)
        }
        onEvent<HeldDropEvents.Destroy> {
            centralActivityLogWriter.logItemDestroy(player, type, obj)
        }
        onEvent<GameLifecycle.UpdateInfo> {
            processReconnects()
            updateService()
            maybeHeartbeatCentralSessions()
        }
        onEvent<SessionStart> { startSession() }
        onEvent<SessionStateEvent.Delete> {
            closeSession()
            finalizeCentralSessionIfNeeded()
        }
        onEvent<SessionStateEvent.Login> { markDbOnlineSession() }
        onEvent<SessionStateEvent.Logout> { notifyCentralLogout() }
        onEvent<NpcStateEvents.Create> { createNpcAvatar(npc) }
        onEvent<NpcStateEvents.Delete> { deleteNpcAvatar(npc) }
        onEvent<WorldEntityStateEvents.Create> { createWorldEntityAvatar(entity) }
        onEvent<WorldEntityStateEvents.Delete> { deleteWorldEntityAvatar(entity) }
    }

    private fun initService() {
        service.setCommunicationThread(Thread.currentThread())
    }

    private fun processReconnects() {
        while (true) {
            val request = ReconnectQueue.poll() ?: return
            try {
                reconnect(request)
            } catch (e: Exception) {
                logger.error(e) { "Reconnect failed for '${request.block.username}'" }
                request.responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
            }
        }
    }

    /**
     * Rebinds a player kept online through the disconnect grace period to the new connection.
     * The client proves it owns the session by sending back the seed of its previous login.
     */
    private fun reconnect(request: ReconnectQueue.Request) {
        val block = request.block
        val player = findReconnectable(block)
        val client = player?.client as? RspClient
        val cycle = player?.clientCycle as? RspCycle
        if (client == null || cycle == null) {
            request.responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
            return
        }
        val infos = client.infos
        infos.playerInfo.onReconnect()
        infos.npcInfo.onReconnect()
        infos.worldEntityInfo.onReconnect()
        val response = LoginResponse.ReconnectOk(infos.playerInfo)
        val session = request.responseHandler.writeSuccessfulResponse(response, block, client.session)
        client.session = session
        cycle.session = session
        cycle.forceSceneRebuild()
        player.clientDisconnected.set(false)
        session.setDisconnectionHook(Runnable { player.clientDisconnected.set(true) })
        logger.info { "Reconnected '${player.username}'" }
    }

    private fun findReconnectable(block: LoginBlock<XteaKey>): Player? {
        val seed = block.authentication.key
        for (player in playerRegistry.playerList) {
            if (!player.clientDisconnected.get() || player.loggingOut || player.pendingLogout) {
                continue
            }
            val previous = (player.client as? RspClient)?.session?.loginBlock ?: continue
            if (previous.username.equals(block.username, ignoreCase = true) && previous.seed.contentEquals(seed)) {
                return player
            }
        }
        return null
    }

    private fun updateService() {
        // `infoProtocols.update()` runs worldentity -> player -> npc in order; the npc info
        // protocol's first step (`synchronizeModifiedWorlds`) consumes the added/removed
        // world entity indices itself - no server-side world sync is needed.
        service.infoProtocols.update()
        if (openRuneCentral.isEnabled) {
            openRuneCentral.drainInboundRevokesOnGameThread(playerRegistry, gameUpdate)
            openRuneCentral.drainInboundSocialOnGameThread(playerRegistry)
        }
    }

    private fun maybeHeartbeatCentralSessions() {
        if (!openRuneCentral.isEnabled) {
            return
        }
        if (mapClock.cycle % CENTRAL_HEARTBEAT_INTERVAL_CYCLES != 0L) {
            return
        }
        val tokens = ArrayList<ByteArray>()
        playerRegistry.forEachOnline { player ->
            player.openRuneCentralSessionToken?.let { tokens.add(it) }
        }
        openRuneCentral.heartbeatOnlineSessions(tokens)
        touchOnlineSessionHeartbeats()
    }

    /** One transaction per mode, since saves live in a schema per world type. */
    private fun touchOnlineSessionHeartbeats() {
        val byWorldType = mutableMapOf<WorldType, MutableList<Int>>()
        playerRegistry.forEachOnline { player ->
            if (player.characterId > 0) {
                byWorldType.getOrPut(player.worldType) { mutableListOf() } += player.characterId
            }
        }
        for ((worldType, characterIds) in byWorldType) {
            try {
                database.withTransactionBlocking(worldType.key) { connection ->
                    for (characterId in characterIds) {
                        characterRepository.setOnlineSession(connection, characterId, config.world)
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) {
                    "Could not refresh online-session heartbeats for world type='${worldType.key}'"
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun SessionStart.startSession() {
        val slot = player.slotId

        val infos = service.infoProtocols.alloc(slot, OldSchoolClientType.DESKTOP)

        val client = RspClient(session, infos) as Client<Any, Any>
        val cycle =
            RspCycle(
                session,
                infos,
                regionReg,
                worldEntityList,
                zoneUpdates,
                locReg,
                objReg,
                enclosedBuffers,
            )

        player.client = client
        player.clientCycle = cycle

        cycle.init(player)
    }

    private fun SessionStateEvent.Delete.closeSession() {
        val client = player.client as? RspClient ?: return
        client.unregister(service, player)
    }

    private fun SessionStateEvent.Delete.finalizeCentralSessionIfNeeded() {
        finalizeCentralSession(player)
    }

    private fun SessionStateEvent.Login.markDbOnlineSession() {
        val startedAt = System.nanoTime()
        try {
            database.withTransactionBlocking(player.worldType.key) { connection ->
                characterRepository.setOnlineSession(
                    connection,
                    player.characterId,
                    config.world,
                )
            }
        } catch (e: Exception) {
            logger.warn(e) { "Could not set DB online-session for characterId=${player.characterId}" }
        }
        val elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        if (config.loginTimingLogs && elapsedMs >= DB_ONLINE_SESSION_TIMING_INFO_MS) {
            logger.info {
                "Login set-online-session user='${player.username}' characterId=${player.characterId} elapsed=${elapsedMs}ms"
            }
        }
        centralActivityLogWriter.logPlayerLogin(player)
    }

    private fun SessionStateEvent.Logout.notifyCentralLogout() {
        centralActivityLogWriter.logPlayerLogout(player)
        finalizeCentralSession(player)
    }

    private fun finalizeCentralSession(player: Player) {
        try {
            database.withTransactionBlocking(player.worldType.key) { connection ->
                characterRepository.clearOnlineSession(connection, player.characterId)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Could not clear DB online-session for characterId=${player.characterId}" }
        }
        val token = player.openRuneCentralSessionToken ?: return
        openRuneCentral.notifyLogout(token)
        token.fill(0)
        player.openRuneCentralSessionToken = null
    }

    private fun createNpcAvatar(npc: Npc) {
        val rspAvatar =
            service.npcAvatarFactory.alloc(
                index = npc.slotId,
                id = npc.id,
                level = npc.level,
                x = npc.x,
                z = npc.z,
                spawnCycle = mapClock.cycle,
                direction = npc.respawnDir.id,
            )
        npc.infoProtocol = RspNpcInfo(rspAvatar)
        org.rsmod.game.entity.npc.GlobalNpcNames.get(npc.id)?.let(npc.infoProtocol::setName)
    }

    private fun deleteNpcAvatar(npc: Npc) {
        val infoProtocol = npc.avatar.infoProtocol
        if (infoProtocol is RspNpcInfo) {
            service.npcAvatarFactory.release(infoProtocol.rspAvatar)
        }
    }

    private fun createWorldEntityAvatar(entity: WorldEntity) {
        val rspAvatar =
            service.worldEntityAvatarFactory.alloc(
                index = entity.slotId,
                id = entity.id,
                ownerIndex = entity.ownerIndex,
                sizeX = entity.sizeX,
                sizeZ = entity.sizeZ,
                southWestZoneX = entity.southWestZoneX,
                southWestZoneZ = entity.southWestZoneZ,
                minLevel = entity.minLevel,
                maxLevel = entity.maxLevel,
                fineX = entity.fineX,
                fineZ = entity.fineZ,
                projectedLevel = entity.projectedLevel,
                activeLevel = entity.activeLevel,
                angle = entity.angle,
            )
        entity.infoProtocol = RspWorldEntityInfo(rspAvatar)
    }

    private fun deleteWorldEntityAvatar(entity: WorldEntity) {
        val infoProtocol = entity.infoProtocol
        if (infoProtocol is RspWorldEntityInfo) {
            service.worldEntityAvatarFactory.release(infoProtocol.rspAvatar)
        }
    }
}
