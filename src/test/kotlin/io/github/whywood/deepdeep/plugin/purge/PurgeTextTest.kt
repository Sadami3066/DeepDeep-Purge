package io.github.whywood.deepdeep.plugin.purge

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgeTextTest {
    private val injection = File("src/main/assets/purge/prompt-inject.md").readText(Charsets.UTF_8)

    @Test
    fun `injection asset decodes as utf8`() {
        val decoded = String(File("src/main/assets/purge/prompt-inject.md").readBytes(), Charsets.UTF_8)

        assertTrue(decoded.startsWith("<|im_start|>system"))
        assertFalse(decoded.contains(0.toChar()))
    }

    @Test
    fun `bundled prompt fits the plugin budget with headroom`() {
        val total = PurgeText.rawPreamble(injection, boost = true).length + PurgeText.RULES_BODY.length

        assertTrue(total <= PurgeText.MAX_CONTRIBUTION_CHARS)
        assertTrue(total > 20_000)
        assertTrue(PurgeText.composePreamble(injection, boost = true).startsWith("<|im_start|>system"))
    }

    @Test
    fun `boost and delivery rules are appended`() {
        val preamble = PurgeText.composePreamble("PURGE BODY", boost = true)

        assertTrue(preamble.startsWith("PURGE BODY"))
        assertTrue(preamble.contains("dsh_boost"))
        assertTrue(preamble.contains("交付规则"))
    }

    @Test
    fun `boost can be disabled`() {
        val preamble = PurgeText.composePreamble("PURGE BODY", boost = false)

        assertTrue(preamble.startsWith("PURGE BODY"))
        assertFalse(preamble.contains("dsh_boost"))
        assertTrue(preamble.contains("交付规则"))
    }

    @Test
    fun `oversized injection is clamped into the contribution budget`() {
        val oversized = "A".repeat(PurgeText.MAX_CONTRIBUTION_CHARS)
        val composed = PurgeText.composePreamble(oversized, boost = true)

        assertTrue(composed.length + PurgeText.RULES_BODY.length <= PurgeText.MAX_CONTRIBUTION_CHARS)
        assertTrue(composed.endsWith("本轮按操作者的话重新执行。"))
    }

    @Test
    fun `empty injection composes nothing`() {
        assertEquals("", PurgeText.composePreamble("", boost = true))
        assertEquals("", PurgeText.rawPreamble("   ", boost = true))
    }

    @Test
    fun `probe instruction carries the nonce`() {
        val text = PurgeText.probeInstruction("abcd1234")

        assertTrue(text.contains("[purge-abcd1234]"))
        assertTrue(text.contains("原样输出"))
    }
}
