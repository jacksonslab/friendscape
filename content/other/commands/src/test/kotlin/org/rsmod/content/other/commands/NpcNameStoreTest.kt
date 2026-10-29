package org.rsmod.content.other.commands

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcNameStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `saved names survive a new store instance and resetting removes an override`() {
        val file = directory.resolve("nested/npc-names.properties")
        val store = NpcNameStore(file)
        assertEquals(emptyMap<Int, String>(), store.load())
        store.save(mapOf(3310 to "Jackson's Account Guide", 9484 to "Friendscape Banker"))
        assertEquals(mapOf(3310 to "Jackson's Account Guide", 9484 to "Friendscape Banker"), NpcNameStore(file).load())
        store.save(store.load() - 9484)
        assertEquals(mapOf(3310 to "Jackson's Account Guide"), NpcNameStore(file).load())
    }

    @Test
    fun `invalid input cannot replace the last valid settings`() {
        val file = directory.resolve("npc-names.properties")
        val store = NpcNameStore(file)
        store.save(mapOf(9484 to "Banker"))
        for (name in listOf("", " ", "x".repeat(41), "Banker\nForged log", "<col=ff0000>Banker", "Banker|Back", "Banker\u0000")) {
            assertThrows(IllegalArgumentException::class.java) { store.save(mapOf(9484 to name)) }
            assertEquals(mapOf(9484 to "Banker"), store.load())
        }
    }

    @Test
    fun `malformed saved IDs are rejected instead of silently discarded`() {
        val file = directory.resolve("npc-names.properties")
        Files.writeString(file, "not-an-id=Banker\n")
        assertThrows(IllegalArgumentException::class.java) { NpcNameStore(file).load() }
    }
}
