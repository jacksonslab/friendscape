package org.rsmod.api.account.saver

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player

public class CharacterSaveWriter @Inject constructor(
    private val repository: CharacterAccountRepository,
    private val pipelines: Set<@JvmSuppressWildcards CharacterDataStage.Pipeline>,
    private val config: ServerConfig,
) {
    public fun save(connection: DatabaseConnection, player: Player) {
        repository.save(connection, player, player.accountId, player.characterId, config.world)
        for (pipeline in pipelines) pipeline.save(connection, player, player.characterId)
    }
}
