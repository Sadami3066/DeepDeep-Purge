package io.github.whywood.deepdeep.plugin.purge

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgeRunbooksTest {
    private val runbooks = PurgeRunbooks(File("src/main/assets/purge"))

    @Test
    fun `catalog exposes every bundled runbook sorted by role`() {
        val catalog = runbooks.catalog()

        assertEquals(23, catalog.size)
        assertTrue(catalog.any { it.id == "passive-recon" })
        assertTrue(catalog.all { it.name.isNotBlank() && it.description.isNotBlank() })
        assertEquals("recon", catalog.first().role)
    }

    @Test
    fun `content returns the runbook markdown`() {
        val content = runbooks.content("credential-attack")

        assertTrue(content != null && content.contains("hydra"))
    }

    @Test
    fun `unknown and unsafe ids return null`() {
        assertNull(runbooks.content("no-such-skill"))
        assertNull(runbooks.content("../etc/passwd"))
        assertNull(runbooks.content(""))
    }

    @Test
    fun `front matter parsing keeps quoted and plain values`() {
        val meta = PurgeRunbooks.parseFrontMatter(
            "---\nname: demo\ndescription: \"quoted value\"\nrole: recon\n---\nbody",
        )

        assertEquals("demo", meta["name"])
        assertEquals("quoted value", meta["description"])
        assertEquals("recon", meta["role"])
    }

    @Test
    fun `role order groups phases`() {
        assertTrue(PurgeRunbooks.roleOrder("recon") < PurgeRunbooks.roleOrder("vuln-scan"))
        assertTrue(PurgeRunbooks.roleOrder("vuln-scan") < PurgeRunbooks.roleOrder("exploit"))
        assertTrue(PurgeRunbooks.roleOrder("internal") < PurgeRunbooks.roleOrder("plan"))
    }
}
