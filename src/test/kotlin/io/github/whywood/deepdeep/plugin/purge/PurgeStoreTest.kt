package io.github.whywood.deepdeep.plugin.purge

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PurgeStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun store(): PurgeStore = PurgeStore(temporary.root)

    @Test
    fun `settings round trip`() {
        val store = store()
        store.writeSettings(PurgeSettings(injection = "口径", boost = false, fallback = false))

        val loaded = PurgeStore(temporary.root).readSettings()

        assertEquals("口径", loaded.injection)
        assertEquals(false, loaded.boost)
        assertEquals(false, loaded.fallback)
    }

    @Test
    fun `missing and corrupt settings fall back to defaults`() {
        assertEquals(PurgeSettings(), store().readSettings())

        File(temporary.root, "settings.json").writeText("{oops", Charsets.UTF_8)

        assertEquals(PurgeSettings(), store().readSettings())
    }

    @Test
    fun `probe expires after thirty minutes`() {
        val store = store()
        val now = 1_000_000L
        store.writeProbe("abcd1234", now = now)

        assertEquals("abcd1234", store.readProbe(now = now + TimeUnit.MINUTES.toMillis(29))?.nonce)
        assertNull(store.readProbe(now = now + TimeUnit.MINUTES.toMillis(31)))
    }

    @Test
    fun `audit keeps newest entries first`() {
        val store = store()
        store.audit("prompt", "first", now = 1L)
        store.audit("probe", "second", now = 2L)

        val entries = store.readAudit()

        assertEquals(2, entries.size)
        assertEquals("probe", entries[0].action)
        assertEquals("prompt", entries[1].action)
    }

    @Test
    fun `audit rotates when the log grows`() {
        val store = store()
        File(temporary.root, "audit.jsonl").writeText("x".repeat(513 * 1024), Charsets.UTF_8)

        store.audit("prompt", "next")

        assertTrue(File(temporary.root, "audit.jsonl.1").isFile)
        assertTrue(File(temporary.root, "audit.jsonl").length() < 512L * 1024L)
    }
}
