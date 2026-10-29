package org.rsmod.content.other.commands

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

internal class NpcNameStore(private val file: Path) {
    fun load(): Map<Int, String> {
        if (!Files.exists(file)) return emptyMap()
        val props = Properties().apply { Files.newBufferedReader(file, UTF_8).use { load(it) } }
        return props.stringPropertyNames().associate { key ->
            val id = requireNotNull(key.toIntOrNull()) { "Invalid NPC id in name settings: $key" }
            require(id in 0..65535) { "Invalid NPC id: $id" }
            id to validateName(props.getProperty(key))
        }
    }

    fun save(names: Map<Int, String>) {
        val props = Properties()
        for ((id, name) in names) {
            require(id in 0..65535)
            props.setProperty(id.toString(), validateName(name))
        }
        val target = file.toAbsolutePath()
        Files.createDirectories(target.parent)
        val pending = Files.createTempFile(target.parent, "npc-names-", ".tmp")
        try {
            Files.newBufferedWriter(pending, UTF_8).use { props.store(it, "Friendscape global NPC names") }
            try {
                Files.move(pending, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }

    companion object {
        fun validateName(value: String): String {
            val name = value.trim()
            require(name.length in 1..40 && name.all { it in ' '..'~' && it !in "<>|" }) {
                "Use 1-40 printable English characters, without <, > or |."
            }
            return name
        }
    }
}
